package org.astrofixxer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/*
 * Small line drawings for the setup wizard and the telescope settings. They use only the theme's colours (so night
 * mode stays red on black) and are drawn on a unit square: (0,0) top left, (1,1) bottom right. In the side views the
 * front (sky end) of the telescope is on the right.
 */

private class Ink(val line: Color, val accent: Color, val faint: Color)

@Composable
private fun ink() = MaterialTheme.colorScheme.let { Ink(it.onSurface, it.primary, it.outline) }

private fun DrawScope.at(x: Float, y: Float) = Offset(x * size.width, y * size.height)
private fun DrawScope.sz(w: Float, h: Float) = Size(w * size.width, h * size.height)
private fun DrawScope.stroke(w: Float = 2.2f) = Stroke(w * density / 2f)

private fun DrawScope.box(x: Float, y: Float, w: Float, h: Float, color: Color, corner: Float = 0.02f, width: Float = 2.2f) =
    drawRoundRect(color, at(x, y), sz(w, h), CornerRadius(corner * size.width), style = stroke(width))

private fun DrawScope.seg(a: Offset, b: Offset, color: Color, width: Float = 2.2f) = drawLine(color, a, b, strokeWidth = width * density / 2f)

/** An arrow from [a] to [b] with a head at [b]. */
private fun DrawScope.arrow(a: Offset, b: Offset, color: Color, width: Float = 2.6f) {
    seg(a, b, color, width)
    val d = b - a
    val len = kotlin.math.hypot(d.x, d.y).coerceAtLeast(1e-3f)
    val ux = d.x / len; val uy = d.y / len
    val head = size.width * 0.07f
    seg(b, Offset(b.x - ux * head - uy * head * 0.6f, b.y - uy * head + ux * head * 0.6f), color, width)
    seg(b, Offset(b.x - ux * head + uy * head * 0.6f, b.y - uy * head - ux * head * 0.6f), color, width)
}

/** The telescope tube in side view for [type], with its eyepiece. */
private fun DrawScope.telescope(type: TelescopeType, ink: Ink, cy: Float = 0.5f, scale: Float = 1f) {
    when (type) {
        TelescopeType.REFRACTOR -> {
            box(0.16f, cy - 0.07f * scale, 0.66f, 0.14f * scale, ink.line)
            box(0.82f, cy - 0.09f * scale, 0.12f, 0.18f * scale, ink.line) // dew shield at the front
            box(0.06f, cy - 0.035f * scale, 0.10f, 0.07f * scale, ink.line) // focuser and eyepiece, straight in
        }
        TelescopeType.REFLECTOR -> {
            box(0.14f, cy - 0.12f * scale, 0.76f, 0.24f * scale, ink.line)
            box(0.68f, cy - 0.20f * scale, 0.06f, 0.08f * scale, ink.line) // eyepiece on the side near the front
        }
        TelescopeType.OTHER -> {
            box(0.20f, cy - 0.15f * scale, 0.60f, 0.30f * scale, ink.line)
            box(0.10f, cy - 0.05f * scale, 0.10f, 0.10f * scale, ink.line)
            seg(at(0.15f, cy + 0.05f * scale), at(0.15f, cy + 0.14f * scale), ink.line)
            box(0.12f, cy + 0.14f * scale, 0.06f, 0.04f * scale, ink.line)
        }
    }
}

@Composable
fun TelescopeTypeDrawing(type: TelescopeType, modifier: Modifier) {
    val ink = ink()
    Canvas(modifier.semantics { contentDescription = type.name }) { telescope(type, ink, 0.5f, 1.4f) }
}

