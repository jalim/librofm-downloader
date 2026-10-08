package com.vishnurajeevan.libroabs.server.ui

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** `3725` seconds -> `1h 2m`. */
fun formatDuration(totalSeconds: Int): String {
  if (totalSeconds <= 0) return "—"
  val hours = totalSeconds / 3600
  val minutes = (totalSeconds % 3600) / 60
  return when {
    hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
    hours > 0 -> "${hours}h"
    minutes > 0 -> "${minutes}m"
    else -> "${totalSeconds}s"
  }
}

/** Elapsed time for a download attempt, e.g. `2m 05s` or `45s`. */
fun formatElapsed(ms: Long): String {
  val seconds = (ms / 1000).coerceAtLeast(0)
  val h = seconds / 3600
  val m = (seconds % 3600) / 60
  val s = seconds % 60
  return when {
    h > 0 -> "${h}h ${m.toString().padStart(2, '0')}m"
    m > 0 -> "${m}m ${s.toString().padStart(2, '0')}s"
    else -> "${s}s"
  }
}

fun isoUtc(epochMs: Long): String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMs))

private val utcText = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)

/** Fallback text for browsers without scripting; scripts replace it with the viewer's local time. */
fun utcText(epochMs: Long): String = utcText.format(Instant.ofEpochMilli(epochMs))

/**
 * Normalises user input to an ISBN: removes hyphens and spaces and upper-cases a trailing `X`.
 * Returns `null` when the result is not a plausible ISBN-10 or ISBN-13.
 */
fun parseIsbn(raw: String?): String? {
  val cleaned = raw.orEmpty().filterNot { it == '-' || it.isWhitespace() }.uppercase()
  return when {
    cleaned.length == 13 && cleaned.all { it.isDigit() } -> cleaned
    cleaned.length == 10 && cleaned.dropLast(1).all { it.isDigit() } && (cleaned.last().isDigit() || cleaned.last() == 'X') -> cleaned
    else -> null
  }
}

/**
 * Builds the value of an `HX-Trigger` header that shows a toast. HTTP headers must be ASCII, so any
 * non-ASCII character (titles often contain them) is written as a JSON unicode escape.
 */
fun toastTrigger(message: String, kind: String = "ok"): String {
  val json = JsonObject(
    mapOf(
      "toast" to JsonObject(
        mapOf(
          "message" to JsonPrimitive(message),
          "kind" to JsonPrimitive(kind),
        )
      )
    )
  ).toString()
  return buildString {
    for (c in json) {
      if (c.code in 0x20..0x7e) append(c) else append("\\u").append(c.code.toString(16).padStart(4, '0'))
    }
  }
}

fun String.truncate(max: Int): String = if (length <= max) this else take(max - 1).trimEnd() + "…"

fun pluralize(count: Int, singular: String, plural: String = singular + "s"): String =
  "$count ${if (count == 1) singular else plural}"

/** `2021-05-04` for a publication date. */
fun kotlinx.datetime.Instant.isoDate(): String = isoUtc(toEpochMilliseconds()).take(10)
