package com.kryptx.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class CustomField(
    val id: String,
    val label: String,
    val value: String,
    val isSecured: Boolean = false
)
