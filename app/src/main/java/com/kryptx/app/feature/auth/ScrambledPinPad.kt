package com.kryptx.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.theme.KryptxCyan
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import java.security.SecureRandom

/**
 * Anti-Shoulder-Surfing Scrambled PIN Pad.
 *
 * Randomizes numeric key positions across sessions to protect against shoulder surfing,
 * screen recordings, and thermal fingerprint residue analysis.
 */
@Composable
fun ScrambledPinPad(
    onPinComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    pinLength: Int = 6,
    isScrambleDisabled: Boolean = false,
    resetKey: Any? = null
) {
    var enteredPin by remember(resetKey) { mutableStateOf("") }
    var digits by remember(isScrambleDisabled) {
        mutableStateOf(if (isScrambleDisabled) listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0) else generateShuffledDigits())
    }
    val view = LocalView.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("scrambled_pin_pad"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // PIN Dot Indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(vertical = 20.dp)
                .testTag("pin_dot_indicator")
                .semantics {
                    contentDescription = "PIN entry indicator"
                    stateDescription = "${enteredPin.length} of $pinLength digits entered"
                }
        ) {
            for (i in 0 until pinLength) {
                val isFilled = i < enteredPin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFilled) KryptxCyan
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (isFilled) KryptxCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3x4 Keypad Grid
        val rows = digits.chunked(3)
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (row in rows.take(3)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    for (digit in row) {
                        PinKeyButton(
                            text = digit.toString(),
                            onClick = {
                                if (enteredPin.length < pinLength) {
                                    val newPin = enteredPin + digit
                                    enteredPin = newPin
                                    KryptxHaptics.tick(view)
                                    if (newPin.length == pinLength) {
                                        onPinComplete(newPin)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Bottom row: Shuffle, 10th digit, Backspace
            val lastDigit = rows.getOrNull(3)?.firstOrNull() ?: 0
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .testTag("pin_shuffle_button")
                        .clickable {
                            if (!isScrambleDisabled) {
                                digits = generateShuffledDigits()
                                KryptxHaptics.tap(view)
                            }
                        }
                        .semantics {
                            role = Role.Button
                            contentDescription = "Scramble Keypad"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Scramble Keypad",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Last Digit
                PinKeyButton(
                    text = lastDigit.toString(),
                    onClick = {
                        if (enteredPin.length < pinLength) {
                            val newPin = enteredPin + lastDigit
                            enteredPin = newPin
                            KryptxHaptics.tick(view)
                            if (newPin.length == pinLength) {
                                onPinComplete(newPin)
                            }
                        }
                    }
                )

                // Backspace Button
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .testTag("pin_backspace_button")
                        .clickable {
                            if (enteredPin.isNotEmpty()) {
                                enteredPin = enteredPin.dropLast(1)
                                KryptxHaptics.tap(view)
                            }
                        }
                        .semantics {
                            role = Role.Button
                            contentDescription = "Backspace"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PinKeyButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .testTag("pin_key_$text")
            .bounceClick(scaleDown = 0.92f, onClick = onClick)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                CircleShape
            )
            .semantics {
                role = Role.Button
                contentDescription = "Digit $text"
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun generateShuffledDigits(): List<Int> {
    val list = (0..9).toMutableList()
    val random = SecureRandom()
    list.shuffle(random)
    return list
}

@Preview(showBackground = true)
@Composable
fun ScrambledPinPadPreview() {
    ScrambledPinPad(
        pinLength = 6,
        onPinComplete = {}
    )
}

