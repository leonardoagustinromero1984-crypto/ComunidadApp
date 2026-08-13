package com.comunidapp.shared.media

import com.comunidapp.shared.poc.m08.model.FileRef

/**
 * Contenido de archivo neutral — sin Uri/NSData en commonMain.
 */
data class FileContent(
    val bytes: ByteArray,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileContent) return false
        return name == other.name && mimeType == other.mimeType &&
            sizeBytes == other.sizeBytes && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

interface FileContentReader {
    suspend fun read(fileRef: FileRef): Result<FileContent>
}

expect fun createFileContentReader(): FileContentReader
