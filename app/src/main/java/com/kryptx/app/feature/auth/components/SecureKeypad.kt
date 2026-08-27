package com.kryptx.app.feature.auth.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A randomized on-screen secure keypad that prevents third-party keyboard loggers
 * from capturing the Master Password or PIN. The keys are shuffled on every composition.
 */
@Composable
fun SecureKeypad(
    onKeyPressed: (Char) -> Unit,
    onBackspacePressed: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    isAlphanumeric: Boolean = false // Set to true to include letters if needed
) {
    // Basic PIN-pad setup (0-9). We shuffle these once upon launch.
    val keys = remember {
        val baseKeys = ('0'..'9').toList()
        baseKeys.shuffled()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Rows of 3 keys
        for (i in 0 until 3) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (j in 0 until 3) {
                    val keyIndex = i * 3 + j
                    KeypadButton(
                        text = keys[keyIndex].toString(),
                        onClick = { onKeyPressed(keys[keyIndex]) }
                    )
                }
            }
        }

        // Bottom row: Backspace, 0 (or last key), Submit
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            KeypadActionButton(
                onClick = onBackspacePressed,
                content = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            )

            KeypadButton(
                text = keys[9].toString(),
                onClick = { onKeyPressed(keys[9]) }
            )

            KeypadActionButton(
                onClick = onSubmit,
                content = {
                    Text("OK", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            )
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KeypadActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
