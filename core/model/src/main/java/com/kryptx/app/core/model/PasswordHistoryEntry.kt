package com.kryptx.app.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Historical record of a previous password before rotation, enabling instant recovery
 * if external services retain older authentication credentials.
 */
@Immutable
@Serializable
data class PasswordHistoryEntry(
    val password: String,
    val changedAt: Long = System.currentTimeMillis(),
    val note: String = "Rotated password"
)
