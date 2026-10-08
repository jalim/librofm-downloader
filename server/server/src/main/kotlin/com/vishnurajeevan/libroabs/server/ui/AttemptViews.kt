package com.vishnurajeevan.libroabs.server.ui

import com.vishnurajeevan.libroabs.db.repo.AttemptPage
import com.vishnurajeevan.libroabs.models.server.AttemptStatus
import com.vishnurajeevan.libroabs.models.server.AttemptTrigger
import com.vishnurajeevan.libroabs.models.server.DownloadAttempt
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.pre
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.summary
import java.net.URLEncoder

/** Where the result of an action on an attempt is rendered. */
class ViewTarget(val target: String, val swap: String, val include: String?)

fun viewTarget(view: String?): ViewTarget = when (view) {
  "history" -> ViewTarget("#history-results", "innerHTML", "#history-filters, #history-status, #history-page")
  "queue" -> ViewTarget("#queue-panel", "outerHTML", null)
  else -> ViewTarget("#book-live", "outerHTML", null)
}

fun AttemptTrigger.label() = when (this) {
  AttemptTrigger.SCHEDULED -> "Scheduled sync"
  AttemptTrigger.MANUAL -> "Manual"
  AttemptTrigger.RETRY -> "Retry"
}

fun FlowContent.attemptRow(
  attempt: DownloadAttempt,
  showTitle: Boolean = true,
  view: String = "history",
  step: String? = null,
  withActions: Boolean = true,
) {
  val ctx = viewTarget(view)
  div("row") {
    id = "attempt-${attempt.id}"
    div {
      div("top") {
        if (showTitle) a("/ui/book/${attempt.isbn}", classes = "name") { +attempt.title }
        attemptBadge(attempt.status)
        span("muted small") { +attempt.trigger.label() }
      }
      div("facts") {
        span {
          +"Queued "
          timestamp(attempt.queuedAt, relative = true)
        }
        attempt.durationMs?.let { span { +"Took ${formatElapsed(it)}" } }
        attempt.format?.let { span { +"Format: ${formatLabel(it.name)}" } }
        attempt.requestedFormat?.let { span { +"Requested: ${it.label()}" } }
        if (showTitle) span("mono") { +attempt.isbn }
      }
      if (attempt.status.isActive && step != null) {
        div("small muted") { +"$step…" }
      }
      attempt.error?.let { error ->
        if (attempt.status == AttemptStatus.FAILED) {
          details("err") {
            summary { +error.truncate(120) }
            pre { +error }
          }
        } else {
          div("small muted") { +error }
        }
      }
    }
    if (withActions) {
      div("row-actions") {
        when {
          attempt.status.isActive ->
            actionButton("Cancel", "/ui/attempts/${attempt.id}/cancel?view=$view", ctx.target, "btn small danger", ctx.swap, include = ctx.include)

          else -> {
            actionButton(
              if (attempt.status == AttemptStatus.SUCCEEDED) "Download again" else "Retry",
              "/ui/attempts/${attempt.id}/retry?view=$view", ctx.target,
              if (attempt.status == AttemptStatus.SUCCEEDED) "btn small" else "btn small primary",
              ctx.swap, include = ctx.include,
            )
            actionButton(
              "Delete", "/ui/attempts/${attempt.id}/delete?view=$view", ctx.target,
              "btn small danger", ctx.swap, include = ctx.include,
              confirm = "Delete this history record?"
            )
          }
        }
      }
    }
  }
}

// ---- history page ----

class HistoryQuery(val text: String, val status: AttemptStatus?, val active: Boolean, val page: Int) {
  fun href(page: Int = this.page, status: String? = statusParam(), text: String = this.text): String = buildString {
    append("/ui/history?page=").append(page)
    if (!status.isNullOrEmpty()) append("&status=").append(status)
    if (text.isNotEmpty()) append("&q=").append(URLEncoder.encode(text, Charsets.UTF_8))
  }

  fun statusParam(): String? = if (active) "ACTIVE" else status?.name

