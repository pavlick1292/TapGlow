package com.pavlick1292.tapglow.ui

import android.view.SurfaceView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.pavlick1292.tapglow.filament.FilamentScene
import kotlinx.coroutines.delay

// Состояния игрового цикла
enum class GameState {
    Idle,       // ожидание старта уровня
    Intrigue,   // последовательность миганий (запоминай!)
    WaitTap,    // ждём тап игрока
    Result      // результат (правильно/неправильно)
}

/**
 * Экран игры: 3D-поле (Filament) + UI-оверлей (Compose).
 *
 * Шаг 1: 3D-сцена пустая, с камерой и светом.
 * UI показывает уровень и подсказку.
 * Тап по полю переключает состояния (заглушка до шага 3-4).
 */
@Composable
fun GameScreen() {
    // --- Игровое состояние ---
    var level by remember { mutableStateOf(1) }
    var gameState by remember { mutableStateOf(GameState.Idle) }
    var hint by remember { mutableStateOf("Нажми на поле, чтобы начать") }

    // --- 3D-сцена Filament ---
    val filamentScene = remember { FilamentScene() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Управление жизненным циклом рендера
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                filamentScene.resume()
            }
            override fun onPause(owner: LifecycleOwner) {
                filamentScene.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            filamentScene.destroy()
        }
    }

    // --- Игровой цикл ---
    LaunchedEffect(gameState) {
        when (gameState) {
            GameState.Idle -> {
                hint = "Уровень $level. Нажми на поле, чтобы начать"
            }
            GameState.Intrigue -> {
                // Заглушка: в шаге 3 здесь будут мигать 3D-фигуры
                hint = "Запоминай..."
                delay(2000)
                gameState = GameState.WaitTap
            }
            GameState.WaitTap -> {
                hint = "Тапай по последней фигуре!"
            }
            GameState.Result -> {
                // Заглушка: в шаге 4 будет проверка правильности
                hint = "Правильно! Следующий уровень..."
                delay(1500)
                level++
                gameState = GameState.Idle
            }
        }
    }

    // --- Экран ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(gameState) {
                detectTapGestures(
                    onTap = {
                        when (gameState) {
                            GameState.Idle -> gameState = GameState.Intrigue
                            GameState.WaitTap -> gameState = GameState.Result
                            else -> { /* в Intrigue и Result тапы игнорируем */ }
                        }
                    }
                )
            }
    ) {
        // 3D-поле: Filament рендерит в SurfaceView
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { sv ->
                    filamentScene.attach(sv)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // UI-оверлей: уровень и подсказка поверх 3D-сцены
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Уровень $level",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = hint,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
