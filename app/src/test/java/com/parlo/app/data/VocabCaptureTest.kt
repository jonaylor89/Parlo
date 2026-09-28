package com.parlo.app.data

import com.parlo.app.data.db.SessionEntity
import com.parlo.app.data.db.TurnEntity
import com.parlo.app.data.db.VocabSource
import com.parlo.app.data.db.VocabStatus
import com.parlo.app.gemini.VocabMiner
import com.parlo.app.judge.JevClient
import com.parlo.app.judge.VocabJudge
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Whole pipeline: transcript -> fake Gemini miner -> fake Jev judge -> vocab table.
 * One MockWebServer plays both APIs, routing on path.
 */
class VocabCaptureTest {
    private val server = MockWebServer()
    private val vocabDao = FakeVocabDao()
    private val sessionDao = FakeSessionDao()
    private var jevKey = "jev-key"
    private lateinit var capture: VocabCapture
    private var sessionId = 0L

    /** word -> Jev answers JSON for that candidate. */
    private val verdicts = mutableMapOf<String, String>()
    /** Bodies of every Jev request, in arrival order. */
    private val jevBodies = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val minerReply = """[
        {"word":"madrugar","translation":"to get up early","example":"Tengo que madrugar.","reason":"asked_meaning"},
        {"word":"hola","translation":"hello","example":"Hola, buenos días.","reason":"introduced"},
        {"word":"Barcelona","translation":"Barcelona","example":"Vivo en Barcelona.","reason":"introduced"},
        {"word":"la cuenta","translation":"the bill","example":"La cuenta, por favor.","reason":"struggled"}
    ]"""

