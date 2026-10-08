package com.vishnurajeevan.libroabs.db.repo

import com.vishnurajeevan.libroabs.db.DownloadAttemptQueries
import com.vishnurajeevan.libroabs.models.graph.Io
import com.vishnurajeevan.libroabs.models.server.AttemptStatus
import com.vishnurajeevan.libroabs.models.server.AttemptTrigger
import com.vishnurajeevan.libroabs.models.server.BookFormat
import com.vishnurajeevan.libroabs.models.server.DownloadAttempt
import com.vishnurajeevan.libroabs.models.server.DownloadedFormat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

data class AttemptPage(
  val attempts: List<DownloadAttempt>,
  val total: Long,
)

/**
 * Persistent log of every download attempt, successful or not.
 */
interface DownloadAttemptRepo {
  suspend fun create(
    isbn: String,
    title: String,
    trigger: AttemptTrigger,
    requestedFormat: BookFormat?,
    now: Long,
  ): Long

  suspend fun markRunning(id: Long, now: Long)

  suspend fun markFinished(
    id: Long,
    status: AttemptStatus,
    error: String?,
    format: DownloadedFormat?,
    now: Long,
  )

  suspend fun get(id: Long): DownloadAttempt?
  suspend fun active(): List<DownloadAttempt>
  suspend fun recent(limit: Int): List<DownloadAttempt>
  suspend fun forIsbn(isbn: String, limit: Int = 20): List<DownloadAttempt>
  suspend fun page(status: AttemptStatus?, query: String?, limit: Int, offset: Int): AttemptPage
  suspend fun statusCounts(): Map<AttemptStatus, Long>

  /** Most recent attempt for each isbn that has ever been tried. */
  suspend fun latestPerIsbn(): Map<String, DownloadAttempt>

  /** Marks leftover queued/running attempts (from a crash or restart) as failed. */
  suspend fun interruptActive(reason: String, now: Long): Int

  suspend fun deleteFinished(): Int
  suspend fun deleteFinishedBefore(queuedBefore: Long): Int
  suspend fun deleteFinished(id: Long): Boolean
}

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RealDownloadAttemptRepo(
  private val queries: DownloadAttemptQueries,
  @Io private val ioDispatcher: CoroutineDispatcher,
) : DownloadAttemptRepo {

  override suspend fun create(
    isbn: String,
    title: String,
    trigger: AttemptTrigger,
    requestedFormat: BookFormat?,
    now: Long,
  ): Long = withContext(ioDispatcher) {
    queries.transactionWithResult {
      queries.insertAttempt(isbn, title, trigger.name, requestedFormat?.name, now)
      queries.lastInsertId().executeAsOne()
    }
  }

  override suspend fun markRunning(id: Long, now: Long): Unit = withContext(ioDispatcher) {
    queries.markRunning(now, id)
  }

  override suspend fun markFinished(
    id: Long,
    status: AttemptStatus,
    error: String?,
    format: DownloadedFormat?,
    now: Long,
  ): Unit = withContext(ioDispatcher) {
    queries.markFinished(status.name, error, format?.name, now, id)
  }

  override suspend fun get(id: Long): DownloadAttempt? = withContext(ioDispatcher) {
    queries.selectById(id, ::map).executeAsOneOrNull()
  }

  override suspend fun active(): List<DownloadAttempt> = withContext(ioDispatcher) {
    queries.selectActive(::map).executeAsList()
  }

  override suspend fun recent(limit: Int): List<DownloadAttempt> = withContext(ioDispatcher) {
    queries.selectRecent(limit.toLong(), ::map).executeAsList()
  }

  override suspend fun forIsbn(isbn: String, limit: Int): List<DownloadAttempt> = withContext(ioDispatcher) {
    queries.selectForIsbn(isbn, limit.toLong(), ::map).executeAsList()
  }

  override suspend fun page(
    status: AttemptStatus?,
    query: String?,
    limit: Int,
    offset: Int,
  ): AttemptPage = withContext(ioDispatcher) {
    val like = query?.trim()?.takeIf { it.isNotEmpty() }?.escapeLike()
    AttemptPage(
      attempts = queries.selectPage(status?.name, like, limit.toLong(), offset.toLong(), ::map).executeAsList(),
      total = queries.countPage(status?.name, like).executeAsOne(),
    )
  }

  override suspend fun statusCounts(): Map<AttemptStatus, Long> = withContext(ioDispatcher) {
    queries.statusCounts().executeAsList().associate { AttemptStatus.valueOf(it.status) to it.count }
  }

  override suspend fun latestPerIsbn(): Map<String, DownloadAttempt> = withContext(ioDispatcher) {
    queries.latestPerIsbn(::map).executeAsList().associateBy { it.isbn }
  }

  override suspend fun interruptActive(reason: String, now: Long): Int = withContext(ioDispatcher) {
    queries.transactionWithResult {
      val count = queries.selectActive(::map).executeAsList().size
      queries.interruptActive(reason, now)
      count
    }
  }

  override suspend fun deleteFinished(): Int = withContext(ioDispatcher) {
    queries.transactionWithResult {
      val before = queries.statusCounts().executeAsList().filter { !AttemptStatus.valueOf(it.status).isActive }.sumOf { it.count }
      queries.deleteFinished()
      before.toInt()
    }
  }

  override suspend fun deleteFinishedBefore(queuedBefore: Long): Int = withContext(ioDispatcher) {
    queries.transactionWithResult {
      queries.deleteFinishedBefore(queuedBefore)
      queries.selectChanges().executeAsOne().toInt()
    }
  }

  override suspend fun deleteFinished(id: Long): Boolean = withContext(ioDispatcher) {
    queries.transactionWithResult {
      queries.deleteFinishedById(id)
      queries.selectChanges().executeAsOne() > 0
    }
  }

  @Suppress("LongParameterList")
  private fun map(
    id: Long,
    isbn: String,
    title: String,
    triggerSource: String,
    status: String,
    error: String?,
    format: String?,
    requestedFormat: String?,
    queuedAt: Long,
    startedAt: Long?,
    finishedAt: Long?,
  ) = DownloadAttempt(
    id = id,
    isbn = isbn,
    title = title,
    trigger = AttemptTrigger.valueOf(triggerSource),
    status = AttemptStatus.valueOf(status),
    error = error,
    format = format?.let { DownloadedFormat.valueOf(it) },
    requestedFormat = requestedFormat?.let { BookFormat.valueOf(it) },
    queuedAt = queuedAt,
    startedAt = startedAt,
    finishedAt = finishedAt,
  )
}

/** Escapes `%`, `_` and `\` for use in a `LIKE ... ESCAPE '\'` clause. */
internal fun String.escapeLike(): String = buildString {
  for (c in this@escapeLike) {
    if (c == '\\' || c == '%' || c == '_') append('\\')
    append(c)
  }
}
