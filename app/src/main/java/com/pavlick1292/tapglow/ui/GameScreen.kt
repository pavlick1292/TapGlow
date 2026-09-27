package com.pavlick1292.tapglow.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ---------- Фазы раунда ----------
private enum class Phase { Idle, Intrigue, WaitTap, Result }

// ---------- Тип фигуры ----------
private enum class ShapeType { Circle, Square, Triangle, Hexagon }

// ---------- Описание фигуры ----------
private data class ShapeItem(
    val type: ShapeType,
    val hue: Hue,
    val offsetX: Float,
    val offsetY: Float
)

// ---------- Базовые оттенки фигур (чтобы различать) ----------
private enum class Hue { Purple, Cyan, Amber, Pink, Green, Blue }

// ---------- Палитры для каждого оттенка (top, mid, bottom, bounce) ----------
private data class Palette(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val bounce: Color
)

private fun paletteOf(hue: Hue, highlight: Boolean, wrong: Boolean, right: Boolean): Palette {
    if (wrong) return Palette(
        top = Color(0xFFFFB3AD),
        mid = Color(0xFFE53935),
        bottom = Color(0xFF5A0000),
        bounce = Color(0xFFFF6E5A)
    )
    if (right) return Palette(
        top = Color(0xFFFFF9C4),
        mid = Color(0xFFFFD54F),
        bottom = Color(0xFF8A6300),
        bounce = Color(0xFFFFE082)
    )
    if (highlight) return Palette(
        top = Color(0xFFFFFFFF),
        mid = Color(0xFFFFF176),
        bottom = Color(0xFF8A6300),
        bounce = Color(0xFFFFECB3)
    )
    return when (hue) {
        Hue.Purple -> Palette(Color(0xFFD6C6FF), Color(0xFF7E57C2), Color(0xFF2E1065), Color(0xFF5B2E91))
        Hue.Cyan -> Palette(Color(0xFFB3F5FF), Color(0xFF26C6DA), Color(0xFF004D57), Color(0xFF1E9EB0))
        Hue.Amber -> Palette(Color(0xFFFFE7B3), Color(0xFFFFA726), Color(0xFF6B3A00), Color(0xFFCC7A1A))
        Hue.Pink -> Palette(Color(0xFFFFC6E0), Color(0xFFEC407A), Color(0xFF6B0033), Color(0xFFB01E5A))
        Hue.Green -> Palette(Color(0xFFC8FFD0), Color(0xFF43A047), Color(0xFF0C3A12), Color(0xFF2E7D33))
        Hue.Blue -> Palette(Color(0xFFC6D8FF), Color(0xFF3F51B5), Color(0xFF0A1246), Color(0xFF2A3A8C))
    }
}

// ---------- Константы ----------
private const val IDLE_DURATION_MS = 700L
private const val RESULT_SHOW_MS = 1200L
private const val MAX_SHAPES = 8 // подняли лимит: было 5

private fun tapTimeoutMs(level: Int): Long {
    val reduction = (level - 1) / 3 * 200L
    return (3000L - reduction).coerceAtLeast(800L)
}

private fun blinkMs(level: Int): Long {
    return (600L - (level - 1) * 25L).coerceAtLeast(220L)
}

private fun sequenceLength(level: Int): Int {
    return when {
        level <= 1 -> 2
        level <= 3 -> 3
        level <= 6 -> 4
        level <= 10 -> 5
        level <= 15 -> 6
        level <= 22 -> 7
        else -> 8
    }
}

