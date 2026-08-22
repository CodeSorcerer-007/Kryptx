package com.kryptx.app.feature.vault

import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.security.VaultSessionManager
import com.kryptx.app.fake.FakeClipboardSecurityManager
import com.kryptx.app.fake.FakeVaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Screen interaction and UI state transition test for VaultDashboardScreen.
 * Tests Category tab selection, Search query filtering, Swipe-to-delete with undo, and Favorite toggles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VaultDashboardScreenTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeRepository: FakeVaultRepository
    private lateinit var fakeClipboard: FakeClipboardSecurityManager
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var viewModel: VaultViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeVaultRepository()
        fakeClipboard = FakeClipboardSecurityManager()
        sessionManager = VaultSessionManager(testScope)
        viewModel = VaultViewModel(
            vaultRepository = fakeRepository,
            sessionManager = sessionManager,
            clipboardSecurityManager = fakeClipboard
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `category badge selection filters dashboard items correctly`() = runTest(testDispatcher) {
        val loginItem = VaultItem(id = "1", title = "Netflix", type = ItemType.LOGIN)
        val cardItem = VaultItem(id = "2", title = "Visa Platinum", type = ItemType.CREDIT_CARD)
        val noteItem = VaultItem(id = "3", title = "Personal Pin", type = ItemType.SECURE_NOTE)

        fakeRepository.saveItem(loginItem)
        fakeRepository.saveItem(cardItem)
        fakeRepository.saveItem(noteItem)
        advanceUntilIdle()

        // Initial state: all 3 items displayed
        assertEquals(3, viewModel.filteredItems.first().size)

        // Select LOGIN category
        viewModel.selectCategory(ItemType.LOGIN)
        advanceUntilIdle()
        val loginFiltered = viewModel.filteredItems.first()
        assertEquals(1, loginFiltered.size)
        assertEquals("Netflix", loginFiltered[0].title)

        // Select CREDIT_CARD category
        viewModel.selectCategory(ItemType.CREDIT_CARD)
        advanceUntilIdle()
        val cardFiltered = viewModel.filteredItems.first()
        assertEquals(1, cardFiltered.size)
        assertEquals("Visa Platinum", cardFiltered[0].title)

        // Clear category selection
        viewModel.selectCategory(null)
        advanceUntilIdle()
        assertEquals(3, viewModel.filteredItems.first().size)
    }

    @Test
    fun `search query filtering matches title, username, website and notes`() = runTest(testDispatcher) {
        val item1 = VaultItem(id = "1", title = "Spotify Music", username = "spotify_user", type = ItemType.LOGIN)
        val item2 = VaultItem(id = "2", title = "AWS Cloud Console", notes = "Production root access", type = ItemType.LOGIN)
        val item3 = VaultItem(id = "3", title = "Bank of America", website = "https://bankofamerica.com", type = ItemType.BANK_ACCOUNT)

        fakeRepository.saveItem(item1)
        fakeRepository.saveItem(item2)
        fakeRepository.saveItem(item3)
        advanceUntilIdle()

        // Search by username
        viewModel.updateSearchQuery("spotify_user")
        advanceUntilIdle()
        assertEquals(1, viewModel.filteredItems.first().size)
        assertEquals("Spotify Music", viewModel.filteredItems.first()[0].title)

        // Search by notes
        viewModel.updateSearchQuery("root access")
        advanceUntilIdle()
        assertEquals(1, viewModel.filteredItems.first().size)
        assertEquals("AWS Cloud Console", viewModel.filteredItems.first()[0].title)

        // Search by website
        viewModel.updateSearchQuery("bankofamerica")
        advanceUntilIdle()
        assertEquals(1, viewModel.filteredItems.first().size)
        assertEquals("Bank of America", viewModel.filteredItems.first()[0].title)
    }

    @Test
    fun `swipe-to-delete with undo restores deleted item to dashboard`() = runTest(testDispatcher) {
        val item = VaultItem(id = "del_1", title = "Item To Delete", type = ItemType.LOGIN)
        fakeRepository.saveItem(item)
        advanceUntilIdle()
        assertEquals(1, viewModel.rawItems.first().size)

        // Delete with undo
        var deletedItem: VaultItem? = null
        viewModel.deleteItemWithUndo("del_1") { deletedItem = it }
        advanceUntilIdle()
        assertNotNull(deletedItem)
        assertEquals(0, fakeRepository.getItems().first().size)

        // Undo delete
        var restoredItem: VaultItem? = null
        viewModel.undoLastDelete { restoredItem = it }
        advanceUntilIdle()
        assertNotNull(restoredItem)
        assertEquals("del_1", restoredItem!!.id)
        assertEquals(1, fakeRepository.getItems().first().size)
    }

    @Test
    fun `favorite toggle updates favorite status reactively`() = runTest(testDispatcher) {
        val item = VaultItem(id = "fav_1", title = "Top Secret", isFavorite = false)
        fakeRepository.saveItem(item)
        advanceUntilIdle()

        viewModel.toggleFavorite("fav_1")
        advanceUntilIdle()
        val updated = fakeRepository.getItemById("fav_1")
        assertNotNull(updated)
        assertTrue(updated!!.isFavorite)
    }
}
