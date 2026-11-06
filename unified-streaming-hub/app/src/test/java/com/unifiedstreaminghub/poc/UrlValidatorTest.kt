package com.unifiedstreaminghub.poc

import com.unifiedstreaminghub.poc.domain.UrlCheck
import com.unifiedstreaminghub.poc.domain.UrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {

    @Test
    fun `accepts https url and trims surrounding whitespace`() {
        val result = UrlValidator.validate("  https://www.example.org/title/123?x=1  ")
        assertEquals(UrlCheck.Valid("https://www.example.org/title/123?x=1", "www.example.org"), result)
    }

    @Test
    fun `scheme check is case insensitive`() {
        assertTrue(UrlValidator.validate("HTTPS://example.org/a") is UrlCheck.Valid)
    }

    @Test
    fun `rejects empty and blank input`() {
        assertTrue(UrlValidator.validate("") is UrlCheck.Invalid)
        assertTrue(UrlValidator.validate("   ") is UrlCheck.Invalid)
    }

    @Test
    fun `rejects http, custom schemes and intent urls`() {
        listOf(
            "http://example.org/a",
            "someapp://title/1",
            "intent://x#Intent;scheme=https;end",
            "javascript:alert(1)",
        ).forEach { assertTrue("should reject $it", UrlValidator.validate(it) is UrlCheck.Invalid) }
    }

    @Test
    fun `rejects missing host and inner whitespace`() {
        assertTrue(UrlValidator.validate("https:///path") is UrlCheck.Invalid)
        assertTrue(UrlValidator.validate("https://exa mple.org/a") is UrlCheck.Invalid)
        assertTrue(UrlValidator.validate("not a url") is UrlCheck.Invalid)
    }
}