@Composable
fun MountTypeDrawing(mount: MountType, modifier: Modifier) {
    val ink = ink()
    Canvas(modifier.semantics { contentDescription = mount.name }) {
        when (mount) {
            MountType.ALT_AZ -> { // tube in a fork on a base
                rotate(-28f, at(0.5f, 0.42f)) { box(0.16f, 0.36f, 0.68f, 0.12f, ink.line) }
                seg(at(0.38f, 0.42f), at(0.38f, 0.78f), ink.accent)
                seg(at(0.62f, 0.42f), at(0.62f, 0.78f), ink.accent)
                seg(at(0.38f, 0.78f), at(0.62f, 0.78f), ink.accent)
                seg(at(0.30f, 0.90f), at(0.70f, 0.90f), ink.accent)
                seg(at(0.5f, 0.78f), at(0.5f, 0.90f), ink.accent)
            }
            MountType.EQUATORIAL -> { // tilted polar axis with a counterweight
                rotate(-28f, at(0.5f, 0.30f)) { box(0.18f, 0.24f, 0.64f, 0.11f, ink.line) }
                seg(at(0.62f, 0.40f), at(0.40f, 0.68f), ink.accent, 3.2f) // polar axis
                seg(at(0.40f, 0.68f), at(0.24f, 0.60f), ink.accent) // counterweight bar
                drawCircle(ink.accent, size.width * 0.06f, at(0.22f, 0.59f), style = stroke())
                seg(at(0.5f, 0.55f), at(0.5f, 0.90f), ink.accent)
                seg(at(0.30f, 0.90f), at(0.70f, 0.90f), ink.accent)
            }
            MountType.OTHER -> { // a plain tripod
                rotate(-28f, at(0.5f, 0.32f)) { box(0.20f, 0.26f, 0.60f, 0.11f, ink.line) }
                seg(at(0.5f, 0.45f), at(0.32f, 0.90f), ink.accent)
                seg(at(0.5f, 0.45f), at(0.68f, 0.90f), ink.accent)
                seg(at(0.5f, 0.45f), at(0.5f, 0.90f), ink.accent)
            }
        }
    }
}

/** A phone from the back or side, as a rounded rectangle, with an optional camera dot. */
private fun DrawScope.phone(x: Float, y: Float, w: Float, h: Float, ink: Ink, camera: Boolean = false, filled: Boolean = false) {
    if (filled) drawRoundRect(ink.faint.copy(alpha = 0.35f), at(x, y), sz(w, h), CornerRadius(0.03f * size.width))
    box(x, y, w, h, ink.accent, 0.03f, 2.8f)
    if (camera) drawCircle(ink.accent, size.width * 0.025f, at(x + w * 0.5f, y + h * 0.12f))
}

@Composable
fun PlacementDrawing(placement: PhonePlacement, modifier: Modifier) {
    val ink = ink()
    Canvas(modifier.semantics { contentDescription = placement.name }) {
        when (placement) {
            PhonePlacement.TUBE -> { // phone lying on the tube, seen from above
                box(0.06f, 0.36f, 0.88f, 0.28f, ink.line)
                phone(0.30f, 0.40f, 0.36f, 0.20f, ink, filled = true)
                arrow(at(0.70f, 0.50f), at(0.94f, 0.50f), ink.accent)
            }
            PhonePlacement.CAMERA_FORWARD -> { // phone beside the tube, its camera looking the way the tube points
                box(0.06f, 0.55f, 0.70f, 0.24f, ink.line)
                phone(0.70f, 0.18f, 0.14f, 0.30f, ink, camera = true, filled = true)
                arrow(at(0.77f, 0.20f), at(0.77f, 0.02f), ink.accent)
            }
            PhonePlacement.EYEPIECE -> { // phone on the eyepiece, camera looking into it
                box(0.34f, 0.36f, 0.62f, 0.28f, ink.line)
                box(0.24f, 0.44f, 0.10f, 0.12f, ink.line)
                phone(0.08f, 0.24f, 0.10f, 0.52f, ink, camera = false, filled = true)
                drawCircle(ink.accent, size.width * 0.025f, at(0.18f, 0.5f))
                arrow(at(0.24f, 0.5f), at(0.32f, 0.5f), ink.accent, 2.2f)
            }
        }
    }
}

