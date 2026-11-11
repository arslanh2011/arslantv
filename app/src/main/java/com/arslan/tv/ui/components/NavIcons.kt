package com.arslan.tv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The navigation glyphs, drawn by hand.
 *
 * An icon font or the material-icons artifact would pull a few hundred KB into
 * an app whose whole release build is a couple of megabytes, and four shapes
 * are not worth that.
 */
enum class NavIcon { SEARCH, HOME, NEWS, SPORTS, DRAMA, MUSIC, FILM, STAR, SETTINGS }

@Composable
fun NavGlyph(icon: NavIcon, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = minOf(size.width, size.height)
        val w = s * 0.09f
        val stroke = Stroke(width = w)
        val pad = s * 0.18f
        val inner = s - pad * 2

        when (icon) {
            NavIcon.SEARCH -> {
                val r = inner * 0.33f
                drawCircle(tint, radius = r, center = Offset(pad + r * 1.1f, pad + r * 1.1f), style = stroke)
                drawLine(
                    tint,
                    Offset(pad + r * 1.95f, pad + r * 1.95f),
                    Offset(pad + inner * 0.95f, pad + inner * 0.95f),
                    strokeWidth = w,
                )
            }

            NavIcon.HOME -> {
                val path = Path().apply {
                    moveTo(pad, pad + inner * 0.45f)
                    lineTo(pad + inner / 2f, pad)
                    lineTo(pad + inner, pad + inner * 0.45f)
                    lineTo(pad + inner, pad + inner)
                    lineTo(pad, pad + inner)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }

            // Stacked bars - a list of headlines.
            NavIcon.NEWS -> repeat(3) { i ->
                val y = pad + inner * (0.15f + i * 0.33f)
                drawLine(tint, Offset(pad, y), Offset(pad + inner * (if (i == 2) 0.6f else 1f), y), strokeWidth = w)
            }

            // A ball.
            NavIcon.SPORTS -> {
                drawCircle(tint, radius = inner / 2f, center = Offset(pad + inner / 2f, pad + inner / 2f), style = stroke)
                drawLine(tint, Offset(pad, pad + inner / 2f), Offset(pad + inner, pad + inner / 2f), strokeWidth = w * 0.8f)
            }

            // A theatre mask outline.
            NavIcon.DRAMA -> {
                drawCircle(tint, radius = inner / 2f, center = Offset(pad + inner / 2f, pad + inner / 2f), style = stroke)
                drawCircle(tint, radius = w * 0.6f, center = Offset(pad + inner * 0.33f, pad + inner * 0.38f))
                drawCircle(tint, radius = w * 0.6f, center = Offset(pad + inner * 0.67f, pad + inner * 0.38f))
                drawLine(tint, Offset(pad + inner * 0.3f, pad + inner * 0.7f), Offset(pad + inner * 0.7f, pad + inner * 0.7f), strokeWidth = w * 0.8f)
            }

            // A quaver.
            NavIcon.MUSIC -> {
                drawCircle(tint, radius = inner * 0.17f, center = Offset(pad + inner * 0.3f, pad + inner * 0.78f), style = stroke)
                drawLine(tint, Offset(pad + inner * 0.47f, pad + inner * 0.78f), Offset(pad + inner * 0.47f, pad + inner * 0.12f), strokeWidth = w)
                drawLine(tint, Offset(pad + inner * 0.47f, pad + inner * 0.12f), Offset(pad + inner * 0.9f, pad + inner * 0.26f), strokeWidth = w)
            }

            // A film frame.
            NavIcon.FILM -> {
                drawRect(tint, topLeft = Offset(pad, pad + inner * 0.12f), size = Size(inner, inner * 0.76f), style = stroke)
                repeat(3) { i ->
                    val x = pad + inner * (0.25f + i * 0.25f)
                    drawLine(tint, Offset(x, pad + inner * 0.12f), Offset(x, pad + inner * 0.88f), strokeWidth = w * 0.7f)
                }
            }

            // A simple four-point star.
            NavIcon.STAR -> {
                val c = Offset(pad + inner / 2f, pad + inner / 2f)
                val path = Path().apply {
                    moveTo(c.x, pad)
                    lineTo(c.x + inner * 0.16f, c.y - inner * 0.16f)
                    lineTo(pad + inner, c.y)
                    lineTo(c.x + inner * 0.16f, c.y + inner * 0.16f)
                    lineTo(c.x, pad + inner)
                    lineTo(c.x - inner * 0.16f, c.y + inner * 0.16f)
                    lineTo(pad, c.y)
                    lineTo(c.x - inner * 0.16f, c.y - inner * 0.16f)
                    close()
                }
                drawPath(path, tint)
            }

            NavIcon.SETTINGS -> {
                val c = Offset(pad + inner / 2f, pad + inner / 2f)
                drawCircle(tint, radius = inner * 0.26f, center = c, style = stroke)
                repeat(6) { i ->
                    val a = Math.toRadians(i * 60.0)
                    val dx = kotlin.math.cos(a).toFloat()
                    val dy = kotlin.math.sin(a).toFloat()
                    drawLine(
                        tint,
                        Offset(c.x + dx * inner * 0.34f, c.y + dy * inner * 0.34f),
                        Offset(c.x + dx * inner * 0.5f, c.y + dy * inner * 0.5f),
                        strokeWidth = w,
                    )
                }
            }
        }
    }
}
