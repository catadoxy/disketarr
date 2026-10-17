package io.github.catadoxy.disketarr.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BaseUrlNormalizerTest {

    @Test
    fun keeps_explicit_https_url() {
        assertEquals(
            "https://disketarr.example.com",
            BaseUrlNormalizer.normalize("https://disketarr.example.com"),
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
            "https://disketarr.example.com",
            BaseUrlNormalizer.normalize("  HTTPS://disketarr.example.com/  "),
        )
    }

    @Test
    fun preserves_sub_path() {
        assertEquals(
            "https://example.com/disketarr",
            BaseUrlNormalizer.normalize("https://example.com/disketarr/"),
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
        assertEquals("http://disketarr.local", BaseUrlNormalizer.normalize("disketarr.local"))
        assertEquals("http://disketarr.lan:8080", BaseUrlNormalizer.normalize("disketarr.lan:8080"))
        assertEquals("http://disketarr.internal", BaseUrlNormalizer.normalize("disketarr.internal"))
        assertEquals("http://disketarr", BaseUrlNormalizer.normalize("disketarr"))
    }

    @Test
    fun defaults_public_domains_to_https() {
        assertEquals("https://disketarr.example.com", BaseUrlNormalizer.normalize("disketarr.example.com"))
        assertEquals("https://disketarr.example.com:8443", BaseUrlNormalizer.normalize("disketarr.example.com:8443"))
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

