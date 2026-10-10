package com.kryptx.app.feature.search

import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.fake.FakeClipboardSecurityManager
import com.kryptx.app.fake.FakeVaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private lateinit var fakeVaultRepository: FakeVaultRepository
    private lateinit var fakeClipboard: FakeClipboardSecurityManager
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        fakeVaultRepository = FakeVaultRepository()
        fakeClipboard = FakeClipboardSecurityManager()
        viewModel = SearchViewModel(fakeVaultRepository, fakeClipboard)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun <T> kotlinx.coroutines.flow.StateFlow<T>.awaitValue(timeoutMs: Long = 3000L, condition: (T) -> Boolean): T {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (condition(value)) return value
            kotlinx.coroutines.delay(20)
        }
        return value
    }

    @Test
    fun testSearchQueryMatching() = kotlinx.coroutines.runBlocking {
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) { viewModel.searchResults.collect { } }
        val item1 = VaultItem(id = "1", title = "ProtonMail", username = "kryptx@proton.me", website = "https://proton.me")
        val item2 = VaultItem(id = "2", title = "DigitalOcean", apiKey = "do_token_xyz")

        fakeVaultRepository.saveItem(item1)
        fakeVaultRepository.saveItem(item2)
        viewModel.searchResults.awaitValue { it.size == 2 }

        viewModel.onQueryChanged("proton")
        val protonResults = viewModel.searchResults.awaitValue { it.size == 1 && it.first().title == "ProtonMail" }
        assertEquals(1, protonResults.size)
        assertEquals("ProtonMail", protonResults.first().title)

        viewModel.onQueryChanged("ocean")
        val oceanResults = viewModel.searchResults.awaitValue { it.size == 1 && it.first().title == "DigitalOcean" }
        assertEquals(1, oceanResults.size)
        assertEquals("DigitalOcean", oceanResults.first().title)

        viewModel.onQueryChanged("")
        val allResults = viewModel.searchResults.awaitValue { it.size == 2 }
        assertEquals(2, allResults.size)
        
        job.cancel()
    }

    @Test
    fun testWeakFilterMatching() = kotlinx.coroutines.runBlocking {
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) { viewModel.searchResults.collect { } }
        val strongItem = VaultItem(id = "1", title = "Strong", type = ItemType.LOGIN, password = "K9#xP!2QmZ8@vL4&")
        val weakItem = VaultItem(id = "2", title = "Weak", type = ItemType.LOGIN, password = "123")

        fakeVaultRepository.saveItem(strongItem)
        fakeVaultRepository.saveItem(weakItem)
        viewModel.searchResults.awaitValue { it.size == 2 }

        viewModel.selectFilter("WEAK")
        val weakResults = viewModel.searchResults.awaitValue { it.size == 1 && it.first().title == "Weak" }

        assertEquals(1, weakResults.size)
        assertEquals("Weak", weakResults.first().title)
        
        job.cancel()
    }

    @Test
    fun testCopySecret() {
        viewModel.copySecret("Email", "test@kryptx.app")
        assertEquals("Email", fakeClipboard.lastCopiedLabel)
        assertEquals("test@kryptx.app", fakeClipboard.lastCopiedText)
    }

    @Test
    fun testSelectFilterSetsIsSearching() {
        viewModel.selectFilter("FAVORITES")
        assertEquals(true, viewModel.isSearching.value)
    }
}
