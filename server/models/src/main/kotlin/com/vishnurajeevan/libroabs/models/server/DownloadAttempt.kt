package com.vishnurajeevan.libroabs.models.server

import kotlinx.serialization.Serializable

@Serializable
enum class AttemptStatus {
  QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED;

  val isActive: Boolean get() = this == QUEUED || this == RUNNING
}

/** What caused an attempt to be created. */
@Serializable
enum class AttemptTrigger {
  SCHEDULED, MANUAL, RETRY
}

/**
 * A single try at downloading a book. Timestamps are epoch milliseconds.
 */
@Serializable
data class DownloadAttempt(
  val id: Long,
  val isbn: String,
  val title: String,
  val trigger: AttemptTrigger,
  val status: AttemptStatus,
  val error: String?,
  /** Format that was actually produced, once known. */
  val format: DownloadedFormat?,
  /** Format override requested for this attempt, `null` means the server default. */
  val requestedFormat: BookFormat?,
  val queuedAt: Long,
  val startedAt: Long?,
  val finishedAt: Long?,
) {
  val durationMs: Long?
    get() = if (startedAt != null && finishedAt != null) finishedAt - startedAt else null
}
