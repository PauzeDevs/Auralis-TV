package dev.pauze.auralis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileNamesTest {
    @Test
    fun removesLineBreaksAndLimitsProfileNames() {
        assertEquals("Family", normalizedProfileName("  Fam\nily  "))
        assertEquals(24, normalizedProfileName("abcdefghijklmnopqrstuvwxyz").length)
    }

    @Test
    fun profileNamesAreUniqueIgnoringCase() {
        assertFalse(canAddProfile("primary", listOf("Primary")))
        assertTrue(canAddProfile("Kids", listOf("Primary")))
        assertFalse(canAddProfile("  ", listOf("Primary")))
    }
}
