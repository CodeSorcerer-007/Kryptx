package com.kryptx.app.feature.generator

import androidx.lifecycle.ViewModel
import com.kryptx.app.core.crypto.EntropyCalculator
import com.kryptx.app.core.generator.GeneratorEngine
import com.kryptx.app.core.model.GeneratorConfig
import com.kryptx.app.core.model.GeneratorMode
import com.kryptx.app.core.model.UsernameStyle
import com.kryptx.app.core.security.IClipboardSecurityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GeneratorViewModel(
    private val clipboardSecurityManager: IClipboardSecurityManager
) : ViewModel() {

    private val _config = MutableStateFlow(GeneratorConfig())
    val config: StateFlow<GeneratorConfig> = _config.asStateFlow()

    private val _result = MutableStateFlow(
        GeneratorEngine.generate(GeneratorConfig())
    )
    val result: StateFlow<GeneratorEngine.GenerationResult> = _result.asStateFlow()

    fun updateMode(mode: GeneratorMode) {
        val newConfig = _config.value.copy(mode = mode)
        _config.value = newConfig
        regenerate(newConfig)
    }

    fun updatePasswordLength(length: Int) {
        val newConfig = _config.value.copy(passwordLength = length)
        _config.value = newConfig
        regenerate(newConfig)
    }

    fun updateWordCount(count: Int) {
        val newConfig = _config.value.copy(wordCount = count)
        _config.value = newConfig
        regenerate(newConfig)
    }

    fun updatePinLength(length: Int) {
        val newConfig = _config.value.copy(pinLength = length)
        _config.value = newConfig
        regenerate(newConfig)
    }

    fun updateUsernameStyle(style: UsernameStyle) {
        val newConfig = _config.value.copy(usernameStyle = style)
        _config.value = newConfig
        regenerate(newConfig)
    }

    fun toggleUppercase(enabled: Boolean) {
        val newConfig = _config.value.copy(includeUppercase = enabled)
        _config.value = newConfig
        regenerate(newConfig)
    }

    fun toggleLowercase(enabled: Boolean) {
        val newConfig = _config.value.copy(includeLowercase = enabled)
        _config.value = newConfig
        regenerate(newConfig)
    }

    fun toggleNumbers(enabled: Boolean) {
        val newConfig = _config.value.copy(includeNumbers = enabled)
        _config.value = newConfig
        regenerate(newConfig)
    }

    fun toggleSymbols(enabled: Boolean) {
        val newConfig = _config.value.copy(includeSymbols = enabled)
        _config.value = newConfig
        regenerate(newConfig)
    }

    fun toggleAvoidAmbiguous(avoid: Boolean) {
        val newConfig = _config.value.copy(avoidAmbiguous = avoid)
        _config.value = newConfig
        regenerate(newConfig)
    }

    fun regenerate(customConfig: GeneratorConfig? = null) {
        val cfg = customConfig ?: _config.value
        _result.value = GeneratorEngine.generate(cfg)
    }

    fun copyToClipboard(label: String = "Generated Password") {
        clipboardSecurityManager.copySensitiveText(label, _result.value.value, timeoutSeconds = 45)
    }
}
