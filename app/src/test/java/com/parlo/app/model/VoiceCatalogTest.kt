package com.parlo.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCatalogTest {

    @Test
    fun `all 30 Gemini voices present, unique, and default is catalogued`() {
        assertEquals(30, VoiceCatalog.all.size)
        assertEquals(30, VoiceCatalog.all.map { it.name }.toSet().size)
        assertTrue(VoiceCatalog.all.all { it.character.isNotBlank() })
        assertEquals("Puck", VoiceCatalog.find(SessionConfig().voice)?.name)
        assertEquals(VoiceCatalog.all.map { it.name }, Defaults.voices)
    }

    @Test
    fun `lookup is case-insensitive and gender lists are sorted and disjoint`() {
        assertEquals("Zephyr", VoiceCatalog.find(" zephyr ")?.name)
        assertNull(VoiceCatalog.find("HAL"))
        val f = VoiceCatalog.byGender(VoiceGender.FEMALE)
        val m = VoiceCatalog.byGender(VoiceGender.MALE)
        assertEquals(f.sortedBy { it.name }, f)
        assertEquals(m.sortedBy { it.name }, m)
        assertEquals(30, f.size + m.size)
        assertTrue(f.none { it in m })
    }
}
