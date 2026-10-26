package dev.pauze.auralis

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class TvApp(
    val label: String,
    val packageName: String,
    val launchActivityName: String,
    val icon: Bitmap
)

private data class InstalledStreamingApp(
    val provider: StreamingProvider,
    val app: TvApp
)

private enum class LauncherDrawer {
    APPS,
    RECOMMENDATIONS,
    CONTROL_CENTER
}

private data class FeaturedMovie(
    val id: String,
    val title: String,
    val year: String,
    val description: String,
    val genres: String,
    val rating: String?,
    val posterUrl: String,
    val backgroundUrl: String,
    val trailerUrl: String?
) {
    val imdbUrl: String
        get() = "https://www.imdb.com/title/$id/"

    val actionUrl: String
        get() = trailerUrl ?: imdbUrl

    val metadataLine: String
        get() = listOfNotNull(
            year.takeIf { it.isNotBlank() },
            genres.takeIf { it.isNotBlank() },
            rating?.let { "IMDb $it" }
        ).joinToString("  •  ")
}

private val Accent = Color(0xFF63B8FF)
private val Ink = Color(0xFF050506)

internal fun updatedRecentPackages(current: List<String>, packageName: String): List<String> =
    (listOf(packageName) + current.filterNot { it == packageName })
        .filter { it.isNotBlank() }
        .distinct()
        .take(12)

private suspend fun requestInitialFocus(requestFocus: () -> Unit) {
    repeat(5) {
        withFrameNanos { }
        try {
            requestFocus()
            return
        } catch (_: IllegalStateException) {
            // Wait for the next frame if Compose has not attached the focus target yet.
        }
    }
}

