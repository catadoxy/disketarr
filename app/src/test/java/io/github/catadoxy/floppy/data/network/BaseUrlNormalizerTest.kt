package io.github.catadoxy.floppy.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BaseUrlNormalizerTest {

    @Test
    fun keeps_explicit_https_url() {
        assertEquals(
            "https://floppy.example.com",
            BaseUrlNormalizer.normalize("https://floppy.example.com"),
        )
    }

    @Test
    fun keeps_explicit_http_url_with_port() {
        assertEquals(
            "http://192.168.1.10:8000",
            BaseUrlNormalizer.normalize("http://192.168.1.10:8000"),
        )
    }

    @Test
    fun trims_whitespace_and_trailing_slash() {
        assertEquals(
            "https://floppy.example.com",
            BaseUrlNormalizer.normalize("  HTTPS://Floppy.Example.com/  "),
        )
    }

    @Test
    fun preserves_sub_path() {
        assertEquals(
            "https://example.com/floppy",
            BaseUrlNormalizer.normalize("https://example.com/floppy/"),
        )
    }

    @Test
    fun defaults_lan_addresses_to_http() {
        assertEquals("http://192.168.1.10:8000", BaseUrlNormalizer.normalize("192.168.1.10:8000"))
        assertEquals("http://10.0.0.5", BaseUrlNormalizer.normalize("10.0.0.5"))
        assertEquals("http://172.16.4.2:8000", BaseUrlNormalizer.normalize("172.16.4.2:8000"))
    }

    @Test
    fun defaults_tailscale_and_loopback_hosts_to_http() {
        assertEquals("http://100.101.102.103:8000", BaseUrlNormalizer.normalize("100.101.102.103:8000"))
        assertEquals("http://localhost:8000", BaseUrlNormalizer.normalize("localhost:8000"))
        assertEquals("http://127.0.0.1:8000", BaseUrlNormalizer.normalize("127.0.0.1:8000"))
        assertEquals("http://[::1]:8000", BaseUrlNormalizer.normalize("[::1]:8000"))
    }

    @Test
    fun defaults_mdns_and_internal_hosts_to_http() {
        assertEquals("http://floppy.local", BaseUrlNormalizer.normalize("floppy.local"))
        assertEquals("http://floppy.lan:8080", BaseUrlNormalizer.normalize("floppy.lan:8080"))
        assertEquals("http://floppy.internal", BaseUrlNormalizer.normalize("floppy.internal"))
        assertEquals("http://floppy", BaseUrlNormalizer.normalize("floppy"))
    }

    @Test
    fun defaults_public_domains_to_https() {
        assertEquals("https://floppy.example.com", BaseUrlNormalizer.normalize("floppy.example.com"))
        assertEquals("https://floppy.example.com:8443", BaseUrlNormalizer.normalize("floppy.example.com:8443"))
        assertEquals("https://100.200.0.1:8000", BaseUrlNormalizer.normalize("100.200.0.1:8000"))
    }

    @Test
    fun rejects_blank_input() {
        assertThrows(IllegalArgumentException::class.java) { BaseUrlNormalizer.normalize(null) }
        assertThrows(IllegalArgumentException::class.java) { BaseUrlNormalizer.normalize("   ") }
    }

    @Test
    fun rejects_non_http_schemes() {
        assertThrows(IllegalArgumentException::class.java) {
            BaseUrlNormalizer.normalize("ftp://example.com")
        }
    }

    @Test
    fun rejects_unparseable_input() {
        assertThrows(IllegalArgumentException::class.java) {
            BaseUrlNormalizer.normalize("http://exa mple.com")
        }
    }
}
