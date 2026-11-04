package com.aternos.bridge.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aternos.bridge.data.ServerStatus
import com.aternos.bridge.ui.theme.BorderOutline
import com.aternos.bridge.ui.theme.EmeraldGreen
import com.aternos.bridge.ui.theme.LightCard
import com.aternos.bridge.ui.theme.PrimaryOrange
import com.aternos.bridge.ui.theme.RedError
import com.aternos.bridge.ui.theme.SecondaryAmber
import com.aternos.bridge.ui.theme.TextMuted

@Composable
fun ServerStatusPill(
    status: ServerStatus,
    modifier: Modifier = Modifier
) {
    val (dotColor, textColor, isPulsing) = when (status) {
        ServerStatus.ONLINE -> Triple(EmeraldGreen, EmeraldGreen, false)
        ServerStatus.OFFLINE -> Triple(TextMuted, TextMuted, false)
        ServerStatus.STARTING, ServerStatus.SAVING -> Triple(PrimaryOrange, PrimaryOrange, true)
        ServerStatus.STOPPING -> Triple(RedError, RedError, true)
        ServerStatus.QUEUE -> Triple(SecondaryAmber, SecondaryAmber, true)
        ServerStatus.UNKNOWN -> Triple(TextMuted, TextMuted, false)
    }

    val alphaAnim = if (isPulsing) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
        alpha
    } else 1.0f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(LightCard)
            .border(1.dp, BorderOutline, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(alphaAnim)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = status.displayName,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
