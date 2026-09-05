package com.codezmr.nullflow.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.codezmr.nullflow.data.BlockedApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The "Distraction Radar" — a custom-drawn hexagonal Canvas graph that maps the
 * top 6 most-intercepted apps to the vertices of a hexagon.
 *
 *  - Base web: concentric hexagons + radial spokes in muted #1E222B.
 *  - Data polygon: filled with #00E5FF at 0.3 alpha + a glowing cyan stroke.
 *  - Data nodes: 4dp cyan circles at each vertex.
 *  - Tactile scrubbing: dragging across a node fires a haptic tick and reveals
 *    a floating label with the exact interception count.
 *
 * Data is normalized against the highest intercepted count (the max app sits on
 * the outer edge = 100%).
 */
@Composable
fun FocusRadarGraph(
    apps: List<BlockedApp>,
    modifier: Modifier = Modifier,
    /** Called when the user scrubs a node (for the floating label). */
    onScrub: (Int) -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // The 6 vertex values (pad with zeros if fewer than 6 apps).
    val vertices = remember(apps) {
        val values = apps.take(6).map { it.deflectedCount.toFloat() }.toMutableList()
        while (values.size < 6) values.add(0f)
        values
    }
    val maxValue = remember(vertices) { vertices.maxOrNull()?.takeIf { it > 0f } ?: 1f }

    // Animated "reveal" — the data polygon scales from center on first draw.
    val revealAnim by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 900),
        label = "radarReveal"
    )

    // Which node is currently scrubbed (-1 = none). Drives the floating label.
    var scrubbedNode by remember { mutableIntStateOf(-1) }
    // Last drag X position (for the floating label offset).
    var dragX by remember { mutableFloatStateOf(0f) }
    // Canvas size (captured from the draw scope for the floating label math).
    var canvasSize by remember { mutableFloatStateOf(0f) }

    // Precompute the 6 unit vectors (angles) for the hexagon.
    val angles = remember {
        (0 until 6).map { i -> -Math.PI / 2 + i * (2 * Math.PI / 6) }
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(vertices) {
                    // Convert the pointer-input IntSize to a Float Size for the
                    // nearest-node math.
                    val pointerSize = Size(size.width.toFloat(), size.height.toFloat())
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragX = offset.x
                            val node = nearestNode(offset, pointerSize, vertices, maxValue, angles)
                            if (node >= 0) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scrubbedNode = node
                                onScrub(node)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragX = change.position.x
                            val node = nearestNode(change.position, pointerSize, vertices, maxValue, angles)
                            if (node != scrubbedNode) {
                                // Crossing into a new node → haptic tick.
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scrubbedNode = node
                                onScrub(node)
                            }
                        },
                        onDragEnd = {
                            // Keep the label visible briefly, then reset.
                            scope.launch {
                                delay(1200)
                                scrubbedNode = -1
                            }
                        },
                        onDragCancel = { scrubbedNode = -1 }
                    )
                }
        ) {
            canvasSize = size.width
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = minOf(size.width, size.height) / 2f * 0.82f

            // ---- 1) Base web: concentric hexagons (3 rings) + radial spokes ----
            val webColor = Color(0xFF1E222B)
            for (ring in 1..3) {
                val ringRadius = radius * ring / 3f
                drawHexagon(cx, cy, ringRadius, angles, color = webColor, strokeWidth = 1.5f)
            }
            for (angle in angles) {
                val ex = cx + (radius * cos(angle)).toFloat()
                val ey = cy + (radius * sin(angle)).toFloat()
                drawLine(
                    color = webColor,
                    start = Offset(cx, cy),
                    end = Offset(ex, ey),
                    strokeWidth = 1.5f
                )
            }

            // ---- 2) Data polygon (normalized, animated reveal) ----
            val dataPoints = vertices.mapIndexed { i, value ->
                val norm = (value / maxValue).coerceIn(0f, 1f) * revealAnim
                val r = radius * norm
                Offset(
                    x = cx + (r * cos(angles[i])).toFloat(),
                    y = cy + (r * sin(angles[i])).toFloat()
                )
            }
            if (revealAnim > 0.01f) {
                val dataPath = Path().apply {
                    dataPoints.forEachIndexed { i, p ->
                        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                    close()
                }
                // Glow: a wider, low-alpha stroke behind the main stroke.
                drawPath(
                    path = dataPath,
                    color = Color(0xFF00E5FF).copy(alpha = 0.18f),
                    style = Stroke(width = 10f, cap = StrokeCap.Round)
                )
                // Main glowing stroke.
                drawPath(
                    path = dataPath,
                    color = Color(0xFF00E5FF).copy(alpha = 0.9f),
                    style = Stroke(width = 3f, cap = StrokeCap.Round)
                )
                // Fill.
                drawPath(
                    path = dataPath,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00E5FF).copy(alpha = 0.3f),
                            Color(0xFF00E5FF).copy(alpha = 0.08f)
                        ),
                        center = Offset(cx, cy),
                        radius = radius
                    )
                )

                // ---- 3) Data nodes: 4dp cyan circles ----
                val nodeRadius = 4.dp.toPx()
                dataPoints.forEachIndexed { i, p ->
                    if (vertices[i] > 0f) {
                        drawCircle(
                            color = Color(0xFF00E5FF),
                            radius = nodeRadius,
                            center = p
                        )
                        // Subtle halo around each node.
                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = 0.25f),
                            radius = nodeRadius * 2f,
                            center = p
                        )
                    }
                }
            }
        }

        // ---- 4) Floating scrub label ----
        if (scrubbedNode in 0 until vertices.size && vertices[scrubbedNode] > 0f && canvasSize > 0f) {
            val labelX = (dragX / canvasSize).coerceIn(0.15f, 0.85f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = "${vertices[scrubbedNode].toInt()} deflected",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF00E5FF),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset {
                            IntOffset(
                                (labelX * canvasSize - 60).toInt(),
                                8.dp.roundToPx()
                            )
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0A0C10).copy(alpha = 0.9f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/**
 * Draw a regular hexagon centered at (cx, cy) with the given radius, using the
 * provided 6 angles.
 */
private fun DrawScope.drawHexagon(
    cx: Float,
    cy: Float,
    radius: Float,
    angles: List<Double>,
    color: Color,
    strokeWidth: Float
) {
    val path = Path().apply {
        angles.forEachIndexed { i, angle ->
            val x = cx + (radius * cos(angle)).toFloat()
            val y = cy + (radius * sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
}

/**
 * Find the index of the nearest data node to a pointer position, or -1 if the
 * pointer is too far from any node (outside the scrub threshold).
 */
private fun nearestNode(
    position: Offset,
    canvasSize: Size,
    vertices: List<Float>,
    maxValue: Float,
    angles: List<Double>
): Int {
    val cx = canvasSize.width / 2f
    val cy = canvasSize.height / 2f
    val radius = minOf(canvasSize.width, canvasSize.height) / 2f * 0.82f
    // Fixed scrub threshold in px (~28dp at typical density).
    val threshold = 80f

    var bestIdx = -1
    var bestDist = Float.MAX_VALUE
    for (i in vertices.indices) {
        if (vertices[i] <= 0f) continue // no data → no node to scrub
        val norm = (vertices[i] / maxValue).coerceIn(0f, 1f)
        val r = radius * norm
        val nx = cx + (r * cos(angles[i])).toFloat()
        val ny = cy + (r * sin(angles[i])).toFloat()
        val dist = hypot((position.x - nx).toDouble(), (position.y - ny).toDouble()).toFloat()
        if (dist < threshold && dist < bestDist) {
            bestDist = dist
            bestIdx = i
        }
    }
    return bestIdx
}
