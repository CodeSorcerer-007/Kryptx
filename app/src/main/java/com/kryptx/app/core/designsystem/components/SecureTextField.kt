package com.kryptx.app.core.designsystem.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.MonospaceFont

/**
 * A Compose TextField backed by a [CharArray] instead of a persistent [String].
 * The internal buffer is a mutable [androidx.compose.runtime.snapshots.SnapshotStateList<Char>]
 * so Compose can track character-level changes without long-lived [String] heap retention.
 * The [onValueChange] callback delivers a [CharArray] snapshot — callers MUST
 * wipe the received array with [SecureMemory.wipe] after use.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureTextField(
    value: CharArray,
    onValueChange: (CharArray) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    isPassword: Boolean = true,
    isMonospace: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    isError: Boolean = false,
    errorMessage: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val buffer = remember { mutableStateListOf<Char>() }
    var textFieldValue by remember { mutableStateOf(TextFieldValue()) }
    val interactionSource = remember { MutableInteractionSource() }

    // Synchronize buffer when external value changes (e.g. wiped or cleared by caller)
    LaunchedEffect(value) {
        val currentChars = CharArray(buffer.size) { i -> buffer[i] }
        if (value.isEmpty() && buffer.isNotEmpty()) {
            buffer.clear()
            textFieldValue = TextFieldValue("")
        } else if (!value.contentEquals(currentChars)) {
            buffer.clear()
            for (c in value) {
                buffer.add(c)
            }
            textFieldValue = TextFieldValue(String(value))
        }
        SecureMemory.wipe(currentChars)
    }

    // Resolve secure keyboard options for sensitive master password input
    val effectiveKeyboardOptions = if (isPassword && keyboardOptions == KeyboardOptions.Default) {
        KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password
        )
    } else {
        keyboardOptions
    }

    val visualTransformation = if (isPassword && !passwordVisible) {
        PasswordVisualTransformation()
    } else {
        VisualTransformation.None
    }

    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = KryptxBlue,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        errorBorderColor = MaterialTheme.colorScheme.error,
        focusedLabelColor = KryptxBlue,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Column(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newTfv ->
                textFieldValue = newTfv
                val incoming = newTfv.text.toCharArray()
                buffer.clear()
                for (c in incoming) {
                    buffer.add(c)
                }
                val snapshot = CharArray(buffer.size) { i -> buffer[i] }
                SecureMemory.wipe(incoming)
                onValueChange(snapshot)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            maxLines = maxLines,
            cursorBrush = SolidColor(KryptxBlue),
            textStyle = TextStyle(
                fontFamily = if (isMonospace) MonospaceFont else FontFamily.Default,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            visualTransformation = visualTransformation,
            keyboardOptions = effectiveKeyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = textFieldValue.text,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = singleLine,
                    visualTransformation = visualTransformation,
                    interactionSource = interactionSource,
                    isError = isError,
                    label = if (label.isNotEmpty()) { { Text(label) } } else null,
                    placeholder = if (placeholder.isNotEmpty()) { { Text(placeholder) } } else null,
                    leadingIcon = leadingIcon,
                    trailingIcon = if (isPassword || trailingIcon != null) {
                        {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (trailingIcon != null) {
                                    trailingIcon()
                                }
                                if (isPassword) {
                                    IconButton(
                                        onClick = { passwordVisible = !passwordVisible },
                                        modifier = Modifier.semantics {
                                            role = Role.Button
                                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else null,
                    colors = colors,
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true,
                            isError = isError,
                            interactionSource = interactionSource,
                            colors = colors,
                            shape = RoundedCornerShape(22.dp)
                        )
                    }
                )
            }
        )

        if (isError && !errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 14.dp, top = 4.dp)
            )
        }
    }
}
