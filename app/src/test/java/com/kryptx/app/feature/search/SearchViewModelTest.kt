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

    @Test
    fun testSearchQueryMatching() = kotlinx.coroutines.runBlocking {
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) { viewModel.searchResults.collect { } }
        val item1 = VaultItem(id = "1", title = "ProtonMail", username = "kryptx@proton.me", website = "https://proton.me")
        val item2 = VaultItem(id = "2", title = "DigitalOcean", apiKey = "do_token_xyz")

        fakeVaultRepository.saveItem(item1)
        fakeVaultRepository.saveItem(item2)
        kotlinx.coroutines.delay(400)

        viewModel.onQueryChanged("proton")
        kotlinx.coroutines.delay(400)
        assertEquals(1, viewModel.searchResults.value.size)
        assertEquals("ProtonMail", viewModel.searchResults.value.first().title)

        viewModel.onQueryChanged("ocean")
        kotlinx.coroutines.delay(400)
        assertEquals(1, viewModel.searchResults.value.size)
        assertEquals("DigitalOcean", viewModel.searchResults.value.first().title)

        viewModel.onQueryChanged("")
        kotlinx.coroutines.delay(400)
        assertEquals(2, viewModel.searchResults.value.size)
        
        job.cancel()
    }

    @Test
    fun testWeakFilterMatching() = kotlinx.coroutines.runBlocking {
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) { viewModel.searchResults.collect { } }
        val strongItem = VaultItem(id = "1", title = "Strong", type = ItemType.LOGIN, password = "K9#xP!2QmZ8@vL4&")
        val weakItem = VaultItem(id = "2", title = "Weak", type = ItemType.LOGIN, password = "123")

        fakeVaultRepository.saveItem(strongItem)
        fakeVaultRepository.saveItem(weakItem)
        kotlinx.coroutines.delay(400)

        viewModel.selectFilter("WEAK")
        kotlinx.coroutines.delay(400)

        assertEquals(1, viewModel.searchResults.value.size)
        assertEquals("Weak", viewModel.searchResults.value.first().title)
        
        job.cancel()
    }

    @Test
    fun testCopySecret() {
        viewModel.copySecret("Email", "test@kryptx.app")
        assertEquals("Email", fakeClipboard.lastCopiedLabel)
        assertEquals("test@kryptx.app", fakeClipboard.lastCopiedText)
    }
}