@Composable
fun GameScreen() {
    var level by remember { mutableStateOf(1) }
    var phase by remember { mutableStateOf(Phase.Idle) }
    var highlightedIndex by remember { mutableStateOf(-1) }
    var targetIndex by remember { mutableStateOf(-1) }
    var tappedIndex by remember { mutableStateOf(-1) }
    var isWin by remember { mutableStateOf(false) }
    var timeLeftMs by remember { mutableStateOf(tapTimeoutMs(1)) }
    var resultFlash by remember { mutableStateOf(0f) }

    // Количество фигур: 2, +1 каждые 3 уровня, максимум MAX_SHAPES
    val shapeCount = (2 + (level - 1) / 3).coerceAtMost(MAX_SHAPES)
    val shapes = remember(shapeCount) { buildShapes(shapeCount) }

    var pulse by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            pulse = 0.5f + 0.5f * sin(System.currentTimeMillis() / 300.0).toFloat()
            delay(16L)
        }
    }
    var breath by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            breath = 0.5f + 0.5f * sin(System.currentTimeMillis() / 500.0).toFloat()
            delay(16L)
        }
    }

    fun startRound() {
        tappedIndex = -1
        isWin = false
        resultFlash = 0f
        highlightedIndex = -1
        timeLeftMs = tapTimeoutMs(level)
        targetIndex = -1
        phase = Phase.Idle
    }

    LaunchedEffect(level) { startRound() }

    LaunchedEffect(phase, level) {
        when (phase) {
            Phase.Idle -> {
                delay(IDLE_DURATION_MS)
                phase = Phase.Intrigue
            }
            Phase.Intrigue -> {
                val seqLen = sequenceLength(level)
                val seq = List(seqLen) { shapes.indices.random() }
                val blink = blinkMs(level)

                for (idx in seq) {
                    highlightedIndex = idx
                    delay(blink)
                    highlightedIndex = -1
                    delay(blink / 3)
                }

                targetIndex = seq.last()
                phase = Phase.WaitTap
            }
            Phase.WaitTap -> {
                val total = tapTimeoutMs(level)
                val start = System.currentTimeMillis()
                while (timeLeftMs > 0 && phase == Phase.WaitTap) {
                    delay(16L)
                    val elapsed = System.currentTimeMillis() - start
                    timeLeftMs = (total - elapsed).coerceAtLeast(0L)
                }
                if (phase == Phase.WaitTap) {
                    tappedIndex = -1
                    isWin = false
                    phase = Phase.Result
                }
            }
            Phase.Result -> {
                val start = System.currentTimeMillis()
                while (resultFlash < 1f) {
                    resultFlash = ((System.currentTimeMillis() - start) / 500f).coerceIn(0f, 1f)
                    delay(16L)
                }
                delay(RESULT_SHOW_MS)
                level += 1
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF05050C), Color(0xFF140F24), Color(0xFF05050C))
                )
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Уровень $level", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(Color(0xFF1A1A28), RoundedCornerShape(7.dp))
        ) {
            val total = tapTimeoutMs(level).toFloat()
            val frac = (timeLeftMs / total).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .height(14.dp)
                    .background(
                        Brush.verticalGradient(
                            if (frac > 0.3f)
                                listOf(Color(0xFF7BFFB0), Color(0xFF43E97B), Color(0xFF1E9E4F))
                            else
                                listOf(Color(0xFFFFA07A), Color(0xFFFF5F6D), Color(0xFF9E1E30))
                        ),
                        RoundedCornerShape(7.dp)
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .height(6.dp)
                    .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(7.dp))
            )
        }

        Spacer(Modifier.height(16.dp))

        val hint = when (phase) {
            Phase.Idle -> "Приготовься..."
            Phase.Intrigue -> "Запоминай последнюю!"
            Phase.WaitTap -> "Тапни ту, что мигала последней"
            Phase.Result -> if (isWin) "Угадал!" else "Промах!"
        }
        Text(hint, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(phase) {
                        if (phase != Phase.WaitTap) return@pointerInput
                        detectTapGestures { offset ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val cx = w / 2f
                            val cy = h / 2f
                            val radius = minOf(w, h) * 0.14f

                            shapes.forEachIndexed { index, shape ->
                                val sx = cx + shape.offsetX * w * 0.5f
                                val sy = cy + shape.offsetY * h * 0.5f
                                val dx = offset.x - sx
                                val dy = offset.y - sy
                                val inside = when (shape.type) {
                                    ShapeType.Circle, ShapeType.Hexagon ->
                                        dx * dx + dy * dy <= radius * radius
                                    ShapeType.Square ->
                                        abs(dx) <= radius && abs(dy) <= radius
                                    ShapeType.Triangle ->
                                        dy >= -radius && dy <= radius && abs(dx) <= radius * (1f - (dy + radius) / (2f * radius))
                                }
                                if (inside && phase == Phase.WaitTap) {
                                    tappedIndex = index
                                    isWin = index == targetIndex
                                    phase = Phase.Result
                                }
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f
                val radius = minOf(w, h) * 0.14f

                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color(0xF0000000)),
                        center = Offset(cx, cy),
                        radius = maxOf(w, h) * 0.85f
                    ),
                    size = size
                )

                val floorY = cy + radius * 1.9f
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0x88A78BFA),
                            Color(0x88A78BFA),
                            Color.Transparent
                        )
                    ),
                    topLeft = Offset(0f, floorY),
                    size = Size(w, 3f)
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0x44A78BFA), Color.Transparent)
                    ),
                    topLeft = Offset(0f, floorY),
                    size = Size(w, 80f)
                )

                shapes.forEachIndexed { index, shape ->
                    val sx = cx + shape.offsetX * w * 0.5f
                    val sy = cy + shape.offsetY * h * 0.5f

                    val isHighlighted = highlightedIndex == index
                    val isWrong = phase == Phase.Result && index == tappedIndex && !isWin
                    val isRight = phase == Phase.Result && index == targetIndex && isWin

                    val idleScale = if (phase == Phase.Idle) 1f + 0.06f * breath else 1f
                    val glowScale = if (isHighlighted) 1f + 0.14f * pulse else 1f
                    val resultScale = when {
                        phase == Phase.Result && isRight -> 1f + 0.32f * (1f - resultFlash)
                        phase == Phase.Result && isWrong -> 1f - 0.10f * (1f - resultFlash)
                        else -> 1f
                    }
                    val r = radius * idleScale * glowScale * resultScale

                    val pal = paletteOf(shape.hue, isHighlighted, isWrong, isRight)

                    // Внешний ореол
                    val haloAlpha = when {
                        isHighlighted -> 0.85f + 0.15f * pulse
                        isRight -> 0.9f
                        isWrong -> 0.85f
                        else -> 0.22f
                    }
                    val haloColor = when {
                        isWrong -> Color(0xFFFF1744)
                        isRight -> Color(0xFFFFEB3B)
                        isHighlighted -> Color(0xFFFFF176)
                        else -> pal.mid
                    }
                    val haloRadius = r * (1.7f + 0.3f * pulse)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                haloColor.copy(alpha = haloAlpha),
                                haloColor.copy(alpha = haloAlpha * 0.35f),
                                Color.Transparent
                            ),
                            center = Offset(sx, sy),
                            radius = haloRadius
                        ),
                        radius = haloRadius,
                        center = Offset(sx, sy)
                    )

                    if (isHighlighted) {
                        val angle = (System.currentTimeMillis() / 4.0).toFloat() % 360f
                        rotate(degrees = angle, pivot = Offset(sx, sy)) {
                            val rays = 12
                            for (i in 0 until rays) {
                                val a = (i * 360f / rays)
                                val rad = Math.toRadians(a.toDouble())
                                val rr = r * 1.85f
                                val ex = sx + (cos(rad).toFloat() * rr)
                                val ey = sy + (sin(rad).toFloat() * rr)
                                drawLine(
                                    color = Color(0xFFFFF176).copy(alpha = 0.5f + 0.3f * pulse),
                                    start = Offset(sx, sy),
                                    end = Offset(ex, ey),
                                    strokeWidth = 3.5f
                                )
                            }
                        }
                    }

                    // Тень
                    val shadowW = r * 2.1f
                    val shadowH = r * 0.5f
                    val shadowAlpha = if (isHighlighted) 0.85f else 0.55f
                    val shadowCY = sy + r * 1.35f
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = shadowAlpha),
                                Color.Transparent
                            ),
                            center = Offset(sx, shadowCY),
                            radius = shadowW * 0.6f
                        ),
                        topLeft = Offset(sx - shadowW / 2f, shadowCY - shadowH / 2f),
                        size = Size(shadowW, shadowH)
                    )

                    // Тёмное основание (объём-толщина)
                    val depth = r * 0.40f
                    val baseDark = pal.bottom
                    drawShape(
                        type = shape.type,
                        sx = sx, sy = sy + depth, r = r,
                        brush = Brush.verticalGradient(
                            colors = listOf(baseDark, Color(0xFF000000).copy(alpha = 0.95f)),
                            startY = sy + r - depth,
                            endY = sy + r + depth
                        )
                    )

                    // Основная фигура с многозонным градиентом
                    drawShape(
                        type = shape.type, sx = sx, sy = sy, r = r,
                        brush = Brush.verticalGradient(
                            colors = listOf(pal.top, pal.mid, pal.mid, pal.bottom),
                            startY = sy - r,
                            endY = sy + r
                        )
                    )

                    // Радиальный 3D-шейдинг (смещённый блик)
                    drawShape(
                        type = shape.type, sx = sx, sy = sy, r = r,
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            center = Offset(sx - r * 0.45f, sy - r * 0.5f),
                            radius = r * 1.35f
                        )
                    )

                    // Bounce light снизу (отражение снизу)
                    drawShape(
                        type = shape.type, sx = sx, sy = sy, r = r,
                        brush = Brush.radialGradient(
                            colors = listOf(
                                pal.bounce.copy(alpha = 0.55f),
                                Color.Transparent
                            ),
                            center = Offset(sx + r * 0.3f, sy + r * 0.65f),
                            radius = r * 0.95f
                        )
                    )

                    // Спекуляр-блик (маленький)
                    drawOval(
                        color = Color.White.copy(alpha = 0.75f),
                        topLeft = Offset(sx - r * 0.6f, sy - r * 0.75f),
                        size = Size(r * 0.55f, r * 0.3f)
                    )

                    // Верхний светлый кант
                    drawShapeOutline(
                        type = shape.type, sx = sx, sy = sy, r = r,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.95f), Color.Transparent),
                            startY = sy - r,
                            endY = sy + r * 0.1f
                        ),
                        width = 3.5f
                    )

                    // Нижний тёмный кант
                    drawShapeOutline(
                        type = shape.type, sx = sx, sy = sy, r = r,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = sy,
                            endY = sy + r
                        ),
                        width = 5f
                    )

                    // Двойная волна при результате
                    if (phase == Phase.Result && (isRight || isWrong)) {
                        val waveColor = if (isRight) Color(0xFFFFEB3B) else Color(0xFFFF1744)
                        val waveR1 = r * (1.2f + 2.0f * resultFlash)
                        val waveR2 = r * (1.2f + 1.5f * resultFlash)
                        val a1 = (1f - resultFlash) * 0.9f
                        drawCircle(
                            color = waveColor.copy(alpha = a1),
                            radius = waveR1,
                            center = Offset(sx, sy),
                            style = Stroke(width = 6f * (1f - resultFlash) + 2f)
                        )
                        drawCircle(
                            color = waveColor.copy(alpha = a1 * 0.6f),
                            radius = waveR2,
                            center = Offset(sx, sy),
                            style = Stroke(width = 4f * (1f - resultFlash) + 1f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Фигур: $shapeCount  •  Длина: ${sequenceLength(level)}",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp
        )
    }
}

