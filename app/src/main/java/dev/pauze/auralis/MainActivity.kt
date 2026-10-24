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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class TvApp(val label: String, val packageName: String, val icon: Bitmap)

private val Lime = Color(0xFFB7F36B)
private val CanvasDark = Color(0xFF080B10)
private val SurfaceDark = Color(0xFF151C27)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val apps = packageManager.getInstalledApplications(0)
            .mapNotNull { info ->
                val launchIntent = packageManager.getLaunchIntentForPackage(info.packageName)
                if (launchIntent == null || info.packageName == packageName) {
                    null
                } else {
                    val drawable = packageManager.getApplicationIcon(info)
                    val bitmap = Bitmap.createBitmap(
                        drawable.intrinsicWidth.coerceAtLeast(1),
                        drawable.intrinsicHeight.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    TvApp(packageManager.getApplicationLabel(info).toString(), info.packageName, bitmap)
                }
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }

        setContent {
            MaterialTheme {
                AuralisHome(
                    apps = apps,
                    onLaunch = { pkg ->
                        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                    },
                    onOpenSettings = {
                        startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                    }
                )
            }
        }
    }
}

@Composable
private fun AuralisHome(
    apps: List<TvApp>,
    onLaunch: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF14233A), CanvasDark, Color(0xFF090D13))))
            .padding(start = 52.dp, end = 52.dp, top = 30.dp, bottom = 26.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("auralis", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                Spacer(Modifier.width(12.dp))
                Text("TV", color = Lime, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Text("YOUR LIVING ROOM, REFINED", color = Color(0xFF8996A9), fontSize = 11.sp, letterSpacing = 1.6.sp)
            }

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(218.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF263D5B), Color(0xFF182638), Color(0xFF101720))))
                    .border(1.dp, Color(0xFF34445A), RoundedCornerShape(28.dp))
                    .padding(horizontal = 34.dp, vertical = 26.dp)
            ) {
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    Text("MADE FOR THE BIG SCREEN", color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Your next\nfavorite starts here.", color = Color.White, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(9.dp))
                    Text("All your apps. One calm, cinematic home.", color = Color(0xFFD0D8E4), fontSize = 14.sp)
                }
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd).size(142.dp)
                        .clip(CircleShape).background(Color(0x20B7F36B),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(102.dp).clip(CircleShape).background(Color(0x2EB7F36B)), contentAlignment = Alignment.Center) {
                        Text("A", color = Lime, fontSize = 70.sp, fontWeight = FontWeight.Light)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your apps", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                Text("${apps.size} ready to launch", color = Color(0xFF8793A5), fontSize = 12.sp)
            }

            Spacer(Modifier.height(14.dp))

            if (apps.isEmpty()) {
                Text("No launchable apps found yet.", color = Color(0xFFADB7C6), fontSize = 16.sp)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        AppTile(app = app, onClick = { onLaunch(app.packageName) })
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use your remote • Select an app to open", color = Color(0xFF788597), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                FocusButton(label = "System settings", onClick = onOpenSettings)
            }
        }
    }
}

@Composable
private fun AppTile(app: TvApp, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.06f else 1f, label = "tileScale")

    Column(
        modifier = Modifier
            .width(142.dp)
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(if (focused) Color(0xFF263344) else SurfaceDark)
            .border(if (focused) 2.dp else 1.dp, if (focused) Lime else Color(0xFF29313D), RoundedCornerShape(20.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(13.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(82.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF263344)),
            contentAlignment = Alignment.Center
        ) {
            Image(bitmap = app.icon.asImageBitmap(), contentDescription = app.label, modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.height(11.dp))
        Text(app.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun FocusButton(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.04f else 1f, label = "buttonScale")

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .background(if (focused) Lime else Color(0xFF202936))
            .border(1.dp, if (focused) Lime else Color(0xFF3A4656), CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 20.dp, vertical = 11.dp)
    ) {
        Text(label, color = if (focused) Color(0xFF10150A) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
