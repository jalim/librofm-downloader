package com.vishnurajeevan.libroabs.db.repo

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.vishnurajeevan.libroabs.db.Database
import com.vishnurajeevan.libroabs.db.Download_history
import com.vishnurajeevan.libroabs.models.server.AttemptStatus
import com.vishnurajeevan.libroabs.models.server.AttemptTrigger
import com.vishnurajeevan.libroabs.models.server.BookFormat
import com.vishnurajeevan.libroabs.models.server.DownloadedFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadAttemptRepoTest {
  private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { Database.Schema.create(it) }
  private val db = Database(driver, Download_history.Adapter(EnumColumnAdapter()))
  private val repo = RealDownloadAttemptRepo(db.downloadAttemptQueries, Dispatchers.Unconfined)

  private fun create(isbn: String, title: String = "Title $isbn", now: Long = 1_000) = runBlocking {
    repo.create(isbn, title, AttemptTrigger.MANUAL, null, now)
  }

  @Test
  fun lifecycleIsRecorded() = runBlocking {
    val id = repo.create("111", "Dune", AttemptTrigger.SCHEDULED, BookFormat.MP3, now = 10)
    assertEquals(AttemptStatus.QUEUED, repo.get(id)!!.status)
    assertEquals(BookFormat.MP3, repo.get(id)!!.requestedFormat)

    repo.markRunning(id, now = 20)
    repo.markFinished(id, AttemptStatus.SUCCEEDED, null, DownloadedFormat.M4B, now = 50)

    val done = repo.get(id)!!
    assertEquals(AttemptStatus.SUCCEEDED, done.status)
    assertEquals(DownloadedFormat.M4B, done.format)
    assertEquals(30L, done.durationMs)
    assertTrue(repo.active().isEmpty())
  }

  @Test
  fun failureKeepsError() = runBlocking {
    val id = create("222")
    repo.markFinished(id, AttemptStatus.FAILED, "boom", null, now = 5)
    val failed = repo.get(id)!!
    assertEquals("boom", failed.error)
    assertNull(failed.format)
  }

  @Test
  fun latestPerIsbnReturnsNewestAttempt() = runBlocking {
    val first = create("333")
    repo.markFinished(first, AttemptStatus.FAILED, "x", null, 2)
    val second = create("333")
    create("444")
    val latest = repo.latestPerIsbn()
    assertEquals(2, latest.size)
    assertEquals(second, latest.getValue("333").id)
  }

  @Test
  fun pageFiltersByStatusAndQuery() = runBlocking {
    val a = create("555", "The Hobbit")
    create("666", "Dune")
    repo.markFinished(a, AttemptStatus.FAILED, "e", null, 3)

    assertEquals(2L, repo.page(null, null, 10, 0).total)
    assertEquals(1L, repo.page(AttemptStatus.FAILED, null, 10, 0).total)
    assertEquals("The Hobbit", repo.page(null, "hobb", 10, 0).attempts.single().title)
    assertEquals("Dune", repo.page(null, "666", 10, 0).attempts.single().title)
    // wildcard characters in the query are literal
    assertEquals(0L, repo.page(null, "%", 10, 0).total)
    assertEquals(1L, repo.page(null, null, 1, 1).attempts.size.toLong())
  }

  @Test
  fun interruptActiveFailsLeftovers() = runBlocking {
    val queued = create("777")
    val running = create("778")
    repo.markRunning(running, 5)
    val finished = create("779")
    repo.markFinished(finished, AttemptStatus.SUCCEEDED, null, DownloadedFormat.MP3, 6)

    assertEquals(2, repo.interruptActive("restart", now = 9))
    assertEquals(AttemptStatus.FAILED, repo.get(queued)!!.status)
    assertEquals("restart", repo.get(running)!!.error)
    assertEquals(AttemptStatus.SUCCEEDED, repo.get(finished)!!.status)
  }

  @Test
  fun deletingNeverTouchesActiveAttempts() = runBlocking {
    val active = create("801", now = 1)
    val done = create("802", now = 2)
    repo.markFinished(done, AttemptStatus.CANCELLED, null, null, 3)

    assertFalse(repo.deleteFinished(active))
    assertNotNull(repo.get(active))
    assertTrue(repo.deleteFinished(done))
    assertNull(repo.get(done))

    val old = create("803", now = 1)
    repo.markFinished(old, AttemptStatus.FAILED, null, null, 3)
    assertEquals(1, repo.deleteFinishedBefore(10))
    assertEquals(0, repo.deleteFinished())
    assertEquals(1, repo.active().size)
  }

  @Test
  fun statusCountsGroupByStatus() = runBlocking {
    create("901")
    val b = create("902")
    repo.markFinished(b, AttemptStatus.FAILED, "e", null, 1)
    assertEquals(mapOf(AttemptStatus.QUEUED to 1L, AttemptStatus.FAILED to 1L), repo.statusCounts())
  }
}
