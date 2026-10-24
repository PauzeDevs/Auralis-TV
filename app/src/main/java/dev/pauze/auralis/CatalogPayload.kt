package dev.pauze.auralis

import java.io.IOException
import java.io.Reader

internal fun readBoundedText(reader: Reader, maxChars: Int): String {
    require(maxChars >= 0) { "maxChars must be non-negative" }
    val result = StringBuilder(minOf(maxChars, 8_192))
    val buffer = CharArray(8_192)

    while (true) {
        val remaining = maxChars - result.length
        val requested = if (remaining >= buffer.size) buffer.size else remaining + 1
        val count = reader.read(buffer, 0, requested)
        if (count < 0) return result.toString()
        if (count > remaining) throw IOException("Catalogue response exceeds the size limit")
        result.append(buffer, 0, count)
    }
}