class MainActivity : ComponentActivity() {
    private var installedApps by mutableStateOf<List<TvApp>>(emptyList())
    private var appsLoading by mutableStateOf(true)
    private var appsLoadJob: Job? = null
    private var appLoadGeneration = 0
    private var featuredMovies by mutableStateOf<List<FeaturedMovie>>(emptyList())
    private var moviesLoading by mutableStateOf(true)
    private var moviesLoadJob: Job? = null
    private var movieLoadGeneration = 0
    private var hasResumedOnce = false
    private val interactionRevision = mutableStateOf(0)
    private var recognizedSearchQuery by mutableStateOf<String?>(null)
    private lateinit var preferences: android.content.SharedPreferences
    private val vpnConsentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val serviceIntent = Intent(this, AdBlockVpnService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
            Toast.makeText(this, "Auralis DNS protection enabled", Toast.LENGTH_SHORT).show()
        }
    }
    private val voiceSearchLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val phrase = if (result.resultCode == RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        } else {
            ""
        }
        if (phrase.isNotBlank()) recognizedSearchQuery = phrase
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = getSharedPreferences("auralis_preferences", MODE_PRIVATE)
        configureHighRefreshRate()

        setContent {
            MaterialTheme {
                var activeProfile by remember { mutableStateOf(readActiveProfile()) }
                var recentPackages by remember(activeProfile) {
                    mutableStateOf(readRecentPackages(activeProfile))
                }
                var watchlist by remember(activeProfile) { mutableStateOf(readWatchlist(activeProfile)) }
                var savedWatchlistMovies by remember(activeProfile) { mutableStateOf(readStoredWatchlist(activeProfile)) }
                var profiles by remember { mutableStateOf(readProfiles()) }
                val recentApps = remember(installedApps, recentPackages) {
                    val byPackage = installedApps.associateBy { it.packageName }
                    recentPackages.mapNotNull { byPackage[it] }
                }

                AuralisHome(
                    apps = installedApps,
                    appsLoading = appsLoading,
                    recentApps = recentApps,
                    movies = featuredMovies,
                    moviesLoading = moviesLoading,
                    watchlist = watchlist,
                    savedWatchlistMovies = savedWatchlistMovies,
                    activeProfile = activeProfile,
                    profiles = profiles,
                    voiceQuery = recognizedSearchQuery,
                    interactionRevision = interactionRevision,
                    onVoiceQueryConsumed = { recognizedSearchQuery = null },
                    onLaunch = { app ->
                        if (launchApp(app)) {
                            recentPackages = updatedRecentPackages(recentPackages, app.packageName)
                            preferences.edit()
                                .putString(recentPackagesKey(activeProfile), recentPackages.joinToString("\n"))
                                .apply()
                        }
                    },
                    onOpenSettings = { openSystemSettings() },
                    onEnableAdBlock = { requestAdBlockVpn() },
                    onSetAsHome = { requestDefaultLauncher() },
                    onOpenMovie = { movie -> openMovie(movie) },
                    onSearchOnProvider = { movie, provider -> openMovieOnProvider(movie, provider) },
                    onSearchPhraseOnProvider = { query, provider -> openSearchOnProvider(query, provider) },
                    onToggleWatchlist = { movie ->
                        watchlist = if (movie.id in watchlist) watchlist - movie.id else listOf(movie.id) + watchlist
                        savedWatchlistMovies = if (movie.id in savedWatchlistMovies.map { it.id }) {
                            savedWatchlistMovies.filterNot { it.id == movie.id }
                        } else {
                            (listOf(movie) + savedWatchlistMovies).distinctBy { it.id }
                        }
                        preferences.edit()
                            .putString(watchlistKey(activeProfile), watchlist.joinToString("\n"))
                            .putString(watchlistDataKey(activeProfile), encodeWatchlist(savedWatchlistMovies))
                            .apply()
                    },
                    onSelectProfile = { name ->
                        activeProfile = name
                        preferences.edit().putString("active_profile", name).apply()
                        recentPackages = readRecentPackages(name)
                        watchlist = readWatchlist(name)
                        savedWatchlistMovies = readStoredWatchlist(name)
                    },
                    onAddProfile = { name ->
                        val clean = normalizedProfileName(name)
                        if (canAddProfile(clean, profiles)) {
                            profiles = profiles + clean
                            preferences.edit().putString("profiles", profiles.filterNot { it == "Primary" }.joinToString("\n")).apply()
                        }
                    },
                    onVoiceSearch = { startVoiceSearch() },
                    onRefreshApps = {
                        refreshApps()
                        refreshFeaturedMovies()
                    }
                )
            }
        }

        refreshApps()
        refreshFeaturedMovies()
    }

    private fun configureHighRefreshRate() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return

        val displayMode = windowManager.defaultDisplay.mode
        val supportedModes = windowManager.defaultDisplay.supportedModes
        val preferredMode = supportedModes
            .filter { it.physicalHeight == displayMode.physicalHeight && it.physicalWidth == displayMode.physicalWidth }
            .maxByOrNull { it.refreshRate }

        if (preferredMode != null && preferredMode.refreshRate >= 90f) {
            val attributes = window.attributes
            attributes.preferredDisplayModeId = preferredMode.modeId
            window.attributes = attributes
        }
    }

    override fun onResume() {
        super.onResume()
        interactionRevision.value++
        if (hasResumedOnce) {
            refreshApps()
        }
        hasResumedOnce = true
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        interactionRevision.value++
    }

    private fun refreshApps() {
        val generation = ++appLoadGeneration
        appsLoadJob?.cancel()
        appsLoading = true
        appsLoadJob = lifecycleScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) { loadApps() }
                if (generation == appLoadGeneration) {
                    installedApps = apps
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                if (generation == appLoadGeneration) {
                    installedApps = emptyList()
                    Toast.makeText(
                        this@MainActivity,
                        "Unable to load installed apps.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                if (generation == appLoadGeneration) {
                    appsLoading = false
                }
            }
        }
    }

    private fun refreshFeaturedMovies() {
        val generation = ++movieLoadGeneration
        moviesLoadJob?.cancel()
        moviesLoading = true
        moviesLoadJob = lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { loadFeaturedMovies() }
                if (generation == movieLoadGeneration) {
                    featuredMovies = result
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == movieLoadGeneration) {
                    featuredMovies = emptyList()
                }
            } finally {
                if (generation == movieLoadGeneration) {
                    moviesLoading = false
                }
            }
        }
    }

    private fun loadFeaturedMovies(): List<FeaturedMovie> {
        val cachedPayload = runCatching {
            preferences.getString(FEATURED_MOVIES_CACHE_KEY, null)
                ?.takeIf { it.length <= MAX_CATALOG_CHARS }
        }.getOrNull()
        var connection: HttpURLConnection? = null

        return try {
            connection = URL(FEATURED_MOVIES_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = MOVIE_CATALOG_TIMEOUT_MS
            connection.readTimeout = MOVIE_CATALOG_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Auralis-TV/0.2")
            connection.useCaches = true

            if (connection.contentLength > MAX_CATALOG_CHARS) {
                throw java.io.IOException("Catalogue response exceeds the size limit")
            }
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                readBoundedText(it, MAX_CATALOG_CHARS)
            }
            val result = parseFeaturedMovies(payload)
            if (result.isNotEmpty()) {
                preferences.edit().putString(FEATURED_MOVIES_CACHE_KEY, payload).apply()
                result
            } else {
                cachedPayload?.let(::parseFeaturedMovies).orEmpty()
            }
        } catch (_: Exception) {
            cachedPayload?.let(::parseFeaturedMovies).orEmpty()
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseFeaturedMovies(payload: String): List<FeaturedMovie> {
        val metas = runCatching { JSONObject(payload).optJSONArray("metas") }.getOrNull()
            ?: return emptyList()
        val movies = ArrayList<FeaturedMovie>()

        for (index in 0 until metas.length()) {
            if (movies.size >= MAX_FEATURED_MOVIES) break
            val item = metas.optJSONObject(index) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() && it != "null" }
                ?: item.optString("imdb_id")
            if (!id.startsWith("tt") || id.length < 4 || !id.drop(2).all(Char::isDigit)) continue

            val title = item.optString("name").trim()
            if (title.isBlank() || title == "null") continue

            val poster = safeArtworkUrl(item.optString("poster"))
            val background = safeArtworkUrl(item.optString("background")) ?: poster
            if (poster == null && background == null) continue

            val genreArray = item.optJSONArray("genres") ?: item.optJSONArray("genre")
            val genres = buildList {
                if (genreArray != null) {
                    for (genreIndex in 0 until genreArray.length()) {
                        val genre = genreArray.optString(genreIndex).trim()
                        if (genre.isNotBlank() && genre != "null") add(genre)
                        if (size == 3) break
                    }
                }
            }.joinToString("  •  ")

            val ratingText = item.optString("imdbRating").trim()
            val rating = ratingText.takeIf {
                val number = it.toDoubleOrNull()
                number != null && number in 0.0..10.0
            }
            val rawYear = item.optString("year").ifBlank { item.optString("releaseInfo") }.trim()
            val year = rawYear.takeIf { it.length == 4 && it.all(Char::isDigit) }.orEmpty()
            val trailerArray = item.optJSONArray("trailerStreams") ?: item.optJSONArray("trailers")
            val trailerId = trailerArray?.let { array ->
                (0 until array.length()).asSequence()
                    .mapNotNull { trailerIndex ->
                        val entry = array.optJSONObject(trailerIndex) ?: return@mapNotNull null
                        entry.optString("ytId").ifBlank { entry.optString("source") }
                    }
                    .firstOrNull { it.matches(Regex("[A-Za-z0-9_-]{6,20}")) }
            }
            val description = item.optString("description").trim().takeUnless {
                it.isBlank() || it == "null"
            }.orEmpty()

            movies += FeaturedMovie(
                id = id,
                title = title,
                year = year,
                description = description,
                genres = genres,
                rating = rating,
                posterUrl = poster ?: background.orEmpty(),
                backgroundUrl = background ?: poster.orEmpty(),
                trailerUrl = trailerId?.let { "https://www.youtube.com/watch?v=$it" }
            )
        }
        return movies
    }

    private fun safeArtworkUrl(rawUrl: String): String? {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return null
        return rawUrl.takeIf {
            uri.scheme == "https" && uri.host == "images.metahub.space"
        }
    }

    private fun openMovie(movie: FeaturedMovie) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(movie.actionUrl)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "No app is available to open this film's trailer or details.",
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: SecurityException) {
            Toast.makeText(
                this,
                "Auralis TV couldn't open this film right now.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    Toast.makeText(this, "Auralis TV is already the default Home app.", Toast.LENGTH_SHORT).show()
                    return
                }
                try {
                    startActivityForResult(
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME),
                        REQUEST_HOME_ROLE
                    )
                    return
                } catch (_: ActivityNotFoundException) {
                    // Customized TV firmware may expose the role without its prompt.
                } catch (_: SecurityException) {
                    // Fall back to the device's Home-app settings below.
                }
            }
        }

        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Open Settings and look for Default apps or Home app to select Auralis TV.",
                Toast.LENGTH_LONG
            ).show()
        } catch (_: SecurityException) {
            Toast.makeText(
                this,
                "This device does not allow changing the default launcher from Auralis TV.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openMovieOnProvider(movie: FeaturedMovie, provider: StreamingProvider) {
        openSearchOnProvider(movie.title, provider)
    }

    private fun openSearchOnProvider(query: String, provider: StreamingProvider) {
        val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(provider.searchUrlFor(query)))
        try {
            startActivity(searchIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "No app or browser is available to search ${provider.displayName}.",
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: SecurityException) {
            Toast.makeText(
                this,
                "Couldn't open ${provider.displayName} for search.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun requestAdBlockVpn() {
        val consent = android.net.VpnService.prepare(this)
        if (consent != null) vpnConsentLauncher.launch(consent) else {
            val serviceIntent = Intent(this, AdBlockVpnService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
        }
    }

    private fun openSystemSettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        } catch (exception: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "System settings aren't available on this device.",
                Toast.LENGTH_SHORT
            ).show()
        } catch (exception: SecurityException) {
            Toast.makeText(
                this,
                "System settings can't be opened from Auralis TV.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun launchApp(app: TvApp): Boolean {
        return try {
            val launchIntent = Intent(Intent.ACTION_MAIN).setClassName(
                app.packageName,
                app.launchActivityName
            )
            startActivity(launchIntent)
            true
        } catch (exception: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Couldn't open ${app.label}. Refreshing apps.",
                Toast.LENGTH_SHORT
            ).show()
            refreshApps()
            false
        } catch (exception: SecurityException) {
            Toast.makeText(
                this,
                "Permission denied while opening ${app.label}.",
                Toast.LENGTH_SHORT
            ).show()
            false
        }
    }

    private fun readActiveProfile(): String {
        val savedProfile = normalizedProfileName(preferences.getString("active_profile", "Primary").orEmpty())
        return readProfiles().firstOrNull { it.equals(savedProfile, ignoreCase = true) } ?: "Primary"
    }

    private fun readRecentPackages(profile: String): List<String> =
        preferences.getString(recentPackagesKey(profile), if (profile == "Primary") preferences.getString(RECENT_PACKAGES_KEY, "") else "")
            .orEmpty()
            .split("\n")
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)

    private fun readWatchlist(profile: String): List<String> =
        preferences.getString(watchlistKey(profile), "").orEmpty().split("\n").filter(String::isNotBlank).distinct()

    private fun readStoredWatchlist(profile: String): List<FeaturedMovie> =
        decodeWatchlist(preferences.getString(watchlistDataKey(profile), "").orEmpty())

    private fun encodeWatchlist(movies: List<FeaturedMovie>): String = JSONArray().apply {
        movies.distinctBy { it.id }.forEach { movie ->
            put(JSONObject().apply {
                put("id", movie.id); put("title", movie.title); put("year", movie.year)
                put("description", movie.description); put("genres", movie.genres)
                put("rating", movie.rating.orEmpty()); put("poster", movie.posterUrl)
                put("background", movie.backgroundUrl); put("trailer", movie.trailerUrl.orEmpty())
            })
        }
    }.toString()

    private fun decodeWatchlist(payload: String): List<FeaturedMovie> {
        val entries = runCatching { JSONArray(payload) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val id = entry.optString("id")
                val title = entry.optString("title").trim()
                val poster = safeArtworkUrl(entry.optString("poster")) ?: continue
                val background = safeArtworkUrl(entry.optString("background")) ?: poster
                if (!id.matches(Regex("tt[0-9]{2,}")) || title.isBlank()) continue
                val trailer = entry.optString("trailer").takeIf { raw ->
                    val uri = runCatching { Uri.parse(raw) }.getOrNull()
                    uri != null && uri.scheme == "https" && uri.host in setOf("youtube.com", "www.youtube.com", "youtu.be")
                }
                add(FeaturedMovie(
                    id = id, title = title, year = entry.optString("year"),
                    description = entry.optString("description"), genres = entry.optString("genres"),
                    rating = entry.optString("rating").takeIf(String::isNotBlank),
                    posterUrl = poster, backgroundUrl = background, trailerUrl = trailer
                ))
            }
        }.distinctBy { it.id }
    }

    private fun readProfiles(): List<String> =
        (listOf("Primary") + preferences.getString("profiles", "").orEmpty().split("\n")
            .map(::normalizedProfileName).filter(String::isNotBlank))
            .distinctBy { it.lowercase(Locale.ROOT) }

    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search for a film or show")
        try { voiceSearchLauncher.launch(intent) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, "Voice search isn't available on this device.", Toast.LENGTH_SHORT).show() }
        catch (_: SecurityException) { Toast.makeText(this, "This device doesn't allow Auralis TV to start voice search.", Toast.LENGTH_SHORT).show() }
    }

    private fun loadApps(): List<TvApp> {
        val launcherCategories = listOf(
            Intent.CATEGORY_LEANBACK_LAUNCHER,
            Intent.CATEGORY_LAUNCHER
        )

        return launcherCategories
            .flatMap { category ->
                val query = Intent(Intent.ACTION_MAIN).addCategory(category)
                packageManager.queryIntentActivities(query, 0)
                    .mapNotNull { resolvedActivity ->
                        val activityInfo = resolvedActivity.activityInfo
                            ?: return@mapNotNull null
                        if (activityInfo.packageName == packageName) {
                            null
                        } else {
                            runCatching {
                                val drawable = resolvedActivity.loadIcon(packageManager)
                                val bitmap = Bitmap.createBitmap(
                                    ICON_BITMAP_SIZE,
                                    ICON_BITMAP_SIZE,
                                    Bitmap.Config.ARGB_8888
                                )
                                drawable.setBounds(
                                    8,
                                    8,
                                    ICON_BITMAP_SIZE - 8,
                                    ICON_BITMAP_SIZE - 8
                                )
                                drawable.draw(Canvas(bitmap))
                                TvApp(
                                    label = resolvedActivity.loadLabel(packageManager).toString(),
                                    packageName = activityInfo.packageName,
                                    launchActivityName = activityInfo.name,
                                    icon = bitmap
                                )
                            }.getOrNull()
                        }
                    }
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    companion object {
        private const val ICON_BITMAP_SIZE = 128
        private const val RECENT_PACKAGES_KEY = "recent_packages"
        private fun recentPackagesKey(profile: String) = "recent_packages_${profile.lowercase(Locale.ROOT)}"
        private fun watchlistKey(profile: String) = "watchlist_${profile.lowercase(Locale.ROOT)}"
        private fun watchlistDataKey(profile: String) = "watchlist_data_${profile.lowercase(Locale.ROOT)}"
        private const val FEATURED_MOVIES_CACHE_KEY = "featured_movies_catalog"
        private const val FEATURED_MOVIES_URL =
            "https://cinemeta-catalogs.strem.io/top/catalog/movie/top.json"
        private const val MOVIE_CATALOG_TIMEOUT_MS = 8_000
        private const val MAX_FEATURED_MOVIES = 20
        private const val MAX_CATALOG_CHARS = 1_048_576
        private const val REQUEST_HOME_ROLE = 601
    }
}

@Composable
private fun AuralisHome(
    apps: List<TvApp>,
    appsLoading: Boolean,
    recentApps: List<TvApp>,
    movies: List<FeaturedMovie>,
    moviesLoading: Boolean,
    watchlist: List<String>,
    savedWatchlistMovies: List<FeaturedMovie>,
    activeProfile: String,
    profiles: List<String>,
    voiceQuery: String?,
    interactionRevision: State<Int>,
    onVoiceQueryConsumed: () -> Unit,
    onLaunch: (TvApp) -> Unit,
    onOpenSettings: () -> Unit,
    onEnableAdBlock: () -> Unit,
    onSetAsHome: () -> Unit,
    onOpenMovie: (FeaturedMovie) -> Unit,
    onSearchOnProvider: (FeaturedMovie, StreamingProvider) -> Unit,
    onSearchPhraseOnProvider: (String, StreamingProvider) -> Unit,
    onToggleWatchlist: (FeaturedMovie) -> Unit,
    onSelectProfile: (String) -> Unit,
    onAddProfile: (String) -> Unit,
    onVoiceSearch: () -> Unit,
    onRefreshApps: () -> Unit
) {
    var activeDrawer by remember { mutableStateOf<LauncherDrawer?>(null) }
    var detailMovie by remember { mutableStateOf<FeaturedMovie?>(null) }
    var ambient by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    val drawerOpen = activeDrawer != null
    val streamingApps = remember(apps) {
        apps.mapNotNull { app ->
            streamingProviderForPackage(app.packageName)?.let { provider ->
                InstalledStreamingApp(provider, app)
            }
        }.distinctBy { it.provider.id }
    }
    var selectedMovieId by remember(movies.firstOrNull()?.id) {
        mutableStateOf(movies.firstOrNull()?.id)
    }
    LaunchedEffect(movies) {
        if (movies.none { it.id == selectedMovieId }) {
            selectedMovieId = movies.firstOrNull()?.id
        }
    }
    val selectedMovie = movies.firstOrNull { it.id == selectedMovieId } ?: movies.firstOrNull()
    BackHandler(enabled = drawerOpen) { activeDrawer = null }
    BackHandler(enabled = detailMovie != null) { detailMovie = null }
    BackHandler(enabled = ambient) { ambient = false }
    BackHandler(enabled = searchOpen) {
        searchOpen = false
        searchText = ""
        onVoiceQueryConsumed()
    }
    IdleScreensaverTimer(interactionRevision, ambient) { ambient = true }
    LaunchedEffect(voiceQuery) {
        if (!voiceQuery.isNullOrBlank()) {
            searchText = voiceQuery
            searchOpen = true
        }
    }

    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalDateTime.now()
            delay(30_000)
        }
    }

    val timeLabel = currentTime.format(
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    )
    val dateLabel = currentTime.format(
        DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    )
    val homeScrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (ambient) {
                if (event.type == KeyEventType.KeyDown) ambient = false
                true
            } else {
                false
            }
        }
    ) {
        // Layout responds to usable dp space (resolution + density), not the TV's inch size.
        val compact = maxHeight < 620.dp || maxWidth < 900.dp
        val shouldScroll = maxHeight < 980.dp
        val horizontalPadding = when {
            maxWidth < 700.dp -> 18.dp
            maxWidth < 1000.dp -> 30.dp
            maxWidth < 1400.dp -> 44.dp
            else -> 64.dp
        }
        val verticalPadding = if (compact) 14.dp else if (maxHeight < 800.dp) 22.dp else 30.dp
        val heroHeight = when {
            maxHeight < 540.dp -> 142.dp
            compact -> 178.dp
            maxHeight < 800.dp -> 218.dp
            else -> 250.dp
        }
        val tileWidth = when {
            maxWidth < 700.dp -> 132.dp
            compact -> 156.dp
            maxWidth > 1600.dp -> 224.dp
            else -> 208.dp
        }
        val featuredApp = recentApps.firstOrNull() ?: apps.firstOrNull()
        val featuredIsRecent = recentApps.isNotEmpty()

        Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = if (drawerOpen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        RenderEffect.createBlurEffect(18f, 18f, Shader.TileMode.CLAMP).asComposeRenderEffect()
                    } else {
                        null
                    }
                }
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF20242B), Color(0xFF101114), Ink)
                    )
                )
                .padding(
                    horizontal = horizontalPadding,
                    vertical = verticalPadding
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (shouldScroll) Modifier.verticalScroll(homeScrollState) else Modifier)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "auralis",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "TV",
                        color = Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.weight(1f))
                    FocusButton(
                        label = activeProfile.take(10).uppercase(Locale.getDefault()),
                        initialFocus = false,
                        onClick = { activeDrawer = LauncherDrawer.CONTROL_CENTER }
                    )
                    Spacer(Modifier.width(8.dp))
                    FocusButton("Search", false, onClick = { searchOpen = true })
                    Spacer(Modifier.width(14.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            timeLabel,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            dateLabel.uppercase(Locale.getDefault()),
                            color = Color(0xFF8996A9),
                            fontSize = 10.sp,
                            letterSpacing = 1.3.sp
                        )
                    }
                }

                Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
                TopShelf(
                    app = featuredApp,
                    movie = selectedMovie,
                    movies = movies,
                    moviesLoading = moviesLoading,
                    recentlyOpened = featuredIsRecent,
                    height = heroHeight,
                    compact = compact,
                    initialFocus = featuredApp != null || selectedMovie != null,
                    onLaunch = onLaunch,
                    onOpenMovie = { detailMovie = it }
                )

                val watchlistMovies = watchlist.mapNotNull { id ->
                    movies.firstOrNull { it.id == id } ?: savedWatchlistMovies.firstOrNull { it.id == id }
                }
                Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
                SectionHeading("My List", "Saved on this device · ${watchlistMovies.size} titles")
                Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
                if (watchlistMovies.isEmpty()) {
                    Text(
                        if (moviesLoading) "Loading titles…" else "Save a film from its details page and it will appear here.",
                        color = Color(0xFFADB7C6),
                        fontSize = 13.sp
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(watchlistMovies, key = { _, movie -> movie.id }) { index, movie ->
                            MovieTile(movie, compact, index == 0, onFocused = { selectedMovieId = it.id }, onClick = { detailMovie = movie })
                        }
                    }
                }

                if (recentApps.isNotEmpty()) {
                    Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
                    SectionHeading(
                        title = "Recently opened",
                        detail = "Quick launch"
                    )
                    Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(recentApps, key = { _, app -> app.packageName }) { index, app ->
                            AppTile(
                                app = app,
                                width = tileWidth,
                                compact = compact,
                                initialFocus = featuredApp == null && index == 0,
                                onClick = { onLaunch(app) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
                SectionHeading(
                    title = "Your apps",
                    detail = if (appsLoading) "Loading…" else "${apps.size} available"
                )
                Spacer(Modifier.height(if (compact) 6.dp else 10.dp))

                if (apps.isEmpty()) {
                    Text(
                        if (appsLoading) "Finding your apps…" else "No launchable apps found. Try restarting Auralis TV.",
                        color = Color(0xFFADB7C6),
                        fontSize = 15.sp
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                            AppTile(
                                app = app,
                                width = tileWidth,
                                compact = compact,
                                initialFocus = featuredApp == null && index == 0,
                                onClick = { onLaunch(app) }
                            )
                        }
                    }
                }

                if (shouldScroll) {
                    Spacer(Modifier.height(12.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!compact) {
                        Text(
                            "Auralis TV",
                            color = Color(0xFF788597),
                            fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    FocusButton(
                        label = "Apps",
                        initialFocus = false,
                        onClick = { activeDrawer = LauncherDrawer.APPS }
                    )
                    Spacer(Modifier.width(if (compact) 5.dp else 10.dp))
                    FocusButton(
                        label = "For You",
                        initialFocus = false,
                        onClick = { activeDrawer = LauncherDrawer.RECOMMENDATIONS }
                    )
                    Spacer(Modifier.width(if (compact) 5.dp else 10.dp))
                    FocusButton(label = "Control", initialFocus = !appsLoading && apps.isEmpty(), onClick = { activeDrawer = LauncherDrawer.CONTROL_CENTER })
                    Spacer(Modifier.width(if (compact) 5.dp else 10.dp))
                }
            }
        }
        AnimatedVisibility(
            visible = activeDrawer != null,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.975f, animationSpec = tween(220)),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.985f, animationSpec = tween(140))
        ) {
            when (activeDrawer) {
                LauncherDrawer.APPS -> AppDrawer(
                    apps = apps,
                    appsLoading = appsLoading,
                    compact = compact,
                    onClose = { activeDrawer = null },
                    onLaunch = { app ->
                        onLaunch(app)
                        activeDrawer = null
                    }
                )
                LauncherDrawer.RECOMMENDATIONS -> RecommendationsDrawer(
                    appsLoading = appsLoading,
                    streamingApps = streamingApps,
                    movies = movies,
                    moviesLoading = moviesLoading,
                    compact = compact,
                    selectedMovieId = selectedMovieId,
                    onSelectMovie = { selectedMovieId = it.id },
                    onClose = { activeDrawer = null },
                    onLaunch = { app ->
                        onLaunch(app)
                        activeDrawer = null
                    },
                    onOpenMovie = { detailMovie = it },
                    onSearchOnProvider = onSearchOnProvider
                )
                LauncherDrawer.CONTROL_CENTER -> ControlCenter(
                    compact = compact,
                    activeProfile = activeProfile,
                    profiles = profiles,
                    profileName = profileName,
                    onProfileName = { profileName = it },
                    onSelectProfile = { onSelectProfile(it); activeDrawer = null },
                    onAddProfile = { onAddProfile(profileName); profileName = "" },
                    onVoiceSearch = { searchOpen = true; onVoiceSearch(); activeDrawer = null },
                    onSettings = { onOpenSettings(); activeDrawer = null },
                    onAdBlock = { onEnableAdBlock(); activeDrawer = null },
                    onRefresh = { onRefreshApps(); activeDrawer = null },
                    onSetAsHome = { onSetAsHome(); activeDrawer = null },
                    onScreensaver = { activeDrawer = null; ambient = true },
                    onClose = { activeDrawer = null }
                )
                null -> Unit
            }
        }
        detailMovie?.let { movie ->
            MovieDetails(movie, movie.id in watchlist, compact, streamingApps,
                onClose = { detailMovie = null }, onWatch = { onOpenMovie(movie) },
                onWatchlist = { onToggleWatchlist(movie) }, onSearch = { onSearchOnProvider(movie, it) })
        }
        if (searchOpen) {
            SearchOverlay(
                query = searchText,
                onQueryChange = { searchText = it },
                apps = apps.filter { app -> searchText.isBlank() || app.label.contains(searchText, ignoreCase = true) },
                movies = movies.filter { movie -> searchText.isBlank() || movie.title.contains(searchText, ignoreCase = true) || movie.genres.contains(searchText, ignoreCase = true) || movie.description.contains(searchText, ignoreCase = true) },
                streamingApps = streamingApps,
                compact = compact,
                onClose = { searchOpen = false; searchText = ""; onVoiceQueryConsumed() },
                onVoiceSearch = onVoiceSearch,
                onLaunch = { app -> searchOpen = false; onVoiceQueryConsumed(); onLaunch(app) },
                onOpenMovie = { movie -> searchOpen = false; onVoiceQueryConsumed(); detailMovie = movie },
                onSearchProvider = { provider -> onSearchPhraseOnProvider(searchText, provider) }
            )
        }
        if (ambient) AmbientScreen(movies.firstOrNull(), currentTime) { ambient = false }
        }
    }
}


