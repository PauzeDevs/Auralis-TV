package dev.pauze.auralis

import java.io.IOException
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogPayloadTest {
    @Test
    fun acceptsResponsesAtTheConfiguredLimit() {
        assertEquals("abc", readBoundedText(StringReader("abc"), 3))
    }

    @Test(expected = IOException::class)
    fun rejectsResponsesLargerThanTheConfiguredLimit() {
        readBoundedText(StringReader("abcd"), 3)
    }

    @Test
    fun preservesUtf16TextWithinLimit() {
        assertEquals("TV 📺", readBoundedText(StringReader("TV 📺"), 5))
    }
}