    @Before
    fun setUp() = runBlocking<Unit> {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                return when {
                    request.path!!.contains(":generateContent") ->
                        MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":${Json.encodeToString(kotlinx.serialization.serializer<String>(), minerReply)}}]}}]}""")
                    request.path == "/v1/systemone" -> {
                        jevBodies += body
                        val word = Json.parseToJsonElement(body).jsonObject["state"]!!.jsonObject["candidate"]!!.jsonObject["word"]!!.jsonPrimitive.content
                        verdicts[word]?.let { MockResponse().setBody(it) } ?: MockResponse().setResponseCode(500).setBody("boom")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        capture = VocabCapture(
            sessions = SessionRepository(sessionDao),
            vocab = VocabRepository(vocabDao),
            geminiKey = { "gemini-key" },
            jevKey = { jevKey },
            miner = VocabMiner(OkHttpClient(), baseUrl = server.url("/v1beta/models").toString().trimEnd('/'), models = listOf("gemini-2.5-flash")),
            judge = VocabJudge(JevClient(OkHttpClient(), endpoint = server.url("/v1/systemone").toString())),
            scope = CoroutineScope(Dispatchers.Default),
        )
        sessionId = sessionDao.insertSession(SessionEntity(startedAt = 1, endedAt = 100, language = "Spanish", dialect = "Madrid Spanish", level = "BEGINNER", scenario = "FREE", correctionStyle = "GENTLE"))
        listOf(
            "TUTOR" to "¿A qué hora te levantas?",
            "USER" to "A las seis. Tengo que madrugar... what does madrugar mean?",
            "TUTOR" to "Madrugar means to get up early. Vivo en Barcelona, ¿y tú?",
            "USER" to "Um... la... cuenta? Hola.",
        ).forEachIndexed { i, (s, t) -> sessionDao.insertTurn(TurnEntity(sessionId = sessionId, speaker = s, text = t, timestamp = i.toLong())) }
    }

    @After fun tearDown() = server.shutdown()

    private fun jev(real: Double, top: Double, mid: Double, reason: String, conf: Double) = """
        {"answers":{
          "is_real_word":{"type":"noul","noul":$real},
          "usefulness":{"type":"score","score":${top * 2 + mid},"confidence":$top,"probabilities":{"0":${1 - top - mid},"1":$mid,"2":$top}},
          "reason":{"type":"choice","choice":"$reason","confidence":$conf,"probabilities":{"$reason":$conf}}
        }}
    """

    @Test
    fun `jev sorts mined candidates into kept, suggested and dropped`() = runBlocking<Unit> {
        verdicts["madrugar"] = jev(0.99, 0.96, 0.03, "asked_meaning", 0.92)
        verdicts["hola"] = jev(0.99, 0.02, 0.08, "known", 0.95)
        verdicts["Barcelona"] = jev(0.05, 0.9, 0.05, "introduced", 0.8)
        verdicts["la cuenta"] = jev(0.98, 0.55, 0.3, "struggled", 0.7)

        val outcome = capture.mine(sessionId)

        assertEquals(VocabCapture.Outcome.Found(kept = 1, suggested = 1), outcome)
        val rows = vocabDao.rows.value.associateBy { it.word }
        assertEquals(setOf("madrugar", "la cuenta"), rows.keys)
        rows.getValue("madrugar").let {
            assertEquals(VocabStatus.KEPT.name, it.status)
            assertEquals(VocabSource.MINED.name, it.source)
            assertEquals("You asked what it means", it.reason)
            assertEquals(0.96, it.confidence!!, 1e-9)
        }
        rows.getValue("la cuenta").let {
            assertEquals(VocabStatus.SUGGESTED.name, it.status)
            assertEquals("You got stuck on it", it.reason)
            assertEquals(0.55, it.confidence!!, 1e-9)
        }
        assertTrue(sessionDao.getSession(sessionId)!!.minedAt != null)
        assertEquals(1 + 4, server.requestCount) // one Gemini call + one Jev call per candidate
    }

    @Test
    fun `without a jev key every candidate is suggested and jev is never called`() = runBlocking<Unit> {
        jevKey = ""
        val outcome = capture.mine(sessionId)
        assertEquals(VocabCapture.Outcome.Found(kept = 0, suggested = 4), outcome)
        assertTrue(vocabDao.rows.value.all { it.status == VocabStatus.SUGGESTED.name && it.confidence == null })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a failing jev call degrades that candidate to a plain suggestion`() = runBlocking<Unit> {
        verdicts["madrugar"] = jev(0.99, 0.96, 0.03, "asked_meaning", 0.92)
        // the other three words have no canned verdict -> HTTP 500 from the fake
        val outcome = capture.mine(sessionId)
        assertEquals(VocabCapture.Outcome.Found(kept = 1, suggested = 3), outcome)
        val hola = vocabDao.rows.value.first { it.word == "hola" }
        assertEquals(VocabStatus.SUGGESTED.name, hola.status)
        assertNull(hola.confidence)
        assertEquals("New word from the tutor", hola.reason)
    }

    @Test
    fun `review of an in-session note promotes, relabels or removes it`() = runBlocking<Unit> {
        val repo = VocabRepository(vocabDao)
        val keepId = repo.suggest("madrugar", "to get up early", "", "Spanish", sessionId, VocabSource.TUTOR, VocabCapture.reasonLabel("introduced"))!!
        val dropId = repo.suggest("Barcelona", "", "", "Spanish", sessionId, VocabSource.TUTOR, VocabCapture.reasonLabel("introduced"))!!
        val stayId = repo.suggest("la cuenta", "the bill", "", "Spanish", sessionId, VocabSource.TUTOR, VocabCapture.reasonLabel("struggled"))!!
        verdicts["madrugar"] = jev(0.99, 0.96, 0.03, "asked_meaning", 0.92)
        verdicts["Barcelona"] = jev(0.05, 0.9, 0.05, "introduced", 0.8)
        verdicts["la cuenta"] = jev(0.98, 0.55, 0.3, "struggled", 0.2)

        assertEquals(VocabJudge.Decision.KEEP, capture.review(keepId))
        assertEquals(VocabJudge.Decision.DROP, capture.review(dropId))
        assertEquals(VocabJudge.Decision.SUGGEST, capture.review(stayId))

        val kept = vocabDao.get(keepId)!!
        assertEquals(VocabStatus.KEPT.name, kept.status)
        assertEquals(VocabSource.TUTOR.name, kept.source)
        assertEquals("You asked what it means", kept.reason)
        assertNull(vocabDao.get(dropId))
        val stay = vocabDao.get(stayId)!!
        assertEquals(VocabStatus.SUGGESTED.name, stay.status)
        assertEquals("You got stuck on it", stay.reason) // low-confidence reason keeps the tutor's label
        assertEquals(0.55, stay.confidence!!, 1e-9)

        // Second look is a no-op: already judged.
        assertNull(capture.review(keepId))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `review skips manual entries and does nothing without a jev key`() = runBlocking<Unit> {
        val repo = VocabRepository(vocabDao)
        val manual = repo.save("gracias", "thanks", "", "Spanish", sessionId)
        assertNull(capture.review(manual))
        jevKey = ""
        val id = repo.suggest("madrugar", "", "", "Spanish", sessionId, VocabSource.TUTOR, "")!!
        assertNull(capture.review(id))
        assertEquals(VocabStatus.SUGGESTED.name, vocabDao.get(id)!!.status)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `review state includes the recent transcript for context`() = runBlocking<Unit> {
        val id = VocabRepository(vocabDao).suggest("madrugar", "to get up early", "", "Spanish", sessionId, VocabSource.TUTOR, "")!!
        verdicts["madrugar"] = jev(0.99, 0.96, 0.03, "asked_meaning", 0.92)
        capture.review(id)
        val state = Json.parseToJsonElement(jevBodies.single()).jsonObject["state"]!!.jsonObject
        assertEquals("Beginner", state["learner_level"]!!.jsonPrimitive.content)
        assertTrue(state["conversation_excerpt"].toString().contains("what does madrugar mean"))
    }

}
