package dev.pauze.auralis

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class TvApp(
    val label: String,
    val packageName: String,
    val launchActivityName: String,
    val icon: Bitmap
)

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
private val TileSurface = Color(0xFF171719)

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
    private lateinit var preferences: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = getSharedPreferences("auralis_preferences", MODE_PRIVATE)
        configureHighRefreshRate()

        setContent {
            MaterialTheme {
                var recentPackages by remember {
                    mutableStateOf(readRecentPackages())
                }
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
                    onLaunch = { app ->
                        if (launchApp(app)) {
                            recentPackages = updatedRecentPackages(recentPackages, app.packageName)
                            preferences.edit()
                                .putString(RECENT_PACKAGES_KEY, recentPackages.joinToString("\n"))
                                .apply()
                        }
                    },
                    onOpenSettings = { openSystemSettings() },
                    onOpenMovie = { movie -> openMovie(movie) },
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
        if (hasResumedOnce) {
            refreshApps()
        }
        hasResumedOnce = true
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

            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
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

    private fun readRecentPackages(): List<String> =
        preferences.getString(RECENT_PACKAGES_KEY, "")
            .orEmpty()
            .split("\n")
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)

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
        private const val FEATURED_MOVIES_CACHE_KEY = "featured_movies_catalog"
        private const val FEATURED_MOVIES_URL =
            "https://cinemeta-catalogs.strem.io/top/catalog/movie/top.json"
        private const val MOVIE_CATALOG_TIMEOUT_MS = 8_000
        private const val MAX_FEATURED_MOVIES = 20
    }
}

@Composable
private fun AuralisHome(
    apps: List<TvApp>,
    appsLoading: Boolean,
    recentApps: List<TvApp>,
    movies: List<FeaturedMovie>,
    moviesLoading: Boolean,
    onLaunch: (TvApp) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMovie: (FeaturedMovie) -> Unit,
    onRefreshApps: () -> Unit
) {
    var drawerOpen by remember { mutableStateOf(false) }
    var selectedMovieId by remember(movies.firstOrNull()?.id) {
        mutableStateOf(movies.firstOrNull()?.id)
    }
    LaunchedEffect(movies) {
        if (movies.none { it.id == selectedMovieId }) {
            selectedMovieId = movies.firstOrNull()?.id
        }
    }
    val selectedMovie = movies.firstOrNull { it.id == selectedMovieId } ?: movies.firstOrNull()
    BackHandler(enabled = drawerOpen) { drawerOpen = false }

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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Layout responds to usable dp space (resolution + density), not the TV's inch size.
        val compact = maxHeight < 620.dp || maxWidth < 900.dp
        val shouldScroll = maxHeight < 780.dp
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
                    onOpenMovie = onOpenMovie
                )

                if (movies.isNotEmpty()) {
                    Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
                    SectionHeading(
                        title = "Movie discovery",
                        detail = if (moviesLoading) "Refreshing catalogue…" else "Focus to preview  •  OK for trailer"
                    )
                    Spacer(Modifier.height(if (compact) 6.dp else 9.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)) {
                        itemsIndexed(movies, key = { _, movie -> movie.id }) { _, movie ->
                            MovieTile(
                                movie = movie,
                                compact = compact,
                                onFocused = { selectedMovieId = it.id },
                                onClick = { onOpenMovie(movie) }
                            )
                        }
                    }
                }

                if (recentApps.isNotEmpty()) {
                    Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
                    SectionHeading(
                        title = "Recently opened",
                        detail = "Pick up where you left off"
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
                    Text(
                        "Auralis TV",
                        color = Color(0xFF788597),
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.weight(1f))
                    FocusButton(
                        label = "All apps",
                        initialFocus = false,
                        onClick = { drawerOpen = true }
                    )
                    Spacer(Modifier.width(10.dp))
                    FocusButton(
                        label = "Refresh",
                        initialFocus = false,
                        onClick = onRefreshApps
                    )
                    Spacer(Modifier.width(10.dp))
                    FocusButton(
                        label = "System settings",
                        initialFocus = !appsLoading && apps.isEmpty(),
                        onClick = onOpenSettings
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.975f, animationSpec = tween(220)),
            exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.985f, animationSpec = tween(140))
        ) {
            AppDrawer(
                apps = apps,
                appsLoading = appsLoading,
                compact = compact,
                onClose = { drawerOpen = false },
                onLaunch = { app ->
                    onLaunch(app)
                    drawerOpen = false
                }
            )
        }
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
                            if (appsLoading) "Loading your apps…" else "Try Refresh from the home screen.",
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
private fun DrawerAppCard(
    app: TvApp,
    compact: Boolean,
    initialFocus: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.055f else 1f,
        animationSpec = tween(durationMillis = if (focused) 170 else 140),
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
        animationSpec = tween(durationMillis = 220),
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
                        recentlyOpened -> "PICK UP WHERE YOU LEFT OFF"
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
                        movie?.trailerUrl != null -> "PRESS OK TO WATCH TRAILER"
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
    onFocused: (FeaturedMovie) -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.055f else 1f,
        animationSpec = tween(durationMillis = if (focused) 180 else 140),
        label = "movieTileScale"
    )
    val shape = RoundedCornerShape(if (compact) 13.dp else 16.dp)

    Column(
        modifier = Modifier
            .width(if (compact) 142.dp else 184.dp)
            .scale(scale)
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
        targetValue = if (focused) 1.06f else 1f,
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
        targetValue = if (focused) 1.04f else 1f,
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
