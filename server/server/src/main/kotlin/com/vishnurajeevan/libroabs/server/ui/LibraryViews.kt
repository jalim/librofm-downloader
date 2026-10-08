package com.vishnurajeevan.libroabs.server.ui

import com.vishnurajeevan.libroabs.models.libro.Book
import com.vishnurajeevan.libroabs.models.server.BookFormat
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.select
import kotlinx.html.span
import java.net.URLEncoder

private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8)

fun LibraryQuery.href(page: Int = this.page): String = buildString {
  append("/ui/library?page=").append(page)
  if (text.isNotEmpty()) append("&q=").append(enc(text))
  if (filter != LibraryFilter.ALL) append("&filter=").append(filter.name)
  if (sort != LibrarySort.LIBRARY) append("&sort=").append(sort.name)
}

fun FlowContent.libraryPage(query: LibraryQuery, page: LibraryPage) {
  pageHead("Library", "Books on your libro.fm account and their download state.") {
    button(classes = "btn primary") {
      attributes["type"] = "button"
      attributes["hx-post"] = "/ui/library/download-missing"
      attributes["hx-include"] = "#filters"
      attributes["hx-target"] = "#library-results"
      attributes["hx-confirm"] = "Queue a download for every book that hasn't been downloaded?"
      icon(Icon.DOWNLOAD, 16)
      +"Download all missing"
    }
  }
  form(action = "/ui/library", method = kotlinx.html.FormMethod.get, classes = "toolbar") {
    id = "filters"
    attributes["hx-get"] = "/ui/library"
    attributes["hx-target"] = "#library-results"
    attributes["hx-trigger"] = "input changed delay:300ms, submit"
    attributes["hx-push-url"] = "true"
    label("field search") {
      span("sr-only") { +"Search" }
      input(type = InputType.search, name = "q") {
        placeholder = "Search title, author, narrator, series or ISBN"
        value = query.text
        attributes["autocomplete"] = "off"
      }
    }
    label("field") {
      span("sr-only") { +"Filter" }
      select {
        attributes["name"] = "filter"
        LibraryFilter.entries.forEach {
          option {
            value = it.name
            selected = it == query.filter
            +it.label
          }
        }
      }
    }
    label("field") {
      span("sr-only") { +"Sort" }
      select {
        attributes["name"] = "sort"
        LibrarySort.entries.forEach {
          option {
            value = it.name
            selected = it == query.sort
            +it.label
          }
        }
      }
    }
  }
  div {
    id = "library-results"
    libraryResults(query, page)
  }
}

fun FlowContent.libraryResults(query: LibraryQuery, page: LibraryPage) {
  input(type = InputType.hidden, name = "page") {
    id = "library-page"
    value = page.page.toString()
  }
  div("bulkbar") {
    label("check") {
      input(type = InputType.checkBox) {
        attributes["aria-label"] = "Select all books on this page"
        attributes["onclick"] =
          "document.querySelectorAll('#library-results input[name=isbn]').forEach(function(c){c.checked=this.checked}.bind(this))"
      }
      span("muted small") {
        +if (page.matching == page.total) "${pluralize(page.total, "book")}" else "${page.matching} of ${page.total} books"
      }
    }
    button(classes = "btn small") {
      attributes["type"] = "button"
      attributes["hx-post"] = "/ui/library/download"
      attributes["hx-include"] = "#filters, #library-page, #library-results input[name=isbn]:checked"
      attributes["hx-target"] = "#library-results"
      +"Download selected"
    }
  }
  if (page.entries.isEmpty()) {
    div("card") {
      if (page.total == 0) {
        emptyState(
          "Your library is empty",
          "Books appear here after the first library sync. Use Sync to check libro.fm now."
        )
      } else {
        emptyState("No books match", "Try a different search or filter.")
      }
    }
  } else {
    div("books") {
      attributes["role"] = "list"
      page.entries.forEach { bookRow(it, selectable = true) }
    }
  }
  pager(page.page, page.pageCount, { query.href(it) }, "#library-results")
}

