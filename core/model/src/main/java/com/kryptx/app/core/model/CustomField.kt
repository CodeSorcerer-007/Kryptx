package com.kryptx.app.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.UUID

@Immutable
@Serializable
data class CustomField(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val value: String,
    val isSecured: Boolean = false
)
