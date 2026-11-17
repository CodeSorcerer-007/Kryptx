package com.kryptx.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ActivityEvent(
    val timestamp: Long,
    val type: String,
    val description: String
)
