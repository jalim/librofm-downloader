package com.vishnurajeevan.libroabs.models.server

import kotlinx.serialization.Serializable

@Serializable
enum class SyncTrigger {
  STARTUP, SCHEDULED, MANUAL, API
}

/**
 * Point-in-time view of the library sync, which checks libro.fm, queues downloads and syncs trackers.
 * Timestamps are epoch milliseconds.
 */
@Serializable
data class SyncSnapshot(
  val running: Boolean = false,
  val trigger: SyncTrigger? = null,
  val overwrite: Boolean = false,
  val startedAt: Long? = null,
  val lastFinishedAt: Long? = null,
  val lastDurationMs: Long? = null,
  val lastTrigger: SyncTrigger? = null,
  val lastError: String? = null,
  val activeCount: Int = 0,
)
