package dev.pauze.auralis

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    onLaunch = { app ->
                        if (launchApp(app)) {
                            recentPackages = updatedRecentPackages(recentPackages, app.packageName)
                            preferences.edit()
                                .putString(RECENT_PACKAGES_KEY, recentPackages.joinToString("\n"))
                                .apply()
                        }
                    },
                    onOpenSettings = { openSystemSettings() },
                    onRefreshApps = { refreshApps() }
                )
            }
        }

        refreshApps()
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
            if (launchIntent == null) {
                Toast.makeText(
                    this,
                    "${app.label} is no longer available.",
                    Toast.LENGTH_SHORT
                ).show()
                refreshApps()
                false
            } else {
                startActivity(launchIntent)
                true
            }
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
    }
}

@Composable
private fun AuralisHome(
    apps: List<TvApp>,
    appsLoading: Boolean,
    recentApps: List<TvApp>,
    onLaunch: (TvApp) -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshApps: () -> Unit
) {
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxHeight < 620.dp
        val horizontalPadding = if (compact) 34.dp else 52.dp
        val verticalPadding = if (compact) 16.dp else 26.dp
        val heroHeight = if (compact) 142.dp else 190.dp
        val tileWidth = if (compact) 116.dp else 138.dp
        val featuredApp = recentApps.firstOrNull() ?: apps.firstOrNull()
        val featuredIsRecent = recentApps.isNotEmpty()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF171719), Color(0xFF09090A), Ink)
                    )
                )
                .padding(
                    horizontal = horizontalPadding,
                    vertical = verticalPadding
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                    recentlyOpened = featuredIsRecent,
                    height = heroHeight,
                    compact = compact,
                    initialFocus = featuredApp != null,
                    onLaunch = onLaunch
                )

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

                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Navigate with your remote  •  OK to open",
                        color = Color(0xFF788597),
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.weight(1f))
                    FocusButton(
                        label = "Refresh apps",
                        initialFocus = false,
                        onClick = onRefreshApps
                    )
                    Spacer(Modifier.width(12.dp))
                    FocusButton(
                        label = "System settings",
                        initialFocus = !appsLoading && apps.isEmpty(),
                        onClick = onOpenSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun TopShelf(
    app: TvApp?,
    recentlyOpened: Boolean,
    height: Dp,
    compact: Boolean,
    initialFocus: Boolean,
    onLaunch: (TvApp) -> Unit
) {
    var focused by remember(app?.packageName) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.012f else 1f,
        label = "topShelfScale"
    )
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(initialFocus, app?.packageName) {
        if (initialFocus) {
            requestInitialFocus { focusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .scale(scale)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF252D3A),
                        Color(0xFF171B22),
                        Color(0xFF101114)
                    )
                )
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Accent else Color(0xFF343943),
                shape = RoundedCornerShape(26.dp)
            )
            .then(
                if (app != null) {
                    Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused }
                        .clickable { onLaunch(app) }
                } else {
                    Modifier
                }
            )
            .padding(
                horizontal = if (compact) 22.dp else 34.dp,
                vertical = if (compact) 14.dp else 20.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
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
                        app == null -> "AURALIS TV"
                        recentlyOpened -> "PICK UP WHERE YOU LEFT OFF"
                        else -> "READY WHEN YOU ARE"
                    },
                    color = Accent,
                    fontSize = if (compact) 9.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    maxLines = 1
                )
                Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
                Text(
                    text = app?.label ?: "Your TV, your apps.",
                    color = Color.White,
                    fontSize = if (compact) 25.sp else 35.sp,
                    lineHeight = if (compact) 29.sp else 39.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!compact) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (app == null) {
                            "Launchable apps will appear here."
                        } else if (recentlyOpened) {
                            "Recently opened • Ready to launch"
                        } else {
                            "Open directly from your home screen."
                        },
                        color = Color(0xFFB7C0CE),
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                Text(
                    text = if (app == null) "NO LAUNCHABLE APPS FOUND" else "PRESS OK TO OPEN",
                    color = if (focused) Color.White else Color(0xFFD5DFEE),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(if (compact) 78.dp else 124.dp)
                    .clip(RoundedCornerShape(if (compact) 18.dp else 25.dp))
                    .background(Color(0xFF252B35))
                    .border(
                        1.dp,
                        Color(0x3FFFFFFF),
                        RoundedCornerShape(if (compact) 18.dp else 25.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (app != null) {
                    Image(
                        bitmap = app.icon.asImageBitmap(),
                        contentDescription = app.label,
                        modifier = Modifier.size(if (compact) 58.dp else 94.dp)
                    )
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
            .background(if (focused) Color(0xFF2B2B2F) else TileSurface)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Accent else Color(0xFF303034),
                shape = RoundedCornerShape(18.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(if (compact) 8.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 50.dp else 70.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xFF263344)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.size(if (compact) 42.dp else 58.dp)
            )
        }
        Spacer(Modifier.height(if (compact) 6.dp else 9.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = if (compact) 11.sp else 13.sp,
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