/** A phone seen from the front (portrait) with the chosen [edge] marked and an arrow pointing out of it, toward the telescope's front. */
@Composable
fun PhoneEdgeDrawing(edge: PhoneEdge, modifier: Modifier) {
    val ink = ink()
    Canvas(modifier.semantics { contentDescription = edge.name }) {
        phone(0.36f, 0.16f, 0.28f, 0.68f, ink)
        box(0.39f, 0.22f, 0.22f, 0.50f, ink.faint, 0.01f, 1.2f) // screen
        val (a, b, out) = when (edge) {
            PhoneEdge.TOP -> Triple(at(0.36f, 0.16f), at(0.64f, 0.16f), Offset(0f, -0.12f))
            PhoneEdge.BOTTOM -> Triple(at(0.36f, 0.84f), at(0.64f, 0.84f), Offset(0f, 0.12f))
            PhoneEdge.LEFT -> Triple(at(0.36f, 0.16f), at(0.36f, 0.84f), Offset(-0.12f, 0f))
            PhoneEdge.RIGHT -> Triple(at(0.64f, 0.16f), at(0.64f, 0.84f), Offset(0.12f, 0f))
        }
        seg(a, b, ink.accent, 6f)
        val mid = (a + b) / 2f
        arrow(mid + Offset(out.x * size.width * 0.3f, out.y * size.height * 0.3f), mid + Offset(out.x * size.width, out.y * size.height), ink.accent)
    }
}

@Composable
fun EyepieceAngleDrawing(angle: EyepieceAngle, modifier: Modifier) {
    val ink = ink()
    Canvas(modifier.semantics { contentDescription = angle.name }) {
        box(0.30f, 0.52f, 0.64f, 0.20f, ink.line)
        when (angle) {
            EyepieceAngle.STRAIGHT -> box(0.10f, 0.57f, 0.20f, 0.10f, ink.accent, 0.02f, 3f)
            EyepieceAngle.RIGHT_ANGLE -> { // a star diagonal or a Newtonian's side eyepiece
                box(0.26f, 0.48f, 0.08f, 0.06f, ink.accent, 0.01f, 3f)
                seg(at(0.30f, 0.48f), at(0.30f, 0.26f), ink.accent, 3f)
                box(0.24f, 0.14f, 0.12f, 0.12f, ink.accent, 0.02f, 3f)
            }
            EyepieceAngle.UNSURE -> {}
        }
    }
}

/** The light path's end: a prism box for "yes", a plain eyepiece for "no". */
@Composable
fun ErectingDrawing(erecting: Erecting, modifier: Modifier) {
    val ink = ink()
    Canvas(modifier.semantics { contentDescription = erecting.name }) {
        box(0.08f, 0.40f, 0.20f, 0.20f, ink.line) // eyepiece
        if (erecting == Erecting.YES) {
            val p = Path().apply { moveTo(at(0.34f, 0.32f).x, at(0.34f, 0.32f).y); lineTo(at(0.34f, 0.68f).x, at(0.34f, 0.68f).y); lineTo(at(0.62f, 0.5f).x, at(0.62f, 0.5f).y); close() }
            drawPath(p, ink.accent, style = stroke(3f))
        }
        seg(at(0.28f, 0.5f), at(0.92f, 0.5f), ink.faint)
    }
}

/**
 * The telescope with the phone on it, as set up: type, where the phone sits, and the axis the app treats as
 * "where the telescope points" (an arrow toward the front of the telescope).
 */
@Composable
fun MountingPreview(setup: TelescopeSetup, modifier: Modifier) {
    val ink = ink()
    Canvas(modifier.semantics { contentDescription = "Mounting preview" }) {
        telescope(setup.type, ink, 0.55f, 1.25f)
        when (setup.placement) {
            PhonePlacement.TUBE -> phone(0.34f, 0.44f, 0.30f, 0.14f, ink, filled = true)
            PhonePlacement.CAMERA_FORWARD -> phone(0.66f, 0.10f, 0.12f, 0.24f, ink, camera = true, filled = true)
            PhonePlacement.EYEPIECE -> phone(0.02f, 0.30f, 0.07f, 0.44f, ink, filled = true)
        }
        arrow(at(0.60f, 0.90f), at(0.94f, 0.90f), ink.accent) // toward the front (sky end)
    }
}

/** Plain-words description of a view orientation for the settings and the check. */
fun describeView(rotationDeg: Int, mirrored: Boolean): String {
    val parts = listOfNotNull(
        if (mirrored) t("mirrored") else null,
        if (rotationDeg != 0) t("rotated %d°").format(rotationDeg) else null,
    )
    return if (parts.isEmpty()) t("upright") else parts.joinToString(" + ")
}
