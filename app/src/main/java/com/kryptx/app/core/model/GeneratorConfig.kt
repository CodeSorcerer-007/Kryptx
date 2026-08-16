package com.kryptx.app.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class GeneratorMode(val title: String) {
    PASSWORD("Password"),
    PASSPHRASE("Passphrase"),
    PIN("PIN Code"),
    USERNAME("Username")
}

@Serializable
enum class UsernameStyle(val title: String) {
    MEMORABLE("Memorable (Adjective + Noun)"),
    ANONYMOUS_ALPHANUMERIC("Anonymous (kryptx_x7f2)"),
    EMAIL_ALIAS("Email Alias Format")
}

@Serializable
data class GeneratorConfig(
    val mode: GeneratorMode = GeneratorMode.PASSWORD,

    // Password mode options
    val passwordLength: Int = 20,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeNumbers: Boolean = true,
    val includeSymbols: Boolean = true,
    val avoidAmbiguous: Boolean = true, // Excludes 0, O, o, l, 1, I, etc.

    // Passphrase mode options
    val wordCount: Int = 4,
    val separator: String = "-",
    val capitalizeWords: Boolean = true,
    val includeNumberInPassphrase: Boolean = true,

    // PIN mode options
    val pinLength: Int = 6,

    // Username options
    val usernameStyle: UsernameStyle = UsernameStyle.MEMORABLE
)
