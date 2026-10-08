package com.vishnurajeevan.libroabs.server.ui

import com.vishnurajeevan.libroabs.db.repo.AttemptPage
import com.vishnurajeevan.libroabs.models.libro.Book
import com.vishnurajeevan.libroabs.models.server.AttemptStatus
import com.vishnurajeevan.libroabs.models.server.BookFormat
import com.vishnurajeevan.libroabs.models.server.DownloadAttempt
import com.vishnurajeevan.libroabs.models.server.ItemDownloadHistory
import com.vishnurajeevan.libroabs.models.server.ServerInfo
import com.vishnurajeevan.libroabs.models.server.SyncSnapshot

/** Where a book stands, derived from the download history and its most recent attempt. */
enum class BookState(val label: String) {
  DOWNLOADED("Downloaded"),
  QUEUED("Queued"),
  DOWNLOADING("Downloading"),
  FAILED("Failed"),
  NOT_DOWNLOADED("Not downloaded");

  val isActive: Boolean get() = this == QUEUED || this == DOWNLOADING
}

data class LibraryEntry(
  val book: Book,
  /** Present when the book is recorded as downloaded. */
  val download: ItemDownloadHistory?,
  val latestAttempt: DownloadAttempt?,
  /** What an active attempt is doing right now. */
  val step: String? = null,
) {
  val state: BookState
    get() = when {
      latestAttempt?.status == AttemptStatus.QUEUED -> BookState.QUEUED
      latestAttempt?.status == AttemptStatus.RUNNING -> BookState.DOWNLOADING
      download != null -> BookState.DOWNLOADED
      latestAttempt?.status == AttemptStatus.FAILED -> BookState.FAILED
      else -> BookState.NOT_DOWNLOADED
    }
}

data class ActiveAttempt(
  val attempt: DownloadAttempt,
  val step: String?,
)

data class DashboardStats(
  val totalBooks: Int,
  val downloaded: Int,
  val notDownloaded: Int,
  val failed: Int,
  val active: Int,
)

data class WishlistOverview(
  val libroSynced: Map<String, Boolean>,
  val trackerEnabled: Boolean,
  val trackerSynced: Map<String, Boolean>,
)

sealed interface EnqueueOutcome {
  data class Queued(val attemptId: Long) : EnqueueOutcome
  data class AlreadyActive(val attemptId: Long) : EnqueueOutcome
  data object NotFound : EnqueueOutcome
  data class Failure(val message: String) : EnqueueOutcome
}

/**
 * Everything the web UI needs from the rest of the application.
 */
interface WebUiBackend {
  val serverInfo: ServerInfo

  suspend fun libraryEntries(): List<LibraryEntry>
  suspend fun libraryEntry(isbn: String): LibraryEntry?
  suspend fun stats(): DashboardStats

  suspend fun attempt(attemptId: Long): DownloadAttempt?
  suspend fun attemptsFor(isbn: String): List<DownloadAttempt>
  suspend fun recentAttempts(limit: Int): List<DownloadAttempt>
  suspend fun activeAttempts(): List<ActiveAttempt>
  suspend fun attemptPage(status: AttemptStatus?, query: String?, page: Int, pageSize: Int): AttemptPage
  suspend fun attemptCounts(): Map<AttemptStatus, Long>

  /** Queues a download of a book, whether or not it is in the library or already downloaded. */
  suspend fun enqueueDownload(isbn: String, format: BookFormat?): EnqueueOutcome

  /** Queues every book in [isbns] that is not already active. Returns the number queued. */
  suspend fun enqueueDownloads(isbns: List<String>, format: BookFormat?): Int

  /** Queues every library book that has not been downloaded. Returns the number queued. */
  suspend fun downloadMissing(): Int

  suspend fun retryAttempt(attemptId: Long): EnqueueOutcome

  /** Retries every book whose latest attempt failed and which is not downloaded. Returns the number queued. */
  suspend fun retryFailed(): Int
  suspend fun cancelAttempt(attemptId: Long): Boolean

  /** Removes a book from the download history so the next sync downloads it again. */
  suspend fun forgetDownload(isbn: String)
  suspend fun deleteAttempt(attemptId: Long): Boolean
  suspend fun clearAttempts(olderThanDays: Int?): Int

  fun syncSnapshot(): SyncSnapshot
  fun requestSync(overwrite: Boolean): Boolean
  suspend fun wishlist(): WishlistOverview
}
