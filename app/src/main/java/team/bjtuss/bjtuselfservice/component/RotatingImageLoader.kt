package team.bjtuss.bjtuselfservice.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

@Composable
fun RotatingImageLoader(
    modifier: Modifier = Modifier,
    image: Painter,
    rotationDuration: Int = 1000 // 旋转一周的时间，单位毫秒
) {
    // 创建一个可以无限循环的旋转动画
    val infiniteTransition = rememberInfiniteTransition(label = "rotate")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(rotationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // 使用Box居中显示并应用旋转
    Box(
        modifier = modifier
            .wrapContentHeight()
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = image,
            contentDescription = "Loading",
            modifier = Modifier
                .size(80.dp)  // 可调整大小
                .rotate(rotation)  // 应用旋转动画
        )
    }
}