  companion object {
    const val PAGE_SIZE = 25

    fun parse(params: io.ktor.http.Parameters): HistoryQuery {
      val raw = params["status"]?.uppercase()
      return HistoryQuery(
        text = params["q"].orEmpty().trim(),
        status = AttemptStatus.entries.firstOrNull { it.name == raw && !it.isActive },
        active = raw == "ACTIVE",
        page = params["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
      )
    }
  }
}

fun FlowContent.historyPage(query: HistoryQuery, page: AttemptPage, counts: Map<AttemptStatus, Long>) {
  pageHead("History", "Every download attempt, including failures.")
  form(action = "/ui/history", method = kotlinx.html.FormMethod.get, classes = "toolbar") {
    id = "history-filters"
    attributes["hx-get"] = "/ui/history"
    attributes["hx-target"] = "#history-results"
    attributes["hx-trigger"] = "input changed delay:300ms, submit"
    attributes["hx-push-url"] = "true"
    attributes["hx-include"] = "#history-status"
    label("field search") {
      span("sr-only") { +"Search history" }
      input(type = InputType.search, name = "q") {
        placeholder = "Search by title or ISBN"
        value = query.text
        attributes["autocomplete"] = "off"
      }
    }
  }
  div {
    id = "history-results"
    historyResults(query, page, counts)
  }
  form(classes = "card") {
    id = "clear-form"
    span("muted small") { +"Clean up finished records. Active downloads are never removed." }
    div("inline-form") {
      label("field") {
        span { +"Remove" }
        select {
          attributes["name"] = "older"
          option { value = "30"; +"Finished more than 30 days ago" }
          option { value = "7"; +"Finished more than 7 days ago" }
          option { value = "all"; +"All finished records" }
        }
      }
      button(classes = "btn danger", type = ButtonType.button) {
        attributes["hx-post"] = "/ui/history/clear"
        attributes["hx-include"] = "#clear-form, #history-filters, #history-status, #history-page"
        attributes["hx-target"] = "#history-results"
        attributes["hx-confirm"] = "Delete these history records? This cannot be undone."
        +"Clear"
      }
    }
  }
}

fun FlowContent.historyResults(query: HistoryQuery, page: AttemptPage, counts: Map<AttemptStatus, Long>) {
  input(type = InputType.hidden, name = "status") {
    id = "history-status"
    value = query.statusParam().orEmpty()
  }
  input(type = InputType.hidden, name = "page") {
    id = "history-page"
    value = query.page.toString()
  }
  val activeCount = (counts[AttemptStatus.QUEUED] ?: 0) + (counts[AttemptStatus.RUNNING] ?: 0)
  val all = counts.values.sum()
  div("tabs") {
    attributes["role"] = "tablist"
    historyTab(query, "All", null, all)
    historyTab(query, "Succeeded", "SUCCEEDED", counts[AttemptStatus.SUCCEEDED] ?: 0)
    historyTab(query, "Failed", "FAILED", counts[AttemptStatus.FAILED] ?: 0)
    historyTab(query, "Cancelled", "CANCELLED", counts[AttemptStatus.CANCELLED] ?: 0)
    historyTab(query, "Active", "ACTIVE", activeCount)
  }
  div("card") {
    if (page.attempts.isEmpty()) {
      emptyState(
        if (all == 0L) "No downloads yet" else "Nothing matches",
        if (all == 0L) "Download attempts are recorded here once a sync or a manual download runs." else "Try another status or search."
      )
    } else {
      div("rows") {
        page.attempts.forEach { attemptRow(it, view = "history") }
      }
    }
  }
  pager(
    query.page,
    ((page.total + HistoryQuery.PAGE_SIZE - 1) / HistoryQuery.PAGE_SIZE).toInt().coerceAtLeast(1),
    { query.href(page = it) },
    "#history-results",
  )
}

private fun FlowContent.historyTab(query: HistoryQuery, label: String, status: String?, count: Long) {
  val selected = query.statusParam() == status
  val href = query.href(page = 1, status = status)
  a(href, classes = "tab") {
    attributes["hx-get"] = href
    attributes["hx-target"] = "#history-results"
    attributes["hx-push-url"] = "true"
    attributes["aria-current"] = selected.toString()
    +label
    span("count") { +count.toString() }
  }
}