private fun Book.byline(): String = authors.joinToString(", ")

fun FlowContent.bookCover(book: Book, classes: String = "cover") {
  if (book.cover_url.isBlank()) {
    div(classes)
  } else {
    img(src = book.cover_url, alt = "Cover of ${book.title}", classes = classes) {
      attributes["loading"] = "lazy"
      attributes["decoding"] = "async"
      attributes["referrerpolicy"] = "no-referrer"
    }
  }
}

fun FlowContent.bookRow(entry: LibraryEntry, selectable: Boolean = false) {
  val book = entry.book
  val isbn = book.isbn
  div("book") {
    id = "book-$isbn"
    attributes["role"] = "listitem"
    attributes["data-state"] = entry.state.name
    if (entry.state.isActive) {
      attributes["hx-get"] = "/ui/library/row/$isbn"
      attributes["hx-trigger"] = "every 2s"
      attributes["hx-swap"] = "outerHTML"
    }
    if (selectable) {
      label("pick") {
        input(type = InputType.checkBox, name = "isbn") {
          value = isbn
          attributes["aria-label"] = "Select ${book.title}"
        }
      }
    } else {
      span()
    }
    a("/ui/book/$isbn") { bookCover(book) }
    div("info") {
      a("/ui/book/$isbn", classes = "title") { +book.title }
      if (book.authors.isNotEmpty()) div("sub") { +book.byline() }
      div("meta") {
        +bookMeta(book)
      }
      val attempt = entry.latestAttempt
      when {
        entry.state.isActive && entry.step != null -> div("status-line muted") { +"${entry.step}…" }
        entry.state == BookState.FAILED && attempt?.error != null ->
          div("status-line err") { +"Last attempt failed: ${attempt.error!!.truncate(140)}" }
      }
    }
    div("state") { stateBadge(entry) }
    div("actions") { bookRowActions(entry) }
  }
}

fun bookMeta(book: Book): String = listOfNotNull(
  book.series?.let { s -> book.series_num?.let { "$s #$it" } ?: s },
  formatDuration(book.audiobook_info.duration).takeIf { book.audiobook_info.duration > 0 },
  book.publication_date.isoDate().take(4),
).joinToString(" · ")

fun FlowContent.bookRowActions(entry: LibraryEntry) {
  val isbn = entry.book.isbn
  val target = "#book-$isbn"
  when (entry.state) {
    BookState.NOT_DOWNLOADED ->
      actionButton("Download", "/ui/book/$isbn/download?view=row", target, "btn small primary")

    BookState.FAILED ->
      actionButton("Retry", "/ui/book/$isbn/download?view=row", target, "btn small primary")

    BookState.DOWNLOADED ->
      actionButton(
        "Re-download", "/ui/book/$isbn/download?view=row", target,
        confirm = "Download “${entry.book.title}” again? Existing files may be overwritten."
      )

    BookState.QUEUED, BookState.DOWNLOADING -> entry.latestAttempt?.let {
      actionButton("Cancel", "/ui/attempts/${it.id}/cancel?view=row", target, "btn small danger")
    }
  }
}

/** Format picker + download button for the book page. Uses the form's own `format` value. */
fun FlowContent.manualDownloadForm(entry: LibraryEntry, defaultFormat: BookFormat) {
  val isbn = entry.book.isbn
  val verb = when (entry.state) {
    BookState.NOT_DOWNLOADED -> "Download"
    BookState.FAILED -> "Retry download"
    else -> "Download again"
  }
  form(classes = "inline-form") {
    id = "download-form"
    div("field") {
      span { +"Format" }
      formatSelect(defaultFormat, id = "format-$isbn")
    }
    button(classes = "btn primary", type = ButtonType.button) {
      attributes["hx-post"] = "/ui/book/$isbn/download?view=detail"
      attributes["hx-include"] = "#download-form"
      attributes["hx-target"] = "#book-live"
      attributes["hx-swap"] = "outerHTML"
      icon(Icon.DOWNLOAD, 16)
      +verb
    }
  }
}
