package com.vishnurajeevan.libroabs.server.ui

import com.vishnurajeevan.libroabs.models.server.AttemptStatus
import com.vishnurajeevan.libroabs.models.server.AttemptTrigger
import com.vishnurajeevan.libroabs.models.server.DownloadAttempt
import com.vishnurajeevan.libroabs.models.server.DownloadedFormat
import com.vishnurajeevan.libroabs.models.server.ItemDownloadHistory
import io.ktor.http.parametersOf
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryQueryTest {
  private fun attempt(status: AttemptStatus) =
    DownloadAttempt(1, "x", "x", AttemptTrigger.MANUAL, status, null, null, null, 0, null, null)

  private fun entry(
    i: Int,
    title: String = "Title $i",
    authors: List<String> = listOf("Author $i"),
    downloaded: Boolean = false,
    status: AttemptStatus? = null,
  ) = LibraryEntry(
    book = sampleBook(i, title, authors),
    download = if (downloaded) ItemDownloadHistory("x", DownloadedFormat.MP3, "/p", false) else null,
    latestAttempt = status?.let { attempt(it) },
  )

  @Test
  fun stateIsDerivedFromHistoryAndLatestAttempt() {
    assertEquals(BookState.NOT_DOWNLOADED, entry(1).state)
    assertEquals(BookState.DOWNLOADED, entry(1, downloaded = true).state)
    assertEquals(BookState.FAILED, entry(1, status = AttemptStatus.FAILED).state)
    assertEquals(BookState.NOT_DOWNLOADED, entry(1, status = AttemptStatus.CANCELLED).state)
    assertEquals(BookState.QUEUED, entry(1, status = AttemptStatus.QUEUED).state)
    assertEquals(BookState.DOWNLOADING, entry(1, downloaded = true, status = AttemptStatus.RUNNING).state)
    // A failed re-download of an already downloaded book is still downloaded.
    assertEquals(BookState.DOWNLOADED, entry(1, downloaded = true, status = AttemptStatus.FAILED).state)
  }

  private val library = listOf(
    entry(1, "Dune", listOf("Frank Herbert"), downloaded = true),
    entry(2, "Emma", listOf("Jane Austen"), status = AttemptStatus.FAILED),
    entry(3, "Persuasion", listOf("Jane Austen")),
    entry(4, "Children of Dune", listOf("Frank Herbert"), status = AttemptStatus.RUNNING),
  )

  @Test
  fun textSearchMatchesAllTermsAcrossFields() {
    assertEquals(listOf("Dune", "Children of Dune"), library.query(LibraryQuery(text = "dune")).entries.map { it.book.title })
    assertEquals(listOf("Children of Dune"), library.query(LibraryQuery(text = "herbert children")).entries.map { it.book.title })
    assertEquals(listOf("Emma", "Persuasion"), library.query(LibraryQuery(text = "austen")).entries.map { it.book.title })
    assertEquals(listOf("Dune"), library.query(LibraryQuery(text = library[0].book.isbn)).entries.map { it.book.title })
    assertEquals(emptyList(), library.query(LibraryQuery(text = "nothing")).entries)
  }

  @Test
  fun filters() {
    fun titles(f: LibraryFilter) = library.query(LibraryQuery(filter = f)).entries.map { it.book.title }
    assertEquals(4, titles(LibraryFilter.ALL).size)
    assertEquals(listOf("Dune"), titles(LibraryFilter.DOWNLOADED))
    assertEquals(listOf("Persuasion"), titles(LibraryFilter.MISSING))
    assertEquals(listOf("Emma"), titles(LibraryFilter.FAILED))
    assertEquals(listOf("Children of Dune"), titles(LibraryFilter.ACTIVE))
  }

  @Test
  fun sorting() {
    fun titles(s: LibrarySort) = library.query(LibraryQuery(sort = s)).entries.map { it.book.title }
    assertEquals(listOf("Children of Dune", "Dune", "Emma", "Persuasion"), titles(LibrarySort.TITLE))
    // Frank Herbert (Children of Dune, Dune) then Jane Austen (Emma, Persuasion)
    assertEquals(listOf("Children of Dune", "Dune", "Emma", "Persuasion"), titles(LibrarySort.AUTHOR))
  }

  @Test
  fun paginationClampsPageAndReportsCounts() {
    val many = (1..65).map { entry(it) }
    val first = many.query(LibraryQuery(page = 1))
    assertEquals(LibraryQuery.PAGE_SIZE, first.entries.size)
    assertEquals(3, first.pageCount)
    assertEquals(65, first.total)
    val last = many.query(LibraryQuery(page = 99))
    assertEquals(3, last.page)
    assertEquals(5, last.entries.size)
    val empty = emptyList<LibraryEntry>().query(LibraryQuery(page = 5))
    assertEquals(1, empty.page)
    assertEquals(1, empty.pageCount)
  }

  @Test
  fun parsingIgnoresGarbage() {
    val q = LibraryQuery.parse(parametersOf("q" to listOf("  hi  "), "filter" to listOf("nope"), "sort" to listOf("title"), "page" to listOf("-4")))
    assertEquals("hi", q.text)
    assertEquals(LibraryFilter.ALL, q.filter)
    assertEquals(LibrarySort.TITLE, q.sort)
    assertEquals(1, q.page)
  }
}
