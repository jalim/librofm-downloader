package com.vishnurajeevan.libroabs.server.ui

import com.vishnurajeevan.libroabs.models.server.AttemptStatus
import com.vishnurajeevan.libroabs.models.server.BookFormat
import kotlinx.html.FlowContent
import kotlinx.html.FlowOrPhrasingContent
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.strong
import kotlinx.html.option
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.time
import kotlinx.html.unsafe

enum class Icon(private val body: String) {
  HOME("""<path d="M3 11l9-8 9 8"/><path d="M5 10v10h5v-6h4v6h5V10"/>"""),
  LIBRARY("""<path d="M4 4h4v16H4z"/><path d="M10 4h4v16h-4z"/><path d="M16 5l4 1-3.5 14-4-1z"/>"""),
  QUEUE("""<path d="M12 3v12"/><path d="M7 11l5 5 5-5"/><path d="M5 20h14"/>"""),
  HISTORY("""<path d="M3 12a9 9 0 1 0 3-6.7"/><path d="M3 4v5h5"/><path d="M12 7v5l3 2"/>"""),
  SYNC("""<path d="M20 11a8 8 0 0 0-14-4L4 9"/><path d="M4 4v5h5"/><path d="M4 13a8 8 0 0 0 14 4l2-2"/><path d="M20 20v-5h-5"/>"""),
  SETTINGS("""<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>"""),
  HEADPHONES("""<path d="M4 15v-3a8 8 0 0 1 16 0v3"/><rect x="3" y="14" width="4" height="7" rx="1.5"/><rect x="17" y="14" width="4" height="7" rx="1.5"/>"""),
  DOWNLOAD("""<path d="M12 4v11"/><path d="M7 11l5 5 5-5"/><path d="M5 20h14"/>"""),
  RETRY("""<path d="M3 12a9 9 0 0 1 15.5-6.2L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-15.5 6.2L3 16"/><path d="M3 21v-5h5"/>"""),
  X("""<path d="M6 6l12 12M18 6L6 18"/>""");

  fun svg(size: Int = 20): String =
    """<svg width="$size" height="$size" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">$body</svg>"""
}

fun FlowOrPhrasingContent.icon(icon: Icon, size: Int = 20) {
  span { unsafe { raw(icon.svg(size)) } }
}

/** A `<time>` that scripts localise; the text shown without scripting is UTC. */
fun FlowOrPhrasingContent.timestamp(epochMs: Long, relative: Boolean = false) {
  time {
    attributes["datetime"] = isoUtc(epochMs)
    if (relative) attributes["data-fmt"] = "relative"
    +utcText(epochMs)
  }
}

fun FlowContent.badge(text: String, kind: String = "", dot: Boolean = false) {
  span("badge $kind".trim()) {
    if (dot) span("dot busy")
    +text
  }
}

fun FlowContent.stateBadge(entry: LibraryEntry) {
  val state = entry.state
  when (state) {
    BookState.DOWNLOADED -> badge("Downloaded" + (entry.download?.format?.let { " · ${formatLabel(it.name)}" } ?: ""), "ok")
    BookState.QUEUED -> badge("Queued", "info", dot = true)
    BookState.DOWNLOADING -> badge("Downloading", "info", dot = true)
    BookState.FAILED -> badge("Failed", "bad")
    BookState.NOT_DOWNLOADED -> badge("Not downloaded")
  }
}

fun FlowContent.attemptBadge(status: AttemptStatus) {
  when (status) {
    AttemptStatus.QUEUED -> badge("Queued", "info", dot = true)
    AttemptStatus.RUNNING -> badge("Running", "info", dot = true)
    AttemptStatus.SUCCEEDED -> badge("Succeeded", "ok")
    AttemptStatus.FAILED -> badge("Failed", "bad")
    AttemptStatus.CANCELLED -> badge("Cancelled", "warn")
  }
}

fun formatLabel(name: String): String = when (name) {
  "M4B" -> "M4B"
  "M4B_CONVERTED" -> "M4B (converted)"
  "MP3" -> "MP3"
  else -> name
}

fun BookFormat.label(): String = when (this) {
  BookFormat.MP3 -> "MP3"
  BookFormat.M4B_MP3_FALLBACK -> "M4B, MP3 fallback"
  BookFormat.M4B_CONVERT_FALLBACK -> "M4B, converted fallback"
}

/** A `<select name=format>` offering the configured default plus each format. */
fun FlowContent.formatSelect(default: BookFormat, id: String = "format") {
  select {
    this.id = id
    attributes["name"] = "format"
    option {
      value = ""
      selected = true
      +"Default (${default.label()})"
    }
    BookFormat.entries.forEach {
      option {
        value = it.name
        +it.label()
      }
    }
  }
}

fun FlowContent.pager(page: Int, pageCount: Int, hrefFor: (Int) -> String, target: String) {
  if (pageCount <= 1) return
  div("pager") {
    if (page > 1) {
      a(hrefFor(page - 1), classes = "btn small") {
        attributes["hx-get"] = hrefFor(page - 1)
        attributes["hx-target"] = target
        attributes["hx-push-url"] = "true"
        +"← Previous"
      }
    }
    span("muted small") { +"Page $page of $pageCount" }
    if (page < pageCount) {
      a(hrefFor(page + 1), classes = "btn small") {
        attributes["hx-get"] = hrefFor(page + 1)
        attributes["hx-target"] = target
        attributes["hx-push-url"] = "true"
        +"Next →"
      }
    }
  }
}

fun FlowContent.emptyState(title: String, hint: String? = null) {
  div("empty") {
    strong { +title }
    if (hint != null) span { +hint }
  }
}

fun FlowContent.actionButton(
  label: String,
  post: String,
  target: String,
  classes: String = "btn small",
  swap: String = "outerHTML",
  confirm: String? = null,
  include: String? = null,
) {
  button(classes = classes) {
    attributes["type"] = "button"
    attributes["hx-post"] = post
    attributes["hx-target"] = target
    attributes["hx-swap"] = swap
    if (confirm != null) attributes["hx-confirm"] = confirm
    if (include != null) attributes["hx-include"] = include
    +label
  }
}