@Composable
private fun SearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    apps: List<TvApp>,
    movies: List<FeaturedMovie>,
    streamingApps: List<InstalledStreamingApp>,
    compact: Boolean,
    onClose: () -> Unit,
    onVoiceSearch: () -> Unit,
    onLaunch: (TvApp) -> Unit,
    onOpenMovie: (FeaturedMovie) -> Unit,
    onSearchProvider: (StreamingProvider) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestInitialFocus { focusRequester.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(Color(0xD9000000)).padding(if (compact) 8.dp else 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(if (compact) 22.dp else 30.dp))
                .background(Brush.verticalGradient(listOf(Color(0xEF303845), Color(0xF20F1218))))
                .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(if (compact) 22.dp else 30.dp))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 22.dp else 40.dp, vertical = if (compact) 16.dp else 28.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Search Auralis", color = Color.White, fontSize = if (compact) 26.sp else 36.sp, fontWeight = FontWeight.SemiBold)
                    Text("Find a title, an app, or search an installed service", color = Color(0xFFB7C0CE), fontSize = if (compact) 11.sp else 13.sp)
                }
                FocusButton("Voice", false, onVoiceSearch)
                Spacer(Modifier.width(8.dp))
                FocusButton("Close", false, onClose)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = if (compact) 18.sp else 22.sp),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF151A22))
                        .border(1.dp, Color(0x5563B8FF), RoundedCornerShape(14.dp))
                        .focusRequester(focusRequester).padding(horizontal = 16.dp, vertical = 13.dp),
                    decorationBox = { inner ->
                        Box {
                            if (query.isBlank()) Text("Speak or type a title or app", color = Color(0xFF8491A2), fontSize = if (compact) 16.sp else 19.sp)
                            inner()
                        }
                    }
                )
                FocusButton("Clear", false) { onQueryChange("") }
            }
            if (query.isBlank()) {
                Text("Search is limited to titles in Auralis's current discovery catalogue and apps installed on this TV.", color = Color(0xFF8995A6), fontSize = 11.sp)
            }
            if (apps.isNotEmpty()) {
                SectionHeading("Apps", "${apps.size} matches")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(apps, key = { _, app -> app.packageName }) { _, app ->
                        AppTile(app, if (compact) 142.dp else 170.dp, compact, false) { onLaunch(app) }
                    }
                }
            }
            if (movies.isNotEmpty()) {
                SectionHeading("Titles", "${movies.size} matches")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(movies, key = { _, movie -> movie.id }) { _, movie ->
                        MovieTile(movie, compact, false, onFocused = {}, onClick = { onOpenMovie(movie) })
                    }
                }
            }
            if (query.isNotBlank() && streamingApps.isNotEmpty()) {
                SectionHeading("Search services", "Search results depend on each service")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(streamingApps, key = { _, item -> item.provider.id }) { _, item ->
                        FocusButton(item.provider.displayName, false) { onSearchProvider(item.provider) }
                    }
                }
            }
            if (query.isNotBlank() && apps.isEmpty() && movies.isEmpty()) {
                Text("No matching apps or titles in Auralis's current catalogue.", color = Color(0xFFB7C0CE), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ControlCenter(
    compact: Boolean, activeProfile: String, profiles: List<String>, profileName: String,
    onProfileName: (String) -> Unit, onSelectProfile: (String) -> Unit, onAddProfile: () -> Unit,
    onVoiceSearch: () -> Unit, onSettings: () -> Unit, onRefresh: () -> Unit, onSetAsHome: () -> Unit, onAdBlock: () -> Unit,
    onScreensaver: () -> Unit, onClose: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color(0xA9000000)).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.width(if (compact) 440.dp else 600.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xF21B202A)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Control Center", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text("Quick controls · Profile: $activeProfile", color = Color(0xFFB7C0CE), fontSize = 12.sp)
                }
                FocusButton("Close", false, onClose)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusButton("Voice search", true, onVoiceSearch)
                    FocusButton("Screensaver", false, onScreensaver)
                    FocusButton("Refresh", false, onRefresh)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusButton("Set as Home", false, onSetAsHome)
                    FocusButton("Settings", false, onSettings)
                    FocusButton("DNS Ad Block", false, onAdBlock)
                }
            }
            Text("Profiles", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(profiles, key = { it }) { name -> FocusButton(name, false) { onSelectProfile(name) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicTextField(value = profileName, onValueChange = onProfileName, singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                    modifier = Modifier.weight(1f).background(Color(0xFF303744), RoundedCornerShape(12.dp)).padding(12.dp),
                    decorationBox = { inner -> Box { if (profileName.isBlank()) Text("New profile name", color = Color(0xFF9EA9B8)); inner() } })
                FocusButton("Add profile", false, onAddProfile)
            }
            Text("Watchlist and launcher history are stored on this device per profile. Playback progress inside streaming apps isn't available to Auralis.", color = Color(0xFF9EA9B8), fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun MovieDetails(
    movie: FeaturedMovie, watchlisted: Boolean, compact: Boolean, streamingApps: List<InstalledStreamingApp>,
    onClose: () -> Unit, onWatch: () -> Unit, onWatchlist: () -> Unit, onSearch: (StreamingProvider) -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color(0xCE000000)).padding(if (compact) 12.dp else 30.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Color(0xFF11151D))) {
            AsyncImage(model = rememberCinematicArtworkRequest(movie.backgroundUrl), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.28f })
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xF2090B10), Color(0xB5090B10), Color(0xE6090B10)))))
            Column(Modifier.fillMaxSize().padding(if (compact) 20.dp else 42.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("FILM DETAILS", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(movie.title, color = Color.White, fontSize = if (compact) 28.sp else 42.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(movie.metadataLine.ifBlank { "Details from the film catalogue" }, color = Color(0xFFD3DCE8), fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(movie.description.ifBlank { "Synopsis unavailable." }, color = Color(0xFFE2E7EF), fontSize = 15.sp, lineHeight = 22.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                    AsyncImage(model = rememberCinematicArtworkRequest(movie.posterUrl), contentDescription = movie.title, contentScale = ContentScale.Crop,
                        modifier = Modifier.padding(start = 18.dp).size(width = if (compact) 94.dp else 150.dp, height = if (compact) 138.dp else 220.dp).clip(RoundedCornerShape(14.dp)))
                }
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusButton(if (movie.trailerUrl != null) "Play trailer" else "Open IMDb details", true, onWatch)
                    FocusButton(if (watchlisted) "Remove from Watchlist" else "Add to Watchlist", false, onWatchlist)
                    FocusButton("Close", false, onClose)
                }
                if (streamingApps.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Search on an installed service", color = Color(0xFFB7C0CE), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { streamingApps.take(5).forEach { FocusButton(it.provider.displayName, false) { onSearch(it.provider) } } }
                }
                Spacer(Modifier.height(10.dp))
                Text("Trailer and service searches open their app or a browser when supported. Auralis does not stream this title.", color = Color(0xFF8995A6), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun AmbientScreen(movie: FeaturedMovie?, time: LocalDateTime, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink).clickable(onClick = onExit), contentAlignment = Alignment.Center) {
        if (movie != null) AsyncImage(model = rememberCinematicArtworkRequest(movie.backgroundUrl), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.38f })
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x11000000), Color(0xB8000000)))))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time.format(DateTimeFormatter.ofPattern("h:mm", Locale.getDefault())), color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.ExtraLight)
            Text(time.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())).uppercase(Locale.getDefault()), color = Color(0xFFD2D9E3), fontSize = 13.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(20.dp))
            Text("Press OK or Back to return", color = Color(0xFFB7C0CE), fontSize = 12.sp)
        }
    }
}

