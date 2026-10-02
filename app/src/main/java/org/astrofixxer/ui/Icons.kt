package org.astrofixxer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Icons drawn in code: the Material icon set is not a dependency of the app. */
internal object AppIcons {
    /** A settings gear on a 24 × 24 grid: eight teeth round a ring with a round hole in the middle. Tinted by whoever draws it. */
    val Gear: ImageVector by lazy {
        val cx = 12f; val cy = 12f
        val body = 7.4f; val tip = 10.6f; val hole = 3.3f
        val teeth = 8
        fun x(r: Float, deg: Float) = cx + r * cos(Math.toRadians(deg.toDouble())).toFloat()
        fun y(r: Float, deg: Float) = cy + r * sin(Math.toRadians(deg.toDouble())).toFloat()
        ImageVector.Builder("Gear", 24.dp, 24.dp, 24f, 24f).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            for (i in 0 until teeth) {
                val a = i * 360f / teeth
                // Up the side of the tooth, across its flat tip, down the other side, then along the ring to the next tooth.
                if (i == 0) moveTo(x(body, a - 14f), y(body, a - 14f)) else lineTo(x(body, a - 14f), y(body, a - 14f))
                lineTo(x(tip, a - 9f), y(tip, a - 9f))
                lineTo(x(tip, a + 9f), y(tip, a + 9f))
                lineTo(x(body, a + 14f), y(body, a + 14f))
                lineTo(x(body, a + 22.5f), y(body, a + 22.5f))
            }
            close()
            // The hole (even-odd fill leaves it empty).
            moveTo(cx + hole, cy)
            arcTo(hole, hole, 0f, true, false, cx - hole, cy)
            arcTo(hole, hole, 0f, true, false, cx + hole, cy)
            close()
        }.build()
    }
}
