package org.astrofixxer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.astrofixxer.astro.Catalog
import org.astrofixxer.astro.JulianDate
import org.astrofixxer.astro.MinorBody
import org.astrofixxer.astro.Sgp4
import org.astrofixxer.astro.SkyObject
import org.astrofixxer.ui.AstroGuide
import org.astrofixxer.ui.EventItem
import org.astrofixxer.ui.MovingObject
import org.astrofixxer.ui.PointingMode
import org.astrofixxer.ui.SkyScreen
import org.astrofixxer.ui.SkyState
import org.astrofixxer.ui.parseMeteorShowers
import org.astrofixxer.ui.solarSystem
import org.astrofixxer.ui.upcomingEvents
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
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

    // AstroGuide: offline speech in, text-to-speech out. Latest sky data mirrored from composition.
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var latestCatalog: Catalog? = null
    private var latestMoving: List<MovingObject> = emptyList()
    private var latestEvents: List<EventItem>? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) listen() }
    private val guideListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            state.guideListening = false
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
            respond(heard)
        }
        override fun onError(error: Int) {
            state.guideListening = false
            state.guideAnswer = "Didn't catch that. Tap Ask and try again."
        }
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

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
        val prefs = getSharedPreferences("astrofixxer", MODE_PRIVATE)
        state.userObjectsText = prefs.getString("user_objects", "") ?: ""
        state.watchListText = prefs.getString("watch_list", "") ?: ""
        state.showOnboarding = !prefs.getBoolean("onboarding_done", false)
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply { setRecognitionListener(guideListener) }
        }
        tts = TextToSpeech(this) {}

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
                snapshotFlow { Triple(state.userObjectsText, state.watchListText, state.showOnboarding) }.collect { (objects, lists, onboarding) ->
                    prefs.edit().putString("user_objects", objects).putString("watch_list", lists)
                        .putBoolean("onboarding_done", prefs.getBoolean("onboarding_done", false) || !onboarding).apply()
                }
            }
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
                state.applyUserText(catalog)
                val showers = withContext(Dispatchers.IO) { parseMeteorShowers(assets.open("meteor_showers.json").bufferedReader().readText()) }
                val brightStars = catalog!!.objects.filter { it.type == "S" && (it.mag ?: 99.0) <= 3.5 }.map { Triple(it.name, it.ra, it.dec) }
                val satellites = loadSatellites()
                val now = JulianDate.fromEpochMillis(System.currentTimeMillis())
                events = withContext(Dispatchers.Default) {
                    upcomingEvents(now, 60, showers, bodies, brightStars, state.lat, state.lon, satellites)
                } + if (satellites.isEmpty()) listOf(EventItem(now, "ISS passes",
                    detailKey = "Connect to the internet once to download satellite orbits; everything else works offline.")) else emptyList()
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
            SideEffect {
                latestCatalog = catalog
                latestMoving = moving
                latestEvents = events
            }
            SkyScreen(state, catalog, moving, events, art, onAsk = if (recognizer != null) ::ask else null)
        }
    }

    /**
     * ISS and Tiangong orbits (TLE) from CelesTrak, refreshed at most daily when online and cached for offline use.
     * This and nothing else in the sky data needs the network.
     */
    private suspend fun loadSatellites(): List<Sgp4> = withContext(Dispatchers.IO) {
        val cache = File(filesDir, "tle.txt")
        if (!cache.exists() || System.currentTimeMillis() - cache.lastModified() > 24 * 3600 * 1000L) {
            runCatching {
                val conn = URI("https://celestrak.org/NORAD/elements/gp.php?GROUP=stations&FORMAT=tle").toURL().openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val bytes = conn.inputStream.use { it.readBytes() }
                if (Sgp4.parse(String(bytes)).isNotEmpty()) cache.writeBytes(bytes)
            }
        }
        if (!cache.exists()) return@withContext emptyList()
        Sgp4.parse(cache.readText()).filter { it.catalogNumber == "25544" || it.catalogNumber == "48274" } // ISS, Tiangong
    }

    private fun ask() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        state.guideAnswer = null
        state.guideListening = true
        recognizer?.startListening(intent)
    }

    private fun respond(heard: String) {
        state.guideHeard = heard
        val reply = AstroGuide.answer(heard, state, latestCatalog, latestMoving, latestEvents)
        state.guideAnswer = reply.speech
        tts?.language = if (heard.any { it in '\u0900'..'\u097F' }) Locale.forLanguageTag("hi-IN") else Locale.getDefault()
        tts?.speak(reply.speech, TextToSpeech.QUEUE_FLUSH, null, "astroguide")
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
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
