package com.kryptx.app.feature.generator

import com.kryptx.app.core.model.GeneratorConfig
import com.kryptx.app.core.model.GeneratorMode
import com.kryptx.app.core.model.UsernameStyle
import com.kryptx.app.fake.FakeClipboardSecurityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeneratorViewModelTest {

    private lateinit var fakeClipboard: FakeClipboardSecurityManager
    private lateinit var viewModel: GeneratorViewModel

    @Before
    fun setUp() {
        fakeClipboard = FakeClipboardSecurityManager()
        viewModel = GeneratorViewModel(fakeClipboard)
    }

    @Test
    fun testInitialState() {
        assertNotNull(viewModel.result.value)
        assertEquals(GeneratorMode.PASSWORD, viewModel.config.value.mode)
    }

    @Test
    fun testUpdatePasswordLength() {
        viewModel.updatePasswordLength(28)
        assertEquals(28, viewModel.config.value.passwordLength)
        assertEquals(28, viewModel.result.value.value.length)
    }

    @Test
    fun testSwitchToPassphraseMode() {
        viewModel.updateMode(GeneratorMode.PASSPHRASE)
        viewModel.updateWordCount(5)
        assertEquals(GeneratorMode.PASSPHRASE, viewModel.config.value.mode)
        assertEquals(5, viewModel.config.value.wordCount)
        assertTrue(viewModel.result.value.value.contains("-"))
    }

    @Test
    fun testSwitchToPinMode() {
        viewModel.updateMode(GeneratorMode.PIN)
        viewModel.updatePinLength(8)
        assertEquals(8, viewModel.result.value.value.length)
        assertTrue(viewModel.result.value.value.all { it.isDigit() })
    }

    @Test
    fun testSwitchToUsernameMode() {
        viewModel.updateMode(GeneratorMode.USERNAME)
        viewModel.updateUsernameStyle(UsernameStyle.ANONYMOUS_ALPHANUMERIC)
        assertTrue(viewModel.result.value.value.startsWith("kryptx_"))
    }

    @Test
    fun testCopyResult() {
        val currentSecret = viewModel.result.value.value
        viewModel.copyToClipboard("Test Secret")
        assertEquals("Test Secret", fakeClipboard.lastCopiedLabel)
        assertEquals(currentSecret, fakeClipboard.lastCopiedText)
    }
}