// ---------- Рисование фигуры заданного типа ----------
private fun DrawScope.drawShape(
    type: ShapeType,
    sx: Float,
    sy: Float,
    r: Float,
    brush: Brush
) {
    when (type) {
        ShapeType.Circle -> drawCircle(brush = brush, radius = r, center = Offset(sx, sy))
        ShapeType.Square -> drawRect(
            brush = brush,
            topLeft = Offset(sx - r, sy - r),
            size = Size(r * 2, r * 2)
        )
        ShapeType.Triangle -> {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(sx, sy - r)
                lineTo(sx + r, sy + r)
                lineTo(sx - r, sy + r)
                close()
            }
            drawPath(path, brush = brush)
        }
        ShapeType.Hexagon -> {
            val path = androidx.compose.ui.graphics.Path()
            val sides = 6
            for (i in 0 until sides) {
                val a = Math.toRadians((60.0 * i - 90.0))
                val px = sx + (cos(a).toFloat() * r)
                val py = sy + (sin(a).toFloat() * r)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(path, brush = brush)
        }
    }
}

private fun DrawScope.drawShapeOutline(
    type: ShapeType,
    sx: Float,
    sy: Float,
    r: Float,
    brush: Brush,
    width: Float
) {
    when (type) {
        ShapeType.Circle -> drawCircle(
            brush = brush, radius = r, center = Offset(sx, sy),
            style = Stroke(width = width)
        )
        ShapeType.Square -> drawRect(
            brush = brush,
            topLeft = Offset(sx - r, sy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = width)
        )
        ShapeType.Triangle -> {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(sx, sy - r)
                lineTo(sx + r, sy + r)
                lineTo(sx - r, sy + r)
                close()
            }
            drawPath(path, brush = brush, style = Stroke(width = width))
        }
        ShapeType.Hexagon -> {
            val path = androidx.compose.ui.graphics.Path()
            val sides = 6
            for (i in 0 until sides) {
                val a = Math.toRadians((60.0 * i - 90.0))
                val px = sx + (cos(a).toFloat() * r)
                val py = sy + (sin(a).toFloat() * r)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(path, brush = brush, style = Stroke(width = width))
        }
    }
}

