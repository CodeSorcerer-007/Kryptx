package com.kryptx.app.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class CustomField(
    val id: String,
    val label: String,
    val value: String,
    val isSecured: Boolean = false
)
