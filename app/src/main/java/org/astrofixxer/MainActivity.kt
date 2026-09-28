package org.astrofixxer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.astrofixxer.astro.Catalog
import org.astrofixxer.astro.JulianDate
import org.astrofixxer.astro.MinorBody
import org.astrofixxer.astro.SkyObject
import org.astrofixxer.ui.EventItem
import org.astrofixxer.ui.MovingObject
import org.astrofixxer.ui.PointingMode
import org.astrofixxer.ui.SkyScreen
import org.astrofixxer.ui.SkyState
import org.astrofixxer.ui.parseMeteorShowers
import org.astrofixxer.ui.solarSystem
import org.astrofixxer.ui.upcomingEvents
import kotlin.math.PI

private const val DEFAULT_LAT = 28.6139 // New Delhi until a location fix arrives
private const val DEFAULT_LON = 77.2090

/** Sensor smoothing: fraction of each new reading kept. Real sensors jitter; tune on devices. */
private const val SENSOR_SMOOTHING = 0.25

class MainActivity : ComponentActivity(), SensorEventListener {
    private val state = SkyState(System.currentTimeMillis(), DEFAULT_LAT, DEFAULT_LON)
    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private val smoothed = DoubleArray(9)
    private var hasReading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor == null) {
            // No compass: gyro + gravity only, so the user fixes azimuth by dragging the sky.
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            state.mode = PointingMode.MANUAL
        }
        updateLocation()

        setContent {
            var catalog by remember { mutableStateOf<Catalog?>(null) }
            var comets by remember { mutableStateOf<List<MinorBody>>(emptyList()) }
            var events by remember { mutableStateOf<List<EventItem>?>(null) }
            var art by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) updateLocation()
            }
            LaunchedEffect(Unit) { permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
            LaunchedEffect(Unit) {
                while (true) {
                    if (state.live) state.timeMillis = System.currentTimeMillis()
                    delay(1000)
                }
            }
            LaunchedEffect(Unit) {
                val bodies = withContext(Dispatchers.IO) { MinorBody.parse(assets.open("minor_bodies.json").bufferedReader().readText()) }
                comets = bodies.filter { it.isComet }
                catalog = withContext(Dispatchers.IO) { assets.open("sky_catalog.json.gz").use { Catalog.load(it) } }
                val showers = withContext(Dispatchers.IO) { parseMeteorShowers(assets.open("meteor_showers.json").bufferedReader().readText()) }
                val brightStars = catalog!!.objects.filter { it.type == "S" && (it.mag ?: 99.0) <= 3.5 }.map { Triple(it.name, it.ra, it.dec) }
                events = withContext(Dispatchers.Default) {
                    upcomingEvents(JulianDate.fromEpochMillis(System.currentTimeMillis()), 60, showers, bodies, brightStars, state.lat, state.lon)
                }
            }
            // Illustrations for the chosen sky culture, decoded at half size to keep memory near 11 MB.
            LaunchedEffect(catalog, state.skyCulture, state.showArt) {
                val cat = catalog ?: return@LaunchedEffect
                if (!state.showArt) return@LaunchedEffect
                val culture = state.skyCulture
                val missing = cat.constellations[culture].orEmpty().mapNotNull { it.art?.file }.filter { "$culture/$it" !in art }
                if (missing.isEmpty()) return@LaunchedEffect
                val loaded = withContext(Dispatchers.IO) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2; inPreferredConfig = Bitmap.Config.RGB_565 }
                    missing.associate { f -> "$culture/$f" to assets.open("art/$culture/$f").use { BitmapFactory.decodeStream(it, null, opts)!!.asImageBitmap() } }
                }
                art = art + loaded
            }
            val minute = state.timeMillis / 60000
            val moving = remember(minute, comets, state.lat, state.lon) {
                val jd = JulianDate.fromEpochMillis(state.timeMillis)
                solarSystem(state) + comets.mapNotNull { c ->
                    val p = c.geocentric(jd)
                    val mag = p.mag ?: return@mapNotNull null
                    if (mag > 11) null
                    else MovingObject(SkyObject("Comet ${c.name}", p.ra * 180 / PI, p.dec * 180 / PI, mag, "C"), MovingObject.Kind.COMET)
                }
            }
            SkyScreen(state, catalog, moving, events, art)
        }
    }

    private fun updateLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val fix = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time } ?: return
        state.lat = fix.latitude
        state.lon = fix.longitude
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        updateLocation()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Device-to-world (east, north, up) rotation, the same frame the web app built from DeviceOrientation.
        // ponytail: magnetic declination not applied; one-star alignment absorbs it (1-2° across India).
        val r = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(r, event.values)
        for (i in 0 until 9) {
            // ponytail: element-wise low-pass; fine for small per-frame changes, re-orthonormalise if jitter shows.
            smoothed[i] = if (hasReading) smoothed[i] + SENSOR_SMOOTHING * (r[i] - smoothed[i]) else r[i].toDouble()
        }
        hasReading = true
        state.device = smoothed.copyOf()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
