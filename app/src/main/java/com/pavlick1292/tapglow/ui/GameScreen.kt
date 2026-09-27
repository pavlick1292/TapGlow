package com.pavlick1292.tapglow.ui
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// ---------- Фазы раунда ----------
private enum class Phase { Idle, Intrigue, WaitTap, Result }

// ---------- Описание фигуры ----------
private data class ShapeItem(
    val type: ShapeType,
    val centerOffsetX: Float, // -1f..1f относительно центра
    val centerOffsetY: Float
)

private enum class ShapeType { Circle, Square }

// ---------- Утилиты ----------
private const val INTRIGUE_HIGHLIGHT_MS = 800L // на 1 уровне
private const val IDLE_DURATION_MS = 1500L
private const val TAP_TIMEOUT_MS = 3000L // 3 сек на 1 уровне

@Composable
fun GameScreen() {
    // --- Состояние раунда ---
    var phase by remember { mutableStateOf(Phase.Idle) }
    var highlightedIndex by remember { mutableStateOf(-1) }
    var targetIndex by remember { mutableStateOf(-1) }
    var tappedIndex by remember { mutableStateOf(-1) }
    var isWin by remember { mutableStateOf(false) }
    var timeLeftMs by remember { mutableStateOf(TAP_TIMEOUT_MS) }

    // Две фигуры: круг слева, квадрат справа
    val shapes = remember {
        listOf(
            ShapeItem(ShapeType.Circle, -0.5f, 0f),
            ShapeItem(ShapeType.Square, 0.5f, 0f)
        )
    }

    // --- Idle-анимация (пульсация 1.0..1.05) ---
    var breath by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            breath = 0.5f + 0.5f * sin(System.currentTimeMillis() / 400.0).toFloat()
            delay(16L)
        }
    }

    // --- Запуск раунда ---
    fun startRound() {
        phase = Phase.Idle
        highlightedIndex = -1
        tappedIndex = -1
        isWin = false
        timeLeftMs = TAP_TIMEOUT_MS
        targetIndex = shapes.indices.random()
    }

    LaunchedEffect(Unit) { startRound() }

    // --- Логика фаз ---
    LaunchedEffect(phase, targetIndex) {
        when (phase) {
            Phase.Idle -> {
                delay(IDLE_DURATION_MS)
                phase = Phase.Intrigue
            }
            Phase.Intrigue -> {
                // По очереди подсвечиваем фигуры
                for (i in shapes.indices) {
                    highlightedIndex = i
                    delay(INTRIGUE_HIGHLIGHT_MS)
                }
                highlightedIndex = -1
                phase = Phase.WaitTap
            }
            Phase.WaitTap -> {
                // Тикаем таймер
                val start = System.currentTimeMillis()
                while (timeLeftMs > 0 && phase == Phase.WaitTap) {
                    delay(16L)
                    val elapsed = System.currentTimeMillis() - start
                    timeLeftMs = (TAP_TIMEOUT_MS - elapsed).coerceAtLeast(0L)
                }
                if (phase == Phase.WaitTap) {
                    // Время вышло — промах
                    tappedIndex = -1
                    isWin = false
                    phase = Phase.Result
                }
            }
            Phase.Result -> { /* ждём кнопку «Дальше» */ }
        }
    }

    // --- UI ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101018))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Верхняя панель: уровень, таймер-бар
        Text("Уровень 1", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        // Таймер-бар
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(Color(0xFF2A2A3A), RoundedCornerShape(5.dp))
        ) {
            val frac = (timeLeftMs.toFloat() / TAP_TIMEOUT_MS).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .height(10.dp)
                    .background(
                        if (frac > 0.3f) Color(0xFF4CAF50) else Color(0xFFE53935),
                        RoundedCornerShape(5.dp)
                    )
            )
        }

        Spacer(Modifier.height(16.dp))

        // Подсказка фазы
        val hint = when (phase) {
            Phase.Idle -> "Приготовься..."
            Phase.Intrigue -> "Смотри внимательно..."
            Phase.WaitTap -> "ЖМИ!"
            Phase.Result -> if (isWin) "Угадал!" else "Промах!"
        }
        Text(hint, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(24.dp))

        // Игровое поле
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
                            val radius = minOf(w, h) * 0.15f

                            shapes.forEachIndexed { index, shape ->
                                val sx = cx + shape.centerOffsetX * w * 0.5f
                                val sy = cy + shape.centerOffsetY * h * 0.5f
                                val dx = offset.x - sx
                                val dy = offset.y - sy
                                val inside = when (shape.type) {
                                    ShapeType.Circle -> dx * dx + dy * dy <= radius * radius
                                    ShapeType.Square -> kotlin.math.abs(dx) <= radius &&
                                            kotlin.math.abs(dy) <= radius
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
                val radius = minOf(w, h) * 0.15f

                shapes.forEachIndexed { index, shape ->
                    val sx = cx + shape.centerOffsetX * w * 0.5f
                    val sy = cy + shape.centerOffsetY * h * 0.5f

                    // Пульсация scale 1.0..1.05 в Idle
                    val scale = if (phase == Phase.Idle) 1f + 0.05f * breath else 1f
                    val r = radius * scale

                    // Цвет фигуры
                    val baseColor = when {
                        phase == Phase.Result && index == tappedIndex && !isWin ->
                            Color(0xFFE53935) // промах — красный
                        phase == Phase.Result && index == targetIndex && isWin ->
                            Color(0xFFFFEB3B) // победа — жёлтый
                        highlightedIndex == index -> Color(0xFFFFEB3B) // интрига
                        else -> Color(0xFF7E57C2) // обычный фиолетовый
                    }

                    when (shape.type) {
                        ShapeType.Circle -> drawCircle(
                            color = baseColor,
                            radius = r,
                            center = Offset(sx, sy)
                        )
                        ShapeType.Square -> drawRect(
                            color = baseColor,
                            topLeft = Offset(sx - r, sy - r),
                            size = Size(r * 2, r * 2)
                        )
                    }

                    // Обводка
                    when (shape.type) {
                        ShapeType.Circle -> drawCircle(
                            color = Color.White.copy(alpha = 0.6f),
                            radius = r,
                            center = Offset(sx, sy),
                            style = Stroke(width = 4f)
                        )
                        ShapeType.Square -> drawRect(
                            color = Color.White.copy(alpha = 0.6f),
                            topLeft = Offset(sx - r, sy - r),
                            size = Size(r * 2, r * 2),
                            style = Stroke(width = 4f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Кнопка «Дальше» после результата
        if (phase == Phase.Result) {
            Button(onClick = { startRound() }) {
                Text("Дальше", fontSize = 18.sp)
            }
        }
    }
}