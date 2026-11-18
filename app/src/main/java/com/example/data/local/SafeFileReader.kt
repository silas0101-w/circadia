package com.example.data.local

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream

object SafeFileReader {
    const val MAX_IMPORT_BYTES = 5 * 1024 * 1024 // 5 MB
    const val MAX_IMPORT_RECORDS = 2000

    fun readUtf8Bounded(
        context: Context,
        uri: Uri,
        maxBytes: Int = MAX_IMPORT_BYTES
    ): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to read the selected file.")
        input.use { stream ->
            val buffer = ByteArray(8192)
            val out = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) {
                    throw IllegalArgumentException(
                        "Selected file exceeds the ${maxBytes / (1024 * 1024)} MB safety limit."
                    )
                }
                out.write(buffer, 0, read)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }

    fun sanitizeText(raw: String, maxLen: Int): String {
        return raw
            .replace(Regex("[\\p{Cntrl}&&[^\t ]]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxLen)
    }
}
