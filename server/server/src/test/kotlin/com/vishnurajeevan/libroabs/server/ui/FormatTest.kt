package com.vishnurajeevan.libroabs.server.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormatTest {
  @Test
  fun durations() {
    assertEquals("1h 2m", formatDuration(3725))
    assertEquals("5h", formatDuration(5 * 3600))
    assertEquals("12m", formatDuration(12 * 60 + 5))
    assertEquals("—", formatDuration(0))
  }

  @Test
  fun elapsed() {
    assertEquals("45s", formatElapsed(45_000))
    assertEquals("2m 05s", formatElapsed(125_000))
    assertEquals("1h 01m", formatElapsed(3_660_000))
    assertEquals("0s", formatElapsed(-5))
  }

  @Test
  fun isbnParsing() {
    assertEquals("9781234567890", parseIsbn("978-1-234-56789-0"))
    assertEquals("9781234567890", parseIsbn("  9781234567890 "))
    assertEquals("080442957X", parseIsbn("0-8044-2957-x"))
    assertNull(parseIsbn("12345"))
    assertNull(parseIsbn("97812345678AB"))
    assertNull(parseIsbn(""))
    assertNull(parseIsbn(null))
  }

  @Test
  fun toastHeaderIsAsciiAndEscaped() {
    val header = toastTrigger("“Café ☕” \"quoted\"", "bad")
    assertTrue(header.all { it.code in 0x20..0x7e }, header)
    assertTrue(header.contains("\\u00e9"))
    assertTrue(header.contains("\\\"quoted\\\""))
    assertTrue(header.startsWith("{\"toast\":{\"message\":"))
    assertTrue(header.contains("\"kind\":\"bad\""))
  }

  @Test
  fun truncate() {
    assertEquals("short", "short".truncate(10))
    assertEquals("12345678…", "1234567890".truncate(9))
  }

  @Test
  fun htmlIsStripped() {
    assertEquals("One\nTwo & three", stripHtml("<p>One</p><p>Two &amp; <b>three</b></p>"))
  }

  @Test
  fun safeRedirects() {
    assertEquals("/ui/library?page=2", safeNext("/ui/library?page=2"))
    assertEquals("/ui", safeNext("https://evil.example"))
    assertEquals("/ui", safeNext("//evil.example"))
    assertEquals("/ui", safeNext("/ui\\evil"))
    assertEquals("/ui", safeNext("/other"))
    assertEquals("/ui", safeNext(null))
  }
}
