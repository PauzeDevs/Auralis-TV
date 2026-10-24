package dev.pauze.auralis

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class TvApp(val label: String, val packageName: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apps = packageManager.getInstalledApplications(0)
            .mapNotNull { info ->
                val launch = packageManager.getLaunchIntentForPackage(info.packageName)
                if (launch == null || info.packageName == packageName) null
                else TvApp(packageManager.getApplicationLabel(info).toString(), info.packageName)
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
    var clock by remember { mutableStateOf("AURALIS • YOUR LIVING ROOM") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF101A2C), Color(0xFF080A0E), Color(0xFF090D12))))
            .padding(start = 54.dp, end = 54.dp, top = 32.dp, bottom = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("auralis", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                Spacer(Modifier.width(14.dp))
                Text("TV", color = Color(0xFFB7F36B), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text(clock, color = Color(0xFF9BA6B7), fontSize = 12.sp, letterSpacing = 1.5.sp)
            }
            Spacer(Modifier.height(26.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(228.dp).clip(RoundedCornerShape(28.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF263C59), Color(0xFF182536), Color(0xFF111720))))
                    .padding(horizontal = 34.dp, vertical = 28.dp)
            ) {
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    Text("YOUR ENTERTAINMENT, REIMAGINED", color = Color(0xFFB7F36B), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("A calmer way\nto watch.", color = Color.White, fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Text("Everything you love. One beautiful screen.", color = Color(0xFFD0D8E4), fontSize = 15.sp)
                }
                Box(modifier = Modifier.align(Alignment.CenterEnd).size(138.dp).clip(CircleShape).background(Color(0x22B7F36B)), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(94.dp).clip(CircleShape).background(Color(0x33B7F36B)), contentAlignment = Alignment.Center) {
                        Text("A", color = Color(0xFFB7F36B), fontSize = 64.sp, fontWeight = FontWeight.Light)
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your apps", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                Text("${apps.size} installed", color = Color(0xFF8E99AA), fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))
            if (apps.isEmpty()) {
                Text("No launchable apps found yet.", color = Color(0xFFADB7C6), fontSize = 16.sp)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        AppTile(app = app, onClick = { onLaunch(app.packageName) })
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Press OK to open an app", color = Color(0xFF7F8A9A), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                FocusButton(label = "Settings", onClick = onOpenSettings)
            }
        }
    }
}

@Composable
private fun AppTile(app: TvApp, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.width(142.dp).clip(RoundedCornerShape(20.dp))
            .background(if (focused) Color(0xFF273344) else Color(0xFF151B24))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color(0xFFB7F36B) else Color(0xFF29313D), RoundedCornerShape(20.dp))
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable().padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(78.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF263344)), contentAlignment = Alignment.Center) {
            Text(app.label.take(1).uppercase(), color = Color(0xFFB7F36B), fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text(app.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun FocusButton(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(24.dp))
            .background(if (focused) Color(0xFFB7F36B) else Color(0xFF202936))
            .border(1.dp, if (focused) Color(0xFFB7F36B) else Color(0xFF3A4656), CircleShape)
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable()
            .padding(horizontal = 20.dp, vertical = 11.dp)
    ) {
        Text(label, color = if (focused) Color(0xFF10150A) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
