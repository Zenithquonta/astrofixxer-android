package org.astrofixxer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.astrofixxer.astro.ApparentPosition
import org.astrofixxer.astro.JulianDate
import kotlin.math.PI

// ponytail: placeholder screen proving the astronomy core runs on device; replaced by the sky view in Phase 5.
private const val DEFAULT_LAT = 28.6139
private const val DEFAULT_LON = 77.2090

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF00BFFF), background = Color.Black)) {
                SkyNow()
            }
        }
    }
}

@Composable
private fun SkyNow() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val jd = JulianDate.fromEpochMillis(now)
    val bodies = listOf(ApparentPosition.SUN, ApparentPosition.MOON, ApparentPosition.MERCURY, ApparentPosition.VENUS,
        ApparentPosition.MARS, ApparentPosition.JUPITER, ApparentPosition.SATURN, ApparentPosition.URANUS, ApparentPosition.NEPTUNE)
    Column(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("AstroFixxer", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text("New Delhi · Alt / Az now", color = Color.Gray)
        for (b in bodies) {
            val p = ApparentPosition.reduce(b, jd, DEFAULT_LAT * PI / 180, DEFAULT_LON * PI / 180)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(ApparentPosition.bodies[b], color = if (p.alt > 0) Color.White else Color.DarkGray)
                Text("%6.1f°  %6.1f°".format(p.alt * 180 / PI, p.az * 180 / PI),
                    fontFamily = FontFamily.Monospace, color = if (p.alt > 0) Color.White else Color.DarkGray)
            }
        }
    }
}
