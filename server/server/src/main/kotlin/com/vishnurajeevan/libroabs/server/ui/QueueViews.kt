package com.vishnurajeevan.libroabs.server.ui

import com.vishnurajeevan.libroabs.models.server.AttemptStatus
import com.vishnurajeevan.libroabs.models.server.BookFormat
import com.vishnurajeevan.libroabs.models.server.DownloadAttempt
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.span

fun FlowContent.queuePage(active: List<ActiveAttempt>, recent: List<DownloadAttempt>, defaultFormat: BookFormat) {
  pageHead("Queue", "Downloads in progress and waiting.") {
    button(classes = "btn", type = ButtonType.button) {
      attributes["hx-post"] = "/ui/queue/retry-failed"
      attributes["hx-target"] = "#queue-panel"
      attributes["hx-swap"] = "outerHTML"
      icon(Icon.RETRY, 16)
      +"Retry failed"
    }
    button(classes = "btn primary", type = ButtonType.button) {
      attributes["hx-post"] = "/ui/queue/download-missing"
      attributes["hx-target"] = "#queue-panel"
      attributes["hx-swap"] = "outerHTML"
      attributes["hx-confirm"] = "Queue a download for every book that hasn't been downloaded?"
      icon(Icon.DOWNLOAD, 16)
      +"Download all missing"
    }
  }
  form(classes = "card") {
    attributes["hx-post"] = "/ui/queue/by-isbn"
    attributes["hx-target"] = "#queue-panel"
    attributes["hx-swap"] = "outerHTML"
    attributes["hx-on::after-request"] = "if (event.detail.successful) this.reset()"
    h2 { +"Download by ISBN" }
    span("muted small") { +"Fetch a specific book, even one that was already downloaded or isn't in the synced library yet." }
    div("inline-form") {
      label("field") {
        span { +"ISBN" }
        input(type = InputType.text, name = "isbn") {
          placeholder = "9781234567890"
          required = true
          attributes["inputmode"] = "numeric"
          attributes["autocomplete"] = "off"
        }
      }
      div("field") {
        span { +"Format" }
        formatSelect(defaultFormat, id = "isbn-format")
      }
      button(classes = "btn primary", type = ButtonType.submit) { +"Download" }
    }
  }
  queuePanel(active, recent)
}

fun FlowContent.queuePanel(active: List<ActiveAttempt>, recent: List<DownloadAttempt>) {
  div("grid") {
    id = "queue-panel"
    attributes["hx-get"] = "/ui/queue/panel"
    attributes["hx-trigger"] = if (active.isEmpty()) "every 10s" else "every 2s"
    attributes["hx-swap"] = "outerHTML"
    div("card") {
      div("page-head") {
        h2 { +"Active" }
        span("muted small") { +"${active.count { it.attempt.status == AttemptStatus.RUNNING }} running · ${active.count { it.attempt.status == AttemptStatus.QUEUED }} waiting" }
      }
      if (active.isEmpty()) {
        emptyState("Nothing is downloading", "Start a download from the Library or by ISBN.")
      } else {
        div("rows") {
          active.sortedBy { if (it.attempt.status == AttemptStatus.RUNNING) 0 else 1 }.forEach {
            attemptRow(it.attempt, view = "queue", step = it.step)
          }
        }
      }
    }
    div("card") {
      div("page-head") {
        h2 { +"Recently finished" }
        a("/ui/history", classes = "small") { +"Full history →" }
      }
      if (recent.isEmpty()) {
        emptyState("No finished downloads yet")
      } else {
        div("rows") {
          recent.forEach { attemptRow(it, view = "queue") }
        }
      }
    }
  }
}
