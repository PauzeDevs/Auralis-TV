package dev.pauze.auralis

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal data class StreamingProvider(
    val id: String,
    val displayName: String,
    val packageNames: Set<String>,
    private val searchUrlPrefix: String
) {
    fun searchUrlFor(title: String): String =
        searchUrlPrefix + URLEncoder.encode(title, StandardCharsets.UTF_8.name())
}

internal val SupportedStreamingProviders: List<StreamingProvider> = listOf(
    StreamingProvider("netflix", "Netflix", setOf("com.netflix.ninja", "com.netflix.mediaclient"), "https://www.netflix.com/search?q="),
    StreamingProvider("prime-video", "Prime Video", setOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"), "https://www.primevideo.com/search/ref=atv_nb_sr?phrase="),
    StreamingProvider("crunchyroll", "Crunchyroll", setOf("com.crunchyroll.crunchyroid"), "https://www.crunchyroll.com/search?q="),
    StreamingProvider("youtube", "YouTube", setOf("com.google.android.youtube.tv", "com.google.android.youtube"), "https://www.youtube.com/results?search_query="),
    StreamingProvider("disney-plus", "Disney+", setOf("com.disney.disneyplus", "com.disney.disneyplus.tv"), "https://www.disneyplus.com/search?q="),
    StreamingProvider("max", "Max", setOf("com.wbd.stream", "com.hbo.hbonow"), "https://www.max.com/search?q="),
    StreamingProvider("hulu", "Hulu", setOf("com.hulu.livingroomplus", "com.hulu.plus"), "https://www.hulu.com/search?q="),
    StreamingProvider("peacock", "Peacock", setOf("com.peacocktv.peacockandroid"), "https://www.peacocktv.com/search?q="),
    StreamingProvider("paramount-plus", "Paramount+", setOf("com.cbs.ott"), "https://www.paramountplus.com/search/?query="),
    StreamingProvider("jiohotstar", "JioHotstar", setOf("in.startv.hotstar", "com.hotstar.android", "in.hotstar.android"), "https://www.hotstar.com/in/search?q="),
    StreamingProvider("sony-liv", "Sony LIV", setOf("com.sonyliv"), "https://www.sonyliv.com/search?query="),
    StreamingProvider("zee5", "ZEE5", setOf("com.graymatrix.did"), "https://www.zee5.com/search?q="),
    StreamingProvider("plex", "Plex", setOf("com.plexapp.android"), "https://app.plex.tv/desktop/#!/search?query="),
    StreamingProvider("tubi", "Tubi", setOf("com.tubitv"), "https://tubitv.com/search?q="),
    StreamingProvider("apple-tv", "Apple TV", setOf("com.apple.atve.androidtv.appletv"), "https://tv.apple.com/search?term=")
)

internal fun streamingProviderForPackage(packageName: String): StreamingProvider? =
    SupportedStreamingProviders.firstOrNull { packageName in it.packageNames }

internal fun installedStreamingProviders(packageNames: Iterable<String>): List<StreamingProvider> =
    packageNames.mapNotNull(::streamingProviderForPackage).distinctBy { it.id }
