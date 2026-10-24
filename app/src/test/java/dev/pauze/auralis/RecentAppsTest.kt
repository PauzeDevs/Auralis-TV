package dev.pauze.auralis

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentAppsTest {
    @Test
    fun launchingAnAppMovesItToTheFrontAndRemovesDuplicates() {
        val current = listOf("music", "video", "music", "browser")

        assertEquals(
            listOf("video", "music", "browser"),
            updatedRecentPackages(current, "video")
        )
    }

    @Test
    fun recentHistoryIsCappedAtTwelveEntries() {
        val current = (1..15).map { "app$it" }

        assertEquals(
            (listOf("new-app") + current).take(12),
            updatedRecentPackages(current, "new-app")
        )
    }

    @Test
    fun blankStoredEntriesAreDiscarded() {
        assertEquals(
            listOf("new-app", "music"),
            updatedRecentPackages(listOf("", "music", "  "), "new-app")
        )
    }
}
