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
import androidx.compose.material3.Button
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
private enum class ShapeType { Circle, Square }

// ---------- Описание фигуры ----------
private data class ShapeItem(
    val type: ShapeType,
    val offsetX: Float,
    val offsetY: Float
)

// ---------- Константы ----------
private const val INTRIGUE_HIGHLIGHT_MS = 800L
private const val IDLE_DURATION_MS = 1500L
private const val TAP_TIMEOUT_MS = 3000L

@Composable
fun GameScreen() {
    var phase by remember { mutableStateOf(Phase.Idle) }
    var highlightedIndex by remember { mutableStateOf(-1) }
    var targetIndex by remember { mutableStateOf(-1) }
    var tappedIndex by remember { mutableStateOf(-1) }
    var isWin by remember { mutableStateOf(false) }
    var timeLeftMs by remember { mutableStateOf(TAP_TIMEOUT_MS) }

    // Прогресс эффекта вспышки при результате (0..1)
    var resultFlash by remember { mutableStateOf(0f) }

    val shapes = remember {
        listOf(
            ShapeItem(ShapeType.Circle, -0.55f, 0f),
            ShapeItem(ShapeType.Square, 0.55f, 0f)
        )
    }

    // Глобальный пульс времени
    var pulse by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            pulse = 0.5f + 0.5f * sin(System.currentTimeMillis() / 300.0).toFloat()
            delay(16L)
        }
    }

    // Idle-«дыхание»: плавный подъём-спад 0..1
    var breath by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            breath = 0.5f + 0.5f * sin(System.currentTimeMillis() / 500.0).toFloat()
            delay(16L)
        }
    }

    fun startRound() {
        phase = Phase.Idle
        highlightedIndex = -1
        tappedIndex = -1
        isWin = false
        timeLeftMs = TAP_TIMEOUT_MS
        resultFlash = 0f
        targetIndex = shapes.indices.random()
    }

    LaunchedEffect(Unit) { startRound() }

    LaunchedEffect(phase, targetIndex) {
        when (phase) {
            Phase.Idle -> {
                delay(IDLE_DURATION_MS)
                phase = Phase.Intrigue
            }
            Phase.Intrigue -> {
                for (i in shapes.indices) {
                    highlightedIndex = i
                    delay(INTRIGUE_HIGHLIGHT_MS)
                }
                highlightedIndex = -1
                phase = Phase.WaitTap
            }
            Phase.WaitTap -> {
                val start = System.currentTimeMillis()
                while (timeLeftMs > 0 && phase == Phase.WaitTap) {
                    delay(16L)
                    val elapsed = System.currentTimeMillis() - start
                    timeLeftMs = (TAP_TIMEOUT_MS - elapsed).coerceAtLeast(0L)
                }
                if (phase == Phase.WaitTap) {
                    tappedIndex = -1
                    isWin = false
                    phase = Phase.Result
                }
            }
            Phase.Result -> {
                // Анимация вспышки результата
                val start = System.currentTimeMillis()
                while (resultFlash < 1f) {
                    resultFlash = ((System.currentTimeMillis() - start) / 600f).coerceIn(0f, 1f)
                    delay(16L)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF07070F), Color(0xFF181028), Color(0xFF07070F))
                )
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Уровень 1", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        // Таймер-бар с 3D-градиентом
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(Color(0xFF1A1A28), RoundedCornerShape(7.dp))
        ) {
            val frac = (timeLeftMs.toFloat() / TAP_TIMEOUT_MS).coerceIn(0f, 1f)
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
            // Блик сверху на баре
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
            Phase.Intrigue -> "Смотри внимательно..."
            Phase.WaitTap -> "ЖМИ!"
            Phase.Result -> if (isWin) "Угадал!" else "Промах!"
        }
        Text(hint, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)

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
                            val radius = minOf(w, h) * 0.20f

                            shapes.forEachIndexed { index, shape ->
                                val sx = cx + shape.offsetX * w * 0.35f
                                val sy = cy + shape.offsetY * h * 0.35f
                                val dx = offset.x - sx
                                val dy = offset.y - sy
                                val inside = when (shape.type) {
                                    ShapeType.Circle -> dx * dx + dy * dy <= radius * radius
                                    ShapeType.Square -> abs(dx) <= radius && abs(dy) <= radius
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
                val radius = minOf(w, h) * 0.20f

                // Виньетка
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color(0xE6000000)),
                        center = Offset(cx, cy),
                        radius = maxOf(w, h) * 0.8f
                    ),
                    size = size
                )

                // Пол-«сцена» под фигурами
                val floorY = cy + radius * 1.7f
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0x66A78BFA),
                            Color(0x66A78BFA),
                            Color.Transparent
                        )
                    ),
                    topLeft = Offset(0f, floorY),
                    size = Size(w, 3f)
                )
                // Свечение-отражение под сценой
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0x33A78BFA), Color.Transparent)
                    ),
                    topLeft = Offset(0f, floorY),
                    size = Size(w, 60f)
                )

                shapes.forEachIndexed { index, shape ->
                    val sx = cx + shape.offsetX * w * 0.35f
                    val sy = cy + shape.offsetY * h * 0.35f

                    val isHighlighted = highlightedIndex == index
                    val isWrong = phase == Phase.Result && index == tappedIndex && !isWin
                    val isRight = phase == Phase.Result && index == targetIndex && isWin

                    // Масштабы
                    val idleScale = if (phase == Phase.Idle) 1f + 0.06f * breath else 1f
                    val glowScale = if (isHighlighted) 1f + 0.10f * pulse else 1f
                    // Отскок при результате
                    val resultScale = when {
                        phase == Phase.Result && isRight -> 1f + 0.25f * (1f - resultFlash)
                        phase == Phase.Result && isWrong -> 1f - 0.10f * (1f - resultFlash)
                        else -> 1f
                    }
                    val r = radius * idleScale * glowScale * resultScale

                    // Палитра
                    val (topColor, midColor, bottomColor) = when {
                        isWrong -> Triple(Color(0xFFFF8A80), Color(0xFFE53935), Color(0xFF7A0000))
                        isRight -> Triple(Color(0xFFFFFFB3), Color(0xFFFFD54F), Color(0xFFA67C00))
                        isHighlighted -> Triple(Color(0xFFFFFFCC), Color(0xFFFFF176), Color(0xFFA67C00))
                        else -> Triple(Color(0xFFD6C6FF), Color(0xFF7E57C2), Color(0xFF3A1F7A))
                    }

                    // 1. Внешнее свечение (halo)
                    val haloAlpha = when {
                        isHighlighted -> 0.55f + 0.25f * pulse
                        isRight -> 0.75f
                        isWrong -> 0.65f
                        else -> 0.18f
                    }
                    val haloColor = when {
                        isWrong -> Color(0xFFFF1744)
                        isRight || isHighlighted -> Color(0xFFFFEB3B)
                        else -> Color(0xFF7E57C2)
                    }
                    val haloRadius = r * (1.55f + 0.20f * pulse)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                haloColor.copy(alpha = haloAlpha),
                                haloColor.copy(alpha = haloAlpha * 0.5f),
                                Color.Transparent
                            ),
                            center = Offset(sx, sy),
                            radius = haloRadius
                        ),
                        radius = haloRadius,
                        center = Offset(sx, sy)
                    )

                    // 2. Вращающийся ореол при подсветке (лучи)
                    if (isHighlighted) {
                        val angle = (System.currentTimeMillis() / 6.0).toFloat() % 360f
                        rotate(degrees = angle, pivot = Offset(sx, sy)) {
                            val rays = 8
                            for (i in 0 until rays) {
                                val a = (i * 360f / rays)
                                val rad = Math.toRadians(a.toDouble())
                                val rr = r * 1.6f
                                val ex = sx + (cos(rad).toFloat() * rr)
                                val ey = sy + (sin(rad).toFloat() * rr)
                                drawLine(
                                    color = Color(0xFFFFEB3B).copy(alpha = 0.35f + 0.25f * pulse),
                                    start = Offset(sx, sy),
                                    end = Offset(ex, ey),
                                    strokeWidth = 3f
                                )
                            }
                        }
                    }

                    // 3. Тень-эллипс под фигурой
                    val shadowW = r * 2.0f
                    val shadowH = r * 0.45f
                    val shadowAlpha = if (isHighlighted) 0.7f else 0.45f
                    val shadowCY = sy + r * 1.25f
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

                    // 4. Тёмное «основание» (нижняя часть, псевдо-3D толщина)
                    val depth = r * 0.35f
                    val baseDark = when {
                        isWrong -> Color(0xFF4A0000)
                        isRight || isHighlighted -> Color(0xFF6B4E00)
                        else -> Color(0xFF241158)
                    }
                    when (shape.type) {
                        ShapeType.Circle -> {
                            // нижний тёмный круг, смещённый вниз
                            drawCircle(
                                brush = Brush.verticalGradient(
                                    listOf(baseDark, Color(0xFF000000).copy(alpha = 0.9f)),
                                    startY = sy + r - depth,
                                    endY = sy + r + depth
                                ),
                                radius = r,
                                center = Offset(sx, sy + depth)
                            )
                        }
                        ShapeType.Square -> {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    listOf(baseDark, Color(0xFF000000).copy(alpha = 0.9f)),
                                    startY = sy + r - depth,
                                    endY = sy + r + depth
                                ),
                                topLeft = Offset(sx - r, sy - r + depth),
                                size = Size(r * 2, r * 2)
                            )
                        }
                    }

                    // 5. Верхняя часть фигуры с многозональным градиентом
                    when (shape.type) {
                        ShapeType.Circle -> drawCircle(
                            brush = Brush.verticalGradient(
                                colors = listOf(topColor, midColor, midColor, bottomColor),
                                startY = sy - r,
                                endY = sy + r
                            ),
                            radius = r,
                            center = Offset(sx, sy)
                        )
                        ShapeType.Square -> drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(topColor, midColor, midColor, bottomColor),
                                startY = sy - r,
                                endY = sy + r
                            ),
                            topLeft = Offset(sx - r, sy - r),
                            size = Size(r * 2, r * 2)
                        )
                    }

                    // 6. Радиальный «глянец» (мягкий объём)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.Transparent
                            ),
                            center = Offset(sx - r * 0.35f, sy - r * 0.45f),
                            radius = r * 1.1f
                        ),
                        radius = r,
                        center = Offset(sx, sy)
                    )

                    // 7. Верхний «кружок-блик» (specular)
                    when (shape.type) {
                        ShapeType.Circle -> {
                            drawOval(
                                color = Color.White.copy(alpha = 0.55f),
                                topLeft = Offset(sx - r * 0.55f, sy - r * 0.65f),
                                size = Size(r * 0.7f, r * 0.4f)
                            )
                        }
                        ShapeType.Square -> {
                            drawOval(
                                color = Color.White.copy(alpha = 0.5f),
                                topLeft = Offset(sx - r * 0.65f, sy - r * 0.7f),
                                size = Size(r * 0.55f, r * 0.3f)
                            )
                        }
                    }

                    // 8. Верхний светлый кант (эффект края)
                    when (shape.type) {
                        ShapeType.Circle -> drawCircle(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.9f),
                                    Color.White.copy(alpha = 0.0f)
                                ),
                                startY = sy - r,
                                endY = sy
                            ),
                            radius = r,
                            center = Offset(sx, sy),
                            style = Stroke(width = 3f)
                        )
                        ShapeType.Square -> drawRect(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.9f),
                                    Color.White.copy(alpha = 0.0f)
                                ),
                                startY = sy - r,
                                endY = sy
                            ),
                            topLeft = Offset(sx - r, sy - r),
                            size = Size(r * 2, r * 2),
                            style = Stroke(width = 3f)
                        )
                    }

                    // 9. Нижний тёмный кант (глубина)
                    when (shape.type) {
                        ShapeType.Circle -> drawCircle(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                ),
                                startY = sy,
                                endY = sy + r
                            ),
                            radius = r,
                            center = Offset(sx, sy),
                            style = Stroke(width = 4f)
                        )
                        ShapeType.Square -> drawRect(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                ),
                                startY = sy,
                                endY = sy + r
                            ),
                            topLeft = Offset(sx - r, sy - r),
                            size = Size(r * 2, r * 2),
                            style = Stroke(width = 4f)
                        )
                    }

                    // 10. Расходящаяся волна при результате
                    if (phase == Phase.Result && (isRight || isWrong)) {
                        val waveR = r * (1.2f + 1.8f * resultFlash)
                        val waveAlpha = (1f - resultFlash) * 0.8f
                        val waveColor = if (isRight) Color(0xFFFFEB3B) else Color(0xFFFF1744)
                        drawCircle(
                            color = waveColor.copy(alpha = waveAlpha),
                            radius = waveR,
                            center = Offset(sx, sy),
                            style = Stroke(width = 6f * (1f - resultFlash) + 2f)
                        )
                        drawCircle(
                            color = waveColor.copy(alpha = waveAlpha * 0.5f),
                            radius = waveR * 1.25f,
                            center = Offset(sx, sy),
                            style = Stroke(width = 3f * (1f - resultFlash) + 1f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (phase == Phase.Result) {
            Button(onClick = { startRound() }) {
                Text("Дальше", fontSize = 18.sp)
            }
        }
    }
}