// ---------- Раскладка фигур (до 8) ----------
private fun buildShapes(count: Int): List<ShapeItem> {
    val hues = Hue.values()
    return when (count) {
        2 -> listOf(
            ShapeItem(ShapeType.Circle, hues[0], -0.5f, 0f),
            ShapeItem(ShapeType.Square, hues[1], 0.5f, 0f)
        )
        3 -> listOf(
            ShapeItem(ShapeType.Circle, hues[0], 0f, -0.55f),
            ShapeItem(ShapeType.Square, hues[1], -0.6f, 0.4f),
            ShapeItem(ShapeType.Triangle, hues[2], 0.6f, 0.4f)
        )
        4 -> listOf(
            ShapeItem(ShapeType.Circle, hues[0], -0.55f, -0.5f),
            ShapeItem(ShapeType.Square, hues[1], 0.55f, -0.5f),
            ShapeItem(ShapeType.Triangle, hues[2], -0.55f, 0.5f),
            ShapeItem(ShapeType.Hexagon, hues[3], 0.55f, 0.5f)
        )
        5 -> listOf(
            ShapeItem(ShapeType.Circle, hues[0], 0f, -0.65f),
            ShapeItem(ShapeType.Square, hues[1], -0.7f, -0.15f),
            ShapeItem(ShapeType.Triangle, hues[2], 0.7f, -0.15f),
            ShapeItem(ShapeType.Hexagon, hues[3], -0.5f, 0.6f),
            ShapeItem(ShapeType.Circle, hues[4], 0.5f, 0.6f)
        )
        6 -> listOf(
            ShapeItem(ShapeType.Circle, hues[0], -0.45f, -0.7f),
            ShapeItem(ShapeType.Square, hues[1], 0.45f, -0.7f),
            ShapeItem(ShapeType.Triangle, hues[2], -0.75f, 0f),
            ShapeItem(ShapeType.Hexagon, hues[3], 0.75f, 0f),
            ShapeItem(ShapeType.Circle, hues[4], -0.45f, 0.7f),
            ShapeItem(ShapeType.Square, hues[5], 0.45f, 0.7f)
        )
        7 -> listOf(
            ShapeItem(ShapeType.Circle, hues[0], 0f, -0.75f),
            ShapeItem(ShapeType.Square, hues[1], -0.7f, -0.4f),
            ShapeItem(ShapeType.Triangle, hues[2], 0.7f, -0.4f),
            ShapeItem(ShapeType.Hexagon, hues[3], -0.8f, 0.2f),
            ShapeItem(ShapeType.Circle, hues[4], 0.8f, 0.2f),
            ShapeItem(ShapeType.Square, hues[5], -0.5f, 0.75f),
            ShapeItem(ShapeType.Triangle, hues[0], 0.5f, 0.75f)
        )
        else -> listOf(
            ShapeItem(ShapeType.Circle, hues[0], -0.45f, -0.75f),
            ShapeItem(ShapeType.Square, hues[1], 0.45f, -0.75f),
            ShapeItem(ShapeType.Triangle, hues[2], -0.85f, -0.2f),
            ShapeItem(ShapeType.Hexagon, hues[3], 0.85f, -0.2f),
            ShapeItem(ShapeType.Circle, hues[4], -0.7f, 0.5f),
            ShapeItem(ShapeType.Square, hues[5], 0.7f, 0.5f),
            ShapeItem(ShapeType.Triangle, hues[0], -0.25f, 0.8f),
            ShapeItem(ShapeType.Hexagon, hues[1], 0.25f, 0.8f)
        )
    }
}