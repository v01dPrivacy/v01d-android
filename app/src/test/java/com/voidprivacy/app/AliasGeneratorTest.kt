package com.voidprivacy.app

import com.voidprivacy.app.tools.emailalias.AliasGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasGeneratorTest {

    @Test
    fun `same key and site produce same alias`() {
        val a1 = AliasGenerator.generate("mysecret", "netflix", "void.alias")
        val a2 = AliasGenerator.generate("mysecret", "netflix", "void.alias")
        assertEquals(a1, a2)
    }

    @Test
    fun `different sites produce different aliases`() {
        val a1 = AliasGenerator.generate("mysecret", "netflix", "void.alias")
        val a2 = AliasGenerator.generate("mysecret", "amazon", "void.alias")
        assertNotEquals(a1, a2)
    }

    @Test
    fun `different keys produce different aliases`() {
        val a1 = AliasGenerator.generate("key1", "netflix", "void.alias")
        val a2 = AliasGenerator.generate("key2", "netflix", "void.alias")
        assertNotEquals(a1, a2)
    }

    @Test
    fun `alias has correct domain`() {
        val alias = AliasGenerator.generate("key", "site", "example.com")
        assertTrue(alias.endsWith("@example.com"))
    }

    @Test
    fun `alias local part is 12 chars`() {
        val alias = AliasGenerator.generate("key", "site", "void.alias")
        val local = alias.substringBefore("@")
        assertEquals(12, local.length)
    }

    @Test
    fun `site name is case insensitive`() {
        val a1 = AliasGenerator.generate("key", "Netflix", "void.alias")
        val a2 = AliasGenerator.generate("key", "netflix", "void.alias")
        assertEquals(a1, a2)
    }

    @Test
    fun `site name is trimmed`() {
        val a1 = AliasGenerator.generate("key", "  netflix  ", "void.alias")
        val a2 = AliasGenerator.generate("key", "netflix", "void.alias")
        assertEquals(a1, a2)
    }

    @Test
    fun `alias contains only valid chars`() {
        val alias = AliasGenerator.generate("key", "site", "void.alias")
        val local = alias.substringBefore("@")
        assertTrue(local.all { it in 'a'..'z' || it in '2'..'7' })
    }
}
