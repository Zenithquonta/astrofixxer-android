package org.astrofixxer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.astrofixxer.astro.Catalog
import org.astrofixxer.astro.CatalogStars
import org.astrofixxer.astro.GrayImage
import org.astrofixxer.astro.PlateSolveHints
import org.astrofixxer.astro.PlateSolver
import org.astrofixxer.astro.SkyObject
import org.astrofixxer.astro.SkyStar
import org.astrofixxer.astro.SolveHints
import org.astrofixxer.astro.SolvePlan
import org.astrofixxer.astro.SolveResult
import org.astrofixxer.astro.StarSource
import kotlin.coroutines.coroutineContext

/** The steps of the guided camera plate-solve flow, in order. */
internal enum class SolveStep { ARRANGEMENT, PICK_STAR, TIPS, LIVE, SOLVING, RESULT }

/** What the solver made of a photo. Only [Solved] ever changes anything, and only when the person applies it. */
internal sealed class SolveOutcome(val plan: SolvePlan.Run?) {
    class Solved(val result: SolveResult.Solved, plan: SolvePlan.Run, val seconds: Double) : SolveOutcome(plan)
    class Failed(val reason: SolveResult.Reason, val detectedStars: Int, plan: SolvePlan.Run) : SolveOutcome(plan)
    /** Not run: a very narrow field with nothing to say roughly where the telescope points. */
    class NoHint(val fieldWidthDeg: Double, plan: SolvePlan.Run? = null) : SolveOutcome(plan)
    /** The solver could not run on this picture (a damaged star list, an odd picture). */
    class Broke : SolveOutcome(null)
}

/**
 * The state of the flow: which step is showing, the photo, the outcome. It never touches the alignment or the sky screen;
 * the flow does that only when the person presses Apply, Save or Show on map after a [SolveOutcome.Solved].
 * [solver] is the plate solver; tests may replace it.
 */
class PlateSolveModel internal constructor(internal val solver: (GrayImage, SolveHints, StarSource) -> SolveResult) {
    constructor() : this(PlateSolver::solve)

    var open by mutableStateOf(false)
        private set
    internal var step by mutableStateOf(SolveStep.ARRANGEMENT)
    /** Set while the camera offset is being calibrated: the star the person centres in the eyepiece. */
    internal var calibrating by mutableStateOf<SkyObject?>(null)
    internal var shot by mutableStateOf<PhotoShot?>(null)
    internal var outcome by mutableStateOf<SolveOutcome?>(null)
    /** Manual exposure in seconds, or null for automatic. */
    internal var exposureSec by mutableStateOf<Double?>(null)
    /** A message from the last button press (for example why Apply failed), shown on the current step. */
    internal var message by mutableStateOf<AlignNote?>(null)

    fun start() {
        step = SolveStep.ARRANGEMENT
        calibrating = null; shot = null; outcome = null; message = null
        open = true
    }

    fun close() {
        open = false
        shot = null; outcome = null; message = null; calibrating = null
    }

    /** One step back (the system Back button); from the first step it closes the flow. */
    internal fun back() {
        message = null
        when (step) {
            SolveStep.ARRANGEMENT -> close()
            SolveStep.PICK_STAR -> { calibrating = null; step = SolveStep.ARRANGEMENT }
            SolveStep.TIPS -> step = if (calibrating != null) SolveStep.PICK_STAR else SolveStep.ARRANGEMENT
            SolveStep.LIVE -> step = SolveStep.TIPS
            SolveStep.SOLVING, SolveStep.RESULT -> { outcome = null; step = SolveStep.LIVE }
        }
    }

    /** Solves [shot] with the settings the setup and the phone allow. Off the UI thread; cancelling the coroutine stops the search. */
    internal suspend fun solve(state: SkyState, catalog: Catalog?, host: PlateSolveHost, shot: PhotoShot): SolveOutcome = withContext(Dispatchers.Default) {
        val job = coroutineContext[Job]!!
        val centre = state.pointingRaDec(shot.device, shot.timeMillis)
        val plan = PlateSolveHints.plan(state.setup.placement, state.telescopeFocalMm, state.eyepieceFocalMm, shot.hFovDeg, shot.image.width,
            centre, aligned = state.alignMatrix != null, hasCompass = state.hasCompass)
        when (plan) {
            is SolvePlan.Refused -> SolveOutcome.NoHint(PlateSolveHints.nominalWidthDeg(state.setup.placement, state.telescopeFocalMm, state.eyepieceFocalMm, shot.hFovDeg))
            is SolvePlan.Run -> {
                val source: StarSource? = (if (plan.deepStars) host.deepStars() else null) ?: catalog?.let { CatalogStars(it) }
                if (source == null) return@withContext SolveOutcome.Broke()
                val started = System.nanoTime()
                val result = try {
                    solver(shot.image, plan.hints, Cancellable(source, job))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@withContext SolveOutcome.Broke()
                }
                when (result) {
                    is SolveResult.Failed -> SolveOutcome.Failed(result.reason, result.detectedStars, plan)
                    is SolveResult.Solved ->
                        // A last look before anything is believed: finite numbers, and the picture the solver saw.
                        if (listOf(result.raDeg, result.decDeg, result.scaleDegPerPx, result.rollDeg).all { it.isFinite() } &&
                            result.imageWidth == shot.image.width && result.imageHeight == shot.image.height)
                            SolveOutcome.Solved(result, plan, (System.nanoTime() - started) / 1e9)
                        else SolveOutcome.Broke()
                }
            }
        }
    }

    /** A star source that stops the search as soon as the coroutine is cancelled (the solver has no cancel button of its own). */
    private class Cancellable(private val inner: StarSource, private val job: Job) : StarSource {
        override fun cone(raDeg: Double, decDeg: Double, radiusDeg: Double, magLimit: Double): List<SkyStar> {
            job.ensureActive()
            return inner.cone(raDeg, decDeg, radiusDeg, magLimit)
        }
    }
}
