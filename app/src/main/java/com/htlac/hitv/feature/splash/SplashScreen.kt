package com.htlac.hitv.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onAnimationFinished: () -> Unit) {
    // 透明度动画：控制渐隐渐现
    val alpha = remember { Animatable(0f) }
    // 缩放动画：营造高级的“呼吸/推进”感
    val scale = remember { Animatable(0.85f) }

    LaunchedEffect(Unit) {
        // 1. 并发执行入场动画：平滑淡入 + 缓慢放大
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing)
            )
        }
        launch {
            scale.animateTo(
                targetValue = 1.05f,
                animationSpec = tween(durationMillis = 1800, easing = FastOutSlowInEasing)
            )
        }

        // 2. 让 Logo 在屏幕上完美展现停留一小会
        delay(1200)

        // 3. 优雅退场：极速淡出，不拖泥带水
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 300)
        )

        // 4. 动画结束，通知宿主跳转
        onAnimationFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // 利用 graphicsLayer 将动画值绑定到 UI 上
        Row(
            modifier = Modifier.graphicsLayer(
                alpha = alpha.value,
                scaleX = scale.value,
                scaleY = scale.value
            ),
            verticalAlignment = Alignment.Bottom
        ) {
            // "Hi" 使用粗体纯白，厚重醒目
            Text(
                text = "Hi",
                color = Color.White,
                fontSize = 84.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            // "TV" 使用纤细科技蓝，制造强烈的视觉反差
            Text(
                text = "TV",
                color = Color(0xFF0A84FF), // 极客魅蓝
                fontSize = 84.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp
            )
        }
    }
}