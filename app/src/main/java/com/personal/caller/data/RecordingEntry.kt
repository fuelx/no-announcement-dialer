package com.personal.caller.data

import java.io.File

data class RecordingEntry(
    val file: File,
    val createdAt: Long = file.lastModified(),
    val sizeBytes: Long = file.length()
)
