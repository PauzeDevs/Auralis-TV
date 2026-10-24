package dev.pauze.auralis

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class TvApp(
    val label: String,
    val packageName: String,
    val icon: Bitmap
)

private val Accent = Color(0xFF63B8FF)
private val Ink = Color(0xFF050506)
private val TileSurface = Color(0xFF171719)

class MainActivity : ComponentActivity() {
    private var installedApps by mutableStateOf<List<TvApp>>(emptyList())
    private var hasResumedOnce = false
    private lateinit var preferences: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = getSharedPreferences("auralis_preferences", MODE_PRIVATE)
        installedApps = loadApps()

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
                    recentApps = recentApps,
                    onLaunch = { app ->
                        recentPackages = (
                            listOf(app.packageName) +
                                recentPackages.filterNot { it == app.packageName }
                            ).take(12)
                        preferences.edit()
                            .putString(RECENT_PACKAGES_KEY, recentPackages.joinToString("\n"))
                            .apply()

                        packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                            startActivity(it)
                        }
                    },
                    onOpenSettings = {
                        startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasResumedOnce) {
            installedApps = loadApps()
        }
        hasResumedOnce = true
    }

    private fun readRecentPackages(): List<String> =
        preferences.getString(RECENT_PACKAGES_KEY, "")
            .orEmpty()
            .split("\n")
            .filter { it.isNotBlank() }
            .take(12)

    private fun loadApps(): List<TvApp> {
        return packageManager.getInstalledApplications(0)
            .mapNotNull { info ->
                if (info.packageName == packageName ||
                    packageManager.getLaunchIntentForPackage(info.packageName) == null
                ) {
                    null
                } else {
                    runCatching {
                        val drawable = packageManager.getApplicationIcon(info)
                        val bitmap = Bitmap.createBitmap(
                            ICON_BITMAP_SIZE,
                            ICON_BITMAP_SIZE,
                            Bitmap.Config.ARGB_8888
                        )
                        drawable.setBounds(8, 8, ICON_BITMAP_SIZE - 8, ICON_BITMAP_SIZE - 8)
                        drawable.draw(Canvas(bitmap))
                        TvApp(
                            label = packageManager.getApplicationLabel(info).toString(),
                            packageName = info.packageName,
                            icon = bitmap
                        )
                    }.getOrNull()
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
    recentApps: List<TvApp>,
    onLaunch: (TvApp) -> Unit,
    onOpenSettings: () -> Unit
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
                HeroCard(height = heroHeight, compact = compact)

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
                                initialFocus = index == 0,
                                onClick = { onLaunch(app) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
                SectionHeading(
                    title = "Your apps",
                    detail = "${apps.size} available"
                )
                Spacer(Modifier.height(if (compact) 6.dp else 10.dp))

                if (apps.isEmpty()) {
                    Text(
                        "No launchable apps found. Try restarting Auralis TV.",
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
                                initialFocus = recentApps.isEmpty() && index == 0,
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
                        label = "System settings",
                        initialFocus = apps.isEmpty(),
                        onClick = onOpenSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(height: Dp, compact: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF39393D),
                        Color(0xFF202023),
                        Color(0xFF111113)
                    )
                )
            )
            .border(1.dp, Color(0xFF3A3A3E), RoundedCornerShape(26.dp))
            .padding(horizontal = if (compact) 22.dp else 34.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterStart)
                .fillMaxWidth(0.76f)
        ) {
            Text(
                "MADE FOR THE BIG SCREEN",
                color = Lime,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp
            )
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            Text(
                "Your next\nfavorite starts here.",
                color = Color.White,
                fontSize = if (compact) 28.sp else 36.sp,
                lineHeight = if (compact) 32.sp else 40.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (!compact) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "All your apps. One calm, cinematic home.",
                    color = Color(0xFFD0D8E4),
                    fontSize = 14.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(if (compact) 84.dp else 126.dp)
                .clip(CircleShape)
                .background(Color(0x20FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 60.dp else 90.dp)
                    .clip(CircleShape)
                    .background(Color(0x2EFFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "A",
                    color = Lime,
                    fontSize = if (compact) 44.sp else 62.sp,
                    fontWeight = FontWeight.Light
                )
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
            delay(180)
            runCatching { focusRequester.requestFocus() }
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
                color = if (focused) Lime else Color(0xFF303034),
                shape = RoundedCornerShape(18.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
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
            maxLines = 1
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
            delay(180)
            runCatching { focusRequester.requestFocus() }
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
            .focusable()
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
