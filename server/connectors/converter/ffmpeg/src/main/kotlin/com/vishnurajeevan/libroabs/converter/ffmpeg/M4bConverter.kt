package com.vishnurajeevan.libroabs.converter.ffmpeg

import com.vishnurajeevan.libroabs.models.libro.Book
import com.vishnurajeevan.libroabs.models.libro.Tracks
import java.io.File

/** Combines downloaded MP3 chapters into a single M4B. */
interface M4bConverter {
  suspend fun convertBookToM4b(book: Book, tracks: List<Tracks>, targetDirectory: File, audioQuality: String)
}
