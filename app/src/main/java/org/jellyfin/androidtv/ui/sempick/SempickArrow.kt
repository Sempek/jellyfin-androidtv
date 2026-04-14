package org.jellyfin.androidtv.ui.sempick

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Arrow colors matching the web version:
//   Right (2) = Blue,  Down (3) = Yellow,  Left (0) = Red,  Up (1) = Green
private val ARROW_COLORS = listOf(
    Color(0xFFDD2222), // 0 Left  — red
    Color(0xFF22AA22), // 1 Up    — green
    Color(0xFF2244DD), // 2 Right — blue
    Color(0xFFDDCC00), // 3 Down  — yellow
)

private val ARROW_ROTATIONS = listOf(180f, 270f, 0f, 90f)

/**
 * A single directional arrow drawn with Canvas, matching the SVG polygon from the web app:
 *   viewBox="0,-1 1 2"  points="0,-1 1,0 0,1 .6,0"
 * which is a right-pointing chevron arrow, rotated to the appropriate direction.
 */
@Composable
fun SempickArrow(
    direction: Int,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    val fillColor = ARROW_COLORS.getOrElse(direction) { Color.Gray }
    val rotation = ARROW_ROTATIONS.getOrElse(direction) { 0f }

    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width
        // Polygon in unit-square space mapped to canvas:
        //   viewBox "0,-1 1 2" → normalize y: y_canvas = (y_svg + 1) / 2 * s
        //   (0,-1)→(0,0)  (1,0)→(s,s/2)  (0,1)→(0,s)  (0.6,0)→(0.6s,s/2)
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(s, s * 0.5f)
            lineTo(0f, s)
            lineTo(s * 0.6f, s * 0.5f)
            close()
        }

        rotate(degrees = rotation, pivot = Offset(s * 0.5f, s * 0.5f)) {
            drawPath(path, color = fillColor, style = Fill)
            drawPath(path, color = Color.Black, style = Stroke(width = s * 0.08f))
        }
    }
}

/**
 * Renders a directional arrow sequence wrapped into rows of [rowSize] arrows.
 * e.g. "023102" with rowSize=3 → row1: [←][→][↓], row2: [←][↑][→]
 */
@Composable
fun SempickArrowSequence(
    sequence: String,
    arrowSize: Dp = 16.dp,
    rowSize: Int = 3,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        sequence.chunked(rowSize).forEach { chunk ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                chunk.forEach { digit ->
                    SempickArrow(direction = digit.digitToInt(), size = arrowSize)
                }
            }
        }
    }
}
