package com.aternos.bridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aternos.bridge.ui.theme.BorderOutline
import com.aternos.bridge.ui.theme.LightBackground
import com.aternos.bridge.ui.theme.LightCard
import com.aternos.bridge.ui.theme.PrimaryOrange
import com.aternos.bridge.ui.theme.TextMuted
import com.aternos.bridge.ui.theme.TextPrimary

@Composable
fun ManualCommandBar(
    onSendCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var commandText by remember { mutableStateOf("") }

    val submit = {
        if (commandText.isNotBlank()) {
            onSendCommand(commandText.trim())
            commandText = ""
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LightCard)
            .border(1.dp, BorderOutline, RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        OutlinedTextField(
            value = commandText,
            onValueChange = { commandText = it },
            placeholder = {
                Text(
                    text = "Type server command (e.g. list, say Hi)...",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainerColor = LightBackground,
                unfocusedContainerColor = LightBackground,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(
            onClick = { submit() },
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PrimaryOrange)
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Send",
                tint = LightCard,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
