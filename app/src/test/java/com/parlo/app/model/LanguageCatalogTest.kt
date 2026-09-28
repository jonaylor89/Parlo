package com.parlo.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageCatalogTest {

    @Test
    fun `catalog is broad, every language has at least one accent, and names are unique`() {
        assertTrue("expected 100+ languages, got ${LanguageCatalog.languages.size}", LanguageCatalog.languages.size >= 100)
        val totalDialects = LanguageCatalog.languages.sumOf { it.dialects.size }
        assertTrue("expected 700+ accents, got $totalDialects", totalDialects >= 700)
        LanguageCatalog.languages.forEach { l ->
            assertTrue("${l.name} has no accents", l.dialects.isNotEmpty())
            assertEquals("${l.name} has duplicate accents", l.dialects.size, l.dialects.map { it.name }.toSet().size)
            l.dialects.forEach { d -> assertTrue("${d.name} blank region", d.region.isNotBlank()) }
        }
        val names = LanguageCatalog.languages.map { it.name }
        assertEquals("duplicate language names", names.size, names.toSet().size)
        Region.entries.forEach { r -> assertTrue("no languages in $r", LanguageCatalog.byRegion()[r]!!.isNotEmpty()) }
    }

    @Test
    fun `default config points at a real catalog entry`() {
        val cfg = SessionConfig()
        val lang = LanguageCatalog.find(cfg.language)!!
        assertEquals(cfg.dialect, lang.defaultDialect.name)
        assertEquals(cfg.dialect, LanguageCatalog.defaultDialectFor("spanish"))
    }

    @Test
    fun `find is case-insensitive and understands native and slash names`() {
        assertEquals("Japanese", LanguageCatalog.find("japanese")!!.name)
        assertEquals("Japanese", LanguageCatalog.find("日本語")!!.name)
        assertEquals("Filipino / Tagalog", LanguageCatalog.find("Tagalog")!!.name)
        assertEquals("Hokkien / Taiwanese", LanguageCatalog.find("taiwanese")!!.name)
        assertNull(LanguageCatalog.find("Klingon"))
        assertEquals("Klingon", LanguageCatalog.canonicalName(" Klingon "))
        assertEquals("Klingon", LanguageCatalog.defaultDialectFor("Klingon"))
        assertTrue(LanguageCatalog.dialectsOf("Klingon").isEmpty())
    }

    @Test
    fun `search matches languages, accents and places and ranks prefix language hits first`() {
        val hits = LanguageCatalog.search("Québec")
        assertTrue(hits.any { it.language.name == "French" && it.dialect?.name?.startsWith("Québec") == true })

        val byPlace = LanguageCatalog.search("Buenos Aires")
        assertEquals("Spanish", byPlace.first().language.name)
        assertTrue(byPlace.first().dialect!!.name.contains("Rioplatense"))

        val port = LanguageCatalog.search("port")
        assertEquals("Portuguese", port.first().language.name)
        assertNull(port.first().dialect)

        assertTrue(LanguageCatalog.search("").isEmpty())
        assertTrue(LanguageCatalog.search("zzzzqqq").isEmpty())
    }

    @Test
    fun `popular languages are the biggest ones`() {
        val popular = LanguageCatalog.popular.map { it.name }
        assertEquals(12, popular.size)
        assertTrue(popular.containsAll(listOf("English", "Mandarin Chinese", "Hindi", "Spanish", "Arabic", "French")))
    }
}
