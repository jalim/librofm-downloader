package com.vishnurajeevan.libroabs.server.ui

enum class LibraryFilter(val label: String) {
  ALL("All books"),
  DOWNLOADED("Downloaded"),
  MISSING("Not downloaded"),
  FAILED("Failed"),
  ACTIVE("Queued / downloading");

  fun matches(entry: LibraryEntry): Boolean = when (this) {
    ALL -> true
    DOWNLOADED -> entry.state == BookState.DOWNLOADED
    MISSING -> entry.state == BookState.NOT_DOWNLOADED
    FAILED -> entry.state == BookState.FAILED
    ACTIVE -> entry.state.isActive
  }
}

enum class LibrarySort(val label: String) {
  LIBRARY("Library order"),
  TITLE("Title (A–Z)"),
  AUTHOR("Author (A–Z)"),
  SERIES("Series"),
  NEWEST("Newest published"),
  OLDEST("Oldest published"),
  LONGEST("Longest first"),
}

data class LibraryQuery(
  val text: String = "",
  val filter: LibraryFilter = LibraryFilter.ALL,
  val sort: LibrarySort = LibrarySort.LIBRARY,
  val page: Int = 1,
) {
  companion object {
    const val PAGE_SIZE = 30

    fun parse(params: io.ktor.http.Parameters): LibraryQuery = LibraryQuery(
      text = params["q"].orEmpty().trim(),
      filter = params["filter"]?.let { v -> LibraryFilter.entries.firstOrNull { it.name.equals(v, true) } }
        ?: LibraryFilter.ALL,
      sort = params["sort"]?.let { v -> LibrarySort.entries.firstOrNull { it.name.equals(v, true) } }
        ?: LibrarySort.LIBRARY,
      page = params["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
    )
  }
}

data class LibraryPage(
  val entries: List<LibraryEntry>,
  val total: Int,
  val matching: Int,
  val page: Int,
  val pageCount: Int,
)

private fun LibraryEntry.matchesText(needle: String): Boolean {
  if (needle.isEmpty()) return true
  val b = book
  return needle.lowercase().split(' ').filter { it.isNotEmpty() }.all { term ->
    b.title.contains(term, true) ||
      b.isbn.contains(term, true) ||
      b.authors.any { it.contains(term, true) } ||
      b.audiobook_info.narrators.any { it.contains(term, true) } ||
      b.series?.contains(term, true) == true
  }
}

fun List<LibraryEntry>.query(query: LibraryQuery): LibraryPage {
  val filtered = filter { query.filter.matches(it) && it.matchesText(query.text) }
  val sorted = when (query.sort) {
    LibrarySort.LIBRARY -> filtered
    LibrarySort.TITLE -> filtered.sortedBy { it.book.title.lowercase() }
    LibrarySort.AUTHOR -> filtered.sortedWith(
      compareBy({ it.book.authors.firstOrNull()?.lowercase() ?: "" }, { it.book.title.lowercase() })
    )
    LibrarySort.SERIES -> filtered.sortedWith(
      compareBy<LibraryEntry> { it.book.series == null }
        .thenBy { it.book.series?.lowercase() ?: "" }
        .thenBy { it.book.series_num ?: Int.MAX_VALUE }
        .thenBy { it.book.title.lowercase() }
    )
    LibrarySort.NEWEST -> filtered.sortedByDescending { it.book.publication_date }
    LibrarySort.OLDEST -> filtered.sortedBy { it.book.publication_date }
    LibrarySort.LONGEST -> filtered.sortedByDescending { it.book.audiobook_info.duration }
  }
  val pageCount = ((sorted.size + LibraryQuery.PAGE_SIZE - 1) / LibraryQuery.PAGE_SIZE).coerceAtLeast(1)
  val page = query.page.coerceIn(1, pageCount)
  return LibraryPage(
    entries = sorted.drop((page - 1) * LibraryQuery.PAGE_SIZE).take(LibraryQuery.PAGE_SIZE),
    total = size,
    matching = sorted.size,
    page = page,
    pageCount = pageCount,
  )
}
