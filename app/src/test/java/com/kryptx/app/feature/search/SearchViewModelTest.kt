package com.kryptx.app.feature.search

import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.fake.FakeClipboardSecurityManager
import com.kryptx.app.fake.FakeVaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeVaultRepository: FakeVaultRepository
    private lateinit var fakeClipboard: FakeClipboardSecurityManager
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeVaultRepository = FakeVaultRepository()
        fakeClipboard = FakeClipboardSecurityManager()
        viewModel = SearchViewModel(fakeVaultRepository, fakeClipboard)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSearchQueryMatching() = runTest(testDispatcher) {
        val item1 = VaultItem(id = "1", title = "ProtonMail", username = "kryptx@proton.me", website = "https://proton.me")
        val item2 = VaultItem(id = "2", title = "DigitalOcean", apiKey = "do_token_xyz")

        fakeVaultRepository.saveItem(item1)
        fakeVaultRepository.saveItem(item2)
        testScheduler.runCurrent()

        viewModel.onQueryChanged("proton")
        testScheduler.runCurrent()
        assertEquals(1, viewModel.searchResults.value.size)
        assertEquals("ProtonMail", viewModel.searchResults.value.first().title)

        viewModel.onQueryChanged("ocean")
        testScheduler.runCurrent()
        assertEquals(1, viewModel.searchResults.value.size)
        assertEquals("DigitalOcean", viewModel.searchResults.value.first().title)

        viewModel.onQueryChanged("")
        testScheduler.runCurrent()
        assertEquals(2, viewModel.searchResults.value.size)
    }

    @Test
    fun testCopySecret() {
        viewModel.copySecret("Email", "test@kryptx.app")
        assertEquals("Email", fakeClipboard.lastCopiedLabel)
        assertEquals("test@kryptx.app", fakeClipboard.lastCopiedText)
    }
}
