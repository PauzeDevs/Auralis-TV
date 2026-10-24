package dev.pauze.auralis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class StreamingServiceTest {
    @Test
    fun detectsCommonTvAndMobilePackageVariants() {
        assertEquals("Netflix", streamingProviderForPackage("com.netflix.ninja")?.displayName)
        assertEquals("Prime Video", streamingProviderForPackage("com.amazon.amazonvideo.livingroom")?.displayName)
        assertEquals("Crunchyroll", streamingProviderForPackage("com.crunchyroll.crunchyroid")?.displayName)
        assertEquals("YouTube", streamingProviderForPackage("com.google.android.youtube.tv")?.displayName)
        assertEquals("YouTube", streamingProviderForPackage("com.google.android.youtube")?.displayName)
    }

    @Test
    fun unknownPackagesAreNotMisidentifiedAsStreamingServices() {
        assertNull(streamingProviderForPackage("com.example.somevideoapp"))
    }

    @Test
    fun installedProviderListIsUniqueAndKeepsInstallationOrder() {
        val providers = installedStreamingProviders(
            listOf(
                "com.google.android.youtube.tv",
                "com.netflix.ninja",
                "com.google.android.youtube",
                "com.crunchyroll.crunchyroid"
            )
        )
        assertEquals(listOf("youtube", "netflix", "crunchyroll"), providers.map { it.id })
    }

    @Test
    fun titleSearchQueryIsUrlEncoded() {
        val netflix = streamingProviderForPackage("com.netflix.ninja")
        assertNotNull(netflix)
        assertEquals(
            "https://www.netflix.com/search?q=Avatar+The+Last+Airbender",
            netflix!!.searchUrlFor("Avatar The Last Airbender")
        )
    }
}
