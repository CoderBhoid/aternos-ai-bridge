package com.aternos.bridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aternos.bridge.ui.theme.LightCard
import com.aternos.bridge.ui.theme.PrimaryOrange
import com.aternos.bridge.ui.theme.SecondaryAmber
import com.aternos.bridge.ui.theme.SecondaryAmberContainer
import com.aternos.bridge.ui.theme.TextPrimary
import com.aternos.bridge.ui.theme.TextSecondary

@Composable
fun CloudflareAlertBanner(
    onSolveClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SecondaryAmberContainer)
            .border(1.5.dp, SecondaryAmber, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Cloudflare Alert",
                tint = SecondaryAmber,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Cloudflare Challenge Detected",
                    color = SecondaryAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Aternos Turnstile check is active. Solve it to resume bridge.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onSolveClicked,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryOrange,
                contentColor = LightCard
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "Solve Now",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}