@Composable
private fun IdleScreensaverTimer(
    interactionRevision: State<Int>,
    screensaverActive: Boolean,
    onTimeout: () -> Unit
) {
    LaunchedEffect(interactionRevision.value, screensaverActive) {
        if (!screensaverActive) {
            delay(5 * 60 * 1000L)
            onTimeout()
        }
    }
}

@Composable
private fun AppDrawer(
    apps: List<TvApp>,
    appsLoading: Boolean,
    compact: Boolean,
    onClose: () -> Unit,
    onLaunch: (TvApp) -> Unit
) {
    val panelShape = RoundedCornerShape(if (compact) 22.dp else 30.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x8203060B))
            .padding(if (compact) 8.dp else 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(panelShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xE63A414B),
                            Color(0xE51A1D24),
                            Color(0xF20C0D11)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color(0xBFFFFFFF),
                            Color(0x42FFFFFF),
                            Color(0x1A8BCBFF),
                            Color(0x66FFFFFF)
                        )
                    ),
                    shape = panelShape
                )
        ) {
            // A restrained specular sheen gives the translucent surface a glass-like edge.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 100.dp else 170.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x30FFFFFF), Color(0x0DFFFFFF), Color.Transparent)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (compact) 18.dp else 32.dp,
                        vertical = if (compact) 14.dp else 24.dp
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(
                            "All Apps",
                            color = Color.White,
                            fontSize = if (compact) 27.sp else 36.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.6).sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            when {
                                appsLoading -> "Discovering apps on this device…"
                                apps.isEmpty() -> "No launchable apps found"
                                else -> "${apps.size} apps ready to launch"
                            },
                            color = Color(0xFFCAD1DD),
                            fontSize = if (compact) 11.sp else 13.sp
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    FocusButton(
                        label = "Close",
                        initialFocus = apps.isEmpty(),
                        onClick = onClose
                    )
                }

                Spacer(Modifier.height(if (compact) 14.dp else 22.dp))

                if (apps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (appsLoading) "Loading your apps…" else "Open Control Center and choose Refresh.",
                            color = Color(0xFFCAD1DD),
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = if (compact) 142.dp else 176.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp)
                    ) {
                        items(apps.size) { index ->
                            val app = apps[index]
                            DrawerAppCard(
                                app = app,
                                compact = compact,
                                initialFocus = index == 0,
                                onClick = { onLaunch(app) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationsDrawer(
    appsLoading: Boolean,
    streamingApps: List<InstalledStreamingApp>,
    movies: List<FeaturedMovie>,
    moviesLoading: Boolean,
    compact: Boolean,
    selectedMovieId: String?,
    onSelectMovie: (FeaturedMovie) -> Unit,
    onClose: () -> Unit,
    onLaunch: (TvApp) -> Unit,
    onOpenMovie: (FeaturedMovie) -> Unit,
    onSearchOnProvider: (FeaturedMovie, StreamingProvider) -> Unit
) {
    var selectedProviderId by remember(streamingApps.firstOrNull()?.provider?.id) {
        mutableStateOf(streamingApps.firstOrNull()?.provider?.id)
    }
    LaunchedEffect(streamingApps) {
        if (streamingApps.none { it.provider.id == selectedProviderId }) {
            selectedProviderId = streamingApps.firstOrNull()?.provider?.id
        }
    }

    val selectedProvider = streamingApps.firstOrNull { it.provider.id == selectedProviderId }
        ?: streamingApps.firstOrNull()
    val selectedMovie = movies.firstOrNull { it.id == selectedMovieId } ?: movies.firstOrNull()
    val panelShape = RoundedCornerShape(if (compact) 22.dp else 30.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x8203060B))
            .padding(if (compact) 8.dp else 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(panelShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xE63A414B), Color(0xE51A1D24), Color(0xF20C0D11))
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(Color(0xBFFFFFFF), Color(0x42FFFFFF), Color(0x1A8BCBFF), Color(0x66FFFFFF))
                    ),
                    shape = panelShape
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 96.dp else 150.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x30FFFFFF), Color(0x0DFFFFFF), Color.Transparent)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = if (compact) 18.dp else 32.dp,
                        vertical = if (compact) 14.dp else 24.dp
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "For You",
                            color = Color.White,
                            fontSize = if (compact) 27.sp else 36.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.6).sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            when {
                                appsLoading -> "Detecting installed streaming apps…"
                                streamingApps.isEmpty() -> "No supported streaming services detected"
                                else -> "${streamingApps.size} installed streaming service${if (streamingApps.size == 1) "" else "s"}"
                            },
                            color = Color(0xFFCAD1DD),
                            fontSize = if (compact) 11.sp else 13.sp
                        )
                    }
                    FocusButton(
                        label = "Close",
                        initialFocus = streamingApps.isEmpty() && movies.isEmpty(),
                        onClick = onClose
                    )
                }

                Spacer(Modifier.height(if (compact) 14.dp else 20.dp))
                SectionHeading(title = "Your streaming apps", detail = "Choose where to search")
                Spacer(Modifier.height(if (compact) 7.dp else 10.dp))

                if (streamingApps.isEmpty()) {
                    Text(
                        if (appsLoading) {
                            "Auralis is checking the apps installed on this TV."
                        } else {
                            "Install a supported service such as Netflix, Prime Video, Crunchyroll or YouTube, then select Refresh. Other launchable apps remain available in the Apps drawer."
                        },
                        color = Color(0xFFB7C0CE),
                        fontSize = if (compact) 12.sp else 14.sp,
                        lineHeight = if (compact) 17.sp else 20.sp
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 12.dp)) {
                        itemsIndexed(streamingApps, key = { _, item -> item.provider.id }) { index, installed ->
                            StreamingServiceTile(
                                installed = installed,
                                selected = installed.provider.id == selectedProvider?.provider?.id,
                                compact = compact,
                                initialFocus = index == 0,
                                onSelect = { selectedProviderId = installed.provider.id }
                            )
                        }
                    }
                }

                if (selectedProvider != null) {
                    Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
                    ) {
                        Text(
                            "Search target: ${selectedProvider.provider.displayName}",
                            color = Color(0xFFE7EDF7),
                            fontSize = if (compact) 12.sp else 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        FocusButton(
                            label = "Open service",
                            initialFocus = false,
                            onClick = { onLaunch(selectedProvider.app) }
                        )
                        if (selectedMovie != null) {
                            FocusButton(
                                label = "Search title",
                                initialFocus = false,
                                onClick = { onSearchOnProvider(selectedMovie, selectedProvider.provider) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(if (compact) 15.dp else 22.dp))
                SectionHeading(
                    title = "Recommended to explore",
                    detail = if (moviesLoading) "Refreshing catalogue…" else "${movies.size} picks"
                )
                Spacer(Modifier.height(if (compact) 5.dp else 8.dp))
                Text(
                    "These are general film-discovery picks, not a claim that a title is included with a service. Focus a title to update the home backdrop, open its details, or search it on the selected installed service.",
                    color = Color(0xFFB7C0CE),
                    fontSize = if (compact) 11.sp else 12.sp,
                    lineHeight = if (compact) 15.sp else 17.sp
                )
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))

                if (movies.isEmpty()) {
                    Text(
                        if (moviesLoading) "Loading recommendations…" else "The catalogue is unavailable. Check the network and use Refresh on the home screen.",
                        color = Color(0xFFCAD1DD),
                        fontSize = 15.sp
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)) {
                        itemsIndexed(movies, key = { _, movie -> movie.id }) { index, movie ->
                            MovieTile(
                                movie = movie,
                                compact = compact,
                                initialFocus = streamingApps.isEmpty() && index == 0,
                                onFocused = onSelectMovie,
                                onClick = { onOpenMovie(movie) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Search may open the service app through Android App Links or a browser, depending on the TV and service. Availability varies by country, subscription and catalogue.",
                    color = Color(0xFF8C98A9),
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun StreamingServiceTile(
    installed: InstalledStreamingApp,
    selected: Boolean,
    compact: Boolean,
    initialFocus: Boolean,
    onSelect: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(initialFocus) {
        if (initialFocus) requestInitialFocus { focusRequester.requestFocus() }
    }
    val scale by animateFloatAsState(
        targetValue = if (focused || selected) 1.035f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "streamingServiceScale"
    )
    val shape = RoundedCornerShape(if (compact) 14.dp else 18.dp)

    Column(
        modifier = Modifier
            .width(if (compact) 158.dp else 206.dp)
            .scale(scale)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (selected || focused) {
                        listOf(Color(0xBFE7F3FF), Color(0xA54A617A), Color(0xA527303D))
                    } else {
                        listOf(Color(0x4DFFFFFF), Color(0x22FFFFFF), Color(0x14FFFFFF))
                    }
                )
            )
            .border(
                width = if (selected || focused) 2.dp else 1.dp,
                color = if (selected || focused) Accent else Color(0x44FFFFFF),
                shape = shape
            )
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .clickable(onClick = onSelect)
            .padding(if (compact) 10.dp else 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            bitmap = installed.app.icon.asImageBitmap(),
            contentDescription = installed.provider.displayName,
            modifier = Modifier.size(if (compact) 40.dp else 52.dp)
        )
        Spacer(Modifier.height(if (compact) 5.dp else 8.dp))
        Text(
            installed.provider.displayName,
            color = Color.White,
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            if (selected) "Search target" else "Select service",
            color = Color(0xFFCAD1DD),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun DrawerAppCard(
    app: TvApp,
    compact: Boolean,
    initialFocus: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.055f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "drawerCardScale"
    )
    val shape = RoundedCornerShape(22.dp)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(initialFocus) {
        if (initialFocus) {
            requestInitialFocus { focusRequester.requestFocus() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 124.dp else 154.dp)
            .scale(scale)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (focused) {
                        listOf(Color(0xBFE7F3FF), Color(0xA54A617A), Color(0xA527303D))
                    } else {
                        listOf(Color(0x4DFFFFFF), Color(0x22FFFFFF), Color(0x14FFFFFF))
                    }
                )
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                brush = if (focused) {
                    Brush.linearGradient(listOf(Color.White, Color(0xFF91CEFF), Color(0xFFE7F5FF)))
                } else {
                    Brush.linearGradient(listOf(Color(0x72FFFFFF), Color(0x22FFFFFF), Color(0x45FFFFFF)))
                },
                shape = shape
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(if (compact) 10.dp else 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 52.dp else 72.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x33080B10))
                .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.size(if (compact) 42.dp else 58.dp)
            )
        }
        Spacer(Modifier.height(if (compact) 7.dp else 10.dp))
        Text(
            app.label,
            color = Color.White,
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun rememberCinematicArtworkRequest(url: String): ImageRequest {
    val context = LocalContext.current.applicationContext
    return remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(350)
            .build()
    }
}

@Composable
private fun TopShelf(
    app: TvApp?,
    movie: FeaturedMovie?,
    movies: List<FeaturedMovie>,
    moviesLoading: Boolean,
    recentlyOpened: Boolean,
    height: Dp,
    compact: Boolean,
    initialFocus: Boolean,
    onLaunch: (TvApp) -> Unit,
    onOpenMovie: (FeaturedMovie) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.012f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "topShelfScale"
    )
    val focusRequester = remember { FocusRequester() }
    val canActivate = movie != null || app != null

    LaunchedEffect(initialFocus) {
        if (initialFocus && canActivate) {
            requestInitialFocus { focusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .scale(scale)
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF252D3A), Color(0xFF171B22), Color(0xFF101114))
                )
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Accent else Color(0xFF343943),
                shape = RoundedCornerShape(30.dp)
            )
            .then(
                if (canActivate) {
                    Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused }
                        .clickable {
                            if (movie != null) onOpenMovie(movie) else app?.let(onLaunch)
                        }
                } else {
                    Modifier
                }
            )
    ) {
        if (movie != null) {
            Crossfade(
                targetState = movie.id,
                animationSpec = tween(durationMillis = 520),
                label = "cinematicBackdropCrossfade"
            ) { movieId ->
                val artwork = movies.firstOrNull { it.id == movieId } ?: movie
                AsyncImage(
                    model = rememberCinematicArtworkRequest(artwork.backgroundUrl.ifBlank { artwork.posterUrl }),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (compact) 0.56f else 0.72f }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xF207090D),
                                Color(0xD20B0E14),
                                Color(0x64101114)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x16000000), Color(0xA8000000))
                        )
                    )
            )
        } else if (app != null) {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (compact) 0.10f else 0.16f }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xF2252D3A), Color(0xCC171B22), Color(0x80101114))
                        )
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (compact) 22.dp else 34.dp,
                    vertical = if (compact) 14.dp else 20.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = if (compact) 12.dp else 20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = when {
                        movie != null -> "FEATURED FILM  •  DISCOVER"
                        app == null -> "AURALIS TV"
                        recentlyOpened -> "RECENTLY OPENED APP"
                        else -> "READY WHEN YOU ARE"
                    },
                    color = Accent,
                    fontSize = if (compact) 9.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
                Text(
                    text = movie?.title ?: app?.label ?: "Your TV, your apps.",
                    color = Color.White,
                    fontSize = if (compact) 25.sp else 35.sp,
                    lineHeight = if (compact) 29.sp else 39.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (movie != null) {
                    if (movie.metadataLine.isNotBlank()) {
                        Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
                        Text(
                            text = movie.metadataLine,
                            color = Color(0xFFCFD7E2),
                            fontSize = if (compact) 10.sp else 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!compact && movie.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = movie.description,
                            color = Color(0xFFCED5E0),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (!compact) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (app == null) {
                            if (moviesLoading) "Preparing your cinematic home screen…" else "Launchable apps will appear here."
                        } else if (recentlyOpened) {
                            "Recently opened • Ready to launch"
                        } else {
                            "Open directly from your home screen."
                        },
                        color = Color(0xFFB7C0CE),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                Text(
                    text = when {
                        movie != null -> "PRESS OK FOR FILM DETAILS"
                        app != null -> "PRESS OK TO OPEN"
                        else -> "A CINEMATIC HOME FOR YOUR TV"
                    },
                    color = if (focused) Color.White else Color(0xFFD5DFEE),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (movie?.posterUrl?.isNotBlank() == true) {
                AsyncImage(
                    model = rememberCinematicArtworkRequest(movie.posterUrl),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(
                            width = if (compact) 60.dp else 84.dp,
                            height = if (compact) 92.dp else 132.dp
                        )
                        .clip(RoundedCornerShape(if (compact) 10.dp else 14.dp))
                        .border(
                            1.dp,
                            Color(0x75FFFFFF),
                            RoundedCornerShape(if (compact) 10.dp else 14.dp)
                        )
                )
            } else if (app != null) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 88.dp else 154.dp)
                        .clip(RoundedCornerShape(if (compact) 18.dp else 25.dp))
                        .background(Color(0xFF252B35))
                        .border(
                            1.dp,
                            Color(0x3FFFFFFF),
                            RoundedCornerShape(if (compact) 18.dp else 25.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = app.icon.asImageBitmap(),
                        contentDescription = app.label,
                        modifier = Modifier.size(if (compact) 66.dp else 116.dp)
                    )
                }
            } else {
                Text(
                    "A",
                    color = Accent,
                    fontSize = if (compact) 40.sp else 64.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}

@Composable
private fun MovieTile(
    movie: FeaturedMovie,
    compact: Boolean,
    initialFocus: Boolean,
    onFocused: (FeaturedMovie) -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(initialFocus) {
        if (initialFocus) requestInitialFocus { focusRequester.requestFocus() }
    }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.055f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "movieTileScale"
    )
    val shape = RoundedCornerShape(if (compact) 13.dp else 16.dp)

    Column(
        modifier = Modifier
            .width(if (compact) 142.dp else 184.dp)
            .scale(scale)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused(movie)
            }
            .clickable(onClick = onClick)
            .padding(1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 68.dp else 96.dp)
                .clip(shape)
                .background(Color(0xFF20242B))
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Accent else Color(0x55FFFFFF),
                    shape = shape
                )
        ) {
            AsyncImage(
                model = rememberCinematicArtworkRequest(movie.backgroundUrl.ifBlank { movie.posterUrl }),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x08000000), Color(0x99000000))
                        )
                    )
            )
        }
        Spacer(Modifier.height(if (compact) 5.dp else 7.dp))
        Text(
            text = movie.title,
            color = if (focused) Color.White else Color(0xFFCDD4DF),
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionHeading(title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(11.dp))
        Text(
            detail,
            color = Color(0xFF8793A5),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun AppTile(
    app: TvApp,
    width: Dp,
    compact: Boolean,
    initialFocus: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.075f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "appTileScale"
    )
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(initialFocus) {
        if (initialFocus) {
            requestInitialFocus { focusRequester.requestFocus() }
        }
    }

    Column(
        modifier = Modifier
            .width(width)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(if (focused) Color(0xFF34363B) else Color(0xFF202124))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Accent else Color(0xFF303034),
                shape = RoundedCornerShape(18.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(if (compact) 12.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 66.dp else 100.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xFF263344)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.size(if (compact) 54.dp else 82.dp)
            )
        }
        Spacer(Modifier.height(if (compact) 6.dp else 9.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = if (compact) 13.sp else 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FocusButton(
    label: String,
    initialFocus: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "settingsScale"
    )
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(initialFocus) {
        if (initialFocus) {
            requestInitialFocus { focusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .background(if (focused) Color(0xFFF4F4F5) else Color(0xFF202023))
            .border(
                1.dp,
                if (focused) Color.White else Color(0xFF343438),
                RoundedCornerShape(22.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            color = if (focused) Color(0xFF101012) else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
