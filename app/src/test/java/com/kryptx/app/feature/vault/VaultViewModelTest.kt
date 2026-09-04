package com.kryptx.app.feature.vault

import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.security.VaultSessionManager
import com.kryptx.app.fake.FakeClipboardSecurityManager
import com.kryptx.app.fake.FakeVaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeVaultRepository: FakeVaultRepository
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var fakeClipboard: FakeClipboardSecurityManager
    private lateinit var viewModel: VaultViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeVaultRepository = FakeVaultRepository()
        sessionManager = VaultSessionManager(testScope)
        fakeClipboard = FakeClipboardSecurityManager()
        viewModel = VaultViewModel(fakeVaultRepository, sessionManager, fakeClipboard)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSaveAndFilterItems() = runTest(testDispatcher) {
        val item1 = VaultItem(
            id = "1",
            title = "GitHub",
            type = ItemType.LOGIN,
            username = "octocat",
            password = "securePassword123!",
            isFavorite = true
        )
        val item2 = VaultItem(
            id = "2",
            title = "Chase Bank",
            type = ItemType.API_KEY,
            apiKey = "key_123456789"
        )

        viewModel.saveItem(item1) {}
        viewModel.saveItem(item2) {}
        testScheduler.runCurrent()

        // Verify raw items count
        assertEquals(2, viewModel.rawItems.value.size)

        // Filter by category
        viewModel.selectCategory(ItemType.LOGIN)
        testScheduler.runCurrent()
        assertEquals(1, viewModel.filteredItems.value.size)
        assertEquals("GitHub", viewModel.filteredItems.value.first().title)

        // Search query filter
        viewModel.selectCategory(null)
        viewModel.updateSearchQuery("Chase")
        testScheduler.runCurrent()
        assertEquals(1, viewModel.filteredItems.value.size)
        assertEquals("Chase Bank", viewModel.filteredItems.value.first().title)

        // Favorites filter
        assertEquals(1, viewModel.favoriteItems.value.size)
        assertEquals("GitHub", viewModel.favoriteItems.value.first().title)
    }

    @Test
    fun testToggleFavorite() = runTest(testDispatcher) {
        val item = VaultItem(id = "fav_1", title = "Netflix", isFavorite = false)
        viewModel.saveItem(item) {}
        testScheduler.runCurrent()

        viewModel.toggleFavorite("fav_1")
        testScheduler.runCurrent()

        assertTrue(fakeVaultRepository.getItemById("fav_1")?.isFavorite == true)
    }

    @Test
    fun testCopySecretDelegatesToClipboardManager() {
        viewModel.copySecret("Password", "SuperSecretCode99", 30)
        assertEquals("Password", fakeClipboard.lastCopiedLabel)
        assertEquals("SuperSecretCode99", fakeClipboard.lastCopiedText)
        assertEquals(30, fakeClipboard.lastTimeoutSeconds)
    }

    @Test
    fun testDeleteItem() = runTest(testDispatcher) {
        val item = VaultItem(id = "del_1", title = "Obsolete Account")
        viewModel.saveItem(item) {}
        testScheduler.runCurrent()

        var deleted = false
        viewModel.deleteItem("del_1") { deleted = true }
        testScheduler.runCurrent()

        assertTrue(deleted)
        assertEquals(0, viewModel.rawItems.value.size)
    }

    @Test
    fun testDeleteItemWithUndoAndRestore() = runTest(testDispatcher) {
        val item = VaultItem(id = "undo_1", title = "Proton Mail", username = "user@pm.me")
        viewModel.saveItem(item) {}
        testScheduler.runCurrent()
        assertEquals(1, viewModel.rawItems.value.size)

        var deletedItemTitle: String? = null
        viewModel.deleteItemWithUndo("undo_1") { deleted ->
            deletedItemTitle = deleted?.title
        }
        testScheduler.runCurrent()

        assertEquals("Proton Mail", deletedItemTitle)
        assertEquals(0, viewModel.rawItems.value.size)

        var restoredTitle: String? = null
        viewModel.undoLastDelete { restored ->
            restoredTitle = restored.title
        }
        testScheduler.runCurrent()

        assertEquals("Proton Mail", restoredTitle)
        assertEquals(1, viewModel.rawItems.value.size)
        assertEquals("undo_1", viewModel.rawItems.value.first().id)
    }

    @Test
    fun testLockVault() {
        sessionManager.unlock(ByteArray(32) { 1 })
        assertTrue(sessionManager.isUnlocked.value)

        viewModel.lockVault()
        assertFalse(sessionManager.isUnlocked.value)
        assertTrue(fakeClipboard.isCleared)
    }

    @Test
    fun testDashboardCategoryAndTotpCounting() = runTest(testDispatcher) {
        val login1 = VaultItem(id = "1", title = "Google", type = ItemType.LOGIN, totpSecret = "JBSWY3DPEHPK3PXP")
        val login2 = VaultItem(id = "2", title = "GitHub", type = ItemType.LOGIN, totpSecret = "")
        val card = VaultItem(id = "3", title = "Visa", type = ItemType.CREDIT_CARD, cardNumber = "4111222233334444")
        val note = VaultItem(id = "4", title = "Recovery Keys", type = ItemType.SECURE_NOTE, notes = "Secret note text")
        val wifi = VaultItem(id = "5", title = "Home Wi-Fi", type = ItemType.WIFI, wifiSsid = "HomeNet")

        viewModel.saveItem(login1) {}
        viewModel.saveItem(login2) {}
        viewModel.saveItem(card) {}
        viewModel.saveItem(note) {}
        viewModel.saveItem(wifi) {}
        testScheduler.runCurrent()

        val all = viewModel.rawItems.value
        assertEquals(5, all.size)

        val loginsCount = all.count { it.type == ItemType.LOGIN }
        val cardsCount = all.count { it.type == ItemType.CREDIT_CARD }
        val notesCount = all.count { it.type == ItemType.SECURE_NOTE }
        val totpCount = all.count { it.totpSecret.isNotBlank() }

        assertEquals(2, loginsCount)
        assertEquals(1, cardsCount)
        assertEquals(1, notesCount)
        assertEquals(1, totpCount)
    }

    @Test
    fun testSearchFilteringMatchesMultipleFields() = runTest(testDispatcher) {
        val item1 = VaultItem(
            id = "s1",
            title = "Personal Email",
            type = ItemType.LOGIN,
            username = "alice@example.com",
            website = "https://mail.example.com",
            tags = listOf("work", "important")
        )
        val item2 = VaultItem(
            id = "s2",
            title = "Crypto Wallet",
            type = ItemType.CUSTOM,
            notes = "Stored in cold storage vault"
        )

        viewModel.saveItem(item1) {}
        viewModel.saveItem(item2) {}
        testScheduler.runCurrent()

        // Match by username
        viewModel.updateSearchQuery("alice")
        testScheduler.runCurrent()
        assertEquals(1, viewModel.filteredItems.value.size)
        assertEquals("Personal Email", viewModel.filteredItems.value.first().title)

        // Match by tag
        viewModel.updateSearchQuery("important")
        testScheduler.runCurrent()
        assertEquals(1, viewModel.filteredItems.value.size)

        // Match by note content
        viewModel.updateSearchQuery("cold storage")
        testScheduler.runCurrent()
        assertEquals(1, viewModel.filteredItems.value.size)
        assertEquals("Crypto Wallet", viewModel.filteredItems.value.first().title)

        // Clear query
        viewModel.updateSearchQuery("")
        testScheduler.runCurrent()
        assertEquals(2, viewModel.filteredItems.value.size)
    }

    @Test
    fun testStorageRatioMath() {
        val emptyTotal = 0
        val emptyRatio = if (emptyTotal <= 0) 0.05f else (0f / emptyTotal).coerceIn(0.1f, 1f)
        assertEquals(0.05f, emptyRatio, 0.001f)

        val total = 10
        val itemsCount = 3
        val ratio = (itemsCount.toFloat() / total.toFloat()).coerceIn(0.1f, 1f)
        assertEquals(0.3f, ratio, 0.001f)

        val fullCount = 10
        val fullRatio = (fullCount.toFloat() / total.toFloat()).coerceIn(0.1f, 1f)
        assertEquals(1.0f, fullRatio, 0.001f)
    }

    @Test
    fun testExpirationCalculationsOnVaultItem() {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60L * 60L * 1000L

        val nonExpiredItem = VaultItem(
            title = "Active Pass",
            expiresAt = now + (30L * dayMs)
        )
        assertFalse(nonExpiredItem.isExpired)
        assertTrue((nonExpiredItem.daysUntilExpiration ?: 0) >= 29)

        val expiredItem = VaultItem(
            title = "Old Pass",
            expiresAt = now - (5L * dayMs)
        )
        assertTrue(expiredItem.isExpired)
        assertTrue((expiredItem.daysUntilExpiration ?: 0) <= 0)
    }

    @Test
    fun testVisibleCategoriesAndMinimalistMode() = runTest(testDispatcher) {
        val fakePrefs = com.kryptx.app.fake.FakePreferencesRepository()
        val customVm = VaultViewModel(fakeVaultRepository, sessionManager, fakeClipboard, preferencesRepository = fakePrefs)

        // Default all categories visible
        assertTrue(customVm.visibleCategories.value.contains(ItemType.LOGIN))
        assertTrue(customVm.visibleCategories.value.contains(ItemType.CREDIT_CARD))

        // Toggle minimalist mode
        assertFalse(customVm.isMinimalistMode.value)
        customVm.toggleMinimalistMode()
        testScheduler.runCurrent()
        assertTrue(customVm.isMinimalistMode.value)
    }

    @Test
    fun testMultiSelectAndBatchOperations() = runTest(testDispatcher) {
        val item1 = VaultItem(id = "batch_1", title = "Alpha", isFavorite = false)
        val item2 = VaultItem(id = "batch_2", title = "Beta", isFavorite = false)
        viewModel.saveItem(item1) {}
        viewModel.saveItem(item2) {}
        testScheduler.runCurrent()

        // Selection mode initially false
        assertFalse(viewModel.isSelectionMode.value)
        assertEquals(0, viewModel.selectedItemIds.value.size)

        // Select item 1
        viewModel.toggleSelectItem("batch_1")
        testScheduler.runCurrent()
        assertTrue(viewModel.isSelectionMode.value)
        assertEquals(1, viewModel.selectedItemIds.value.size)
        assertTrue(viewModel.selectedItemIds.value.contains("batch_1"))

        // Select all
        viewModel.selectAllFiltered()
        testScheduler.runCurrent()
        assertEquals(2, viewModel.selectedItemIds.value.size)

        // Batch toggle favorite
        viewModel.batchToggleFavorite()
        testScheduler.runCurrent()
        assertTrue(fakeVaultRepository.getItemById("batch_1")?.isFavorite == true)
        assertTrue(fakeVaultRepository.getItemById("batch_2")?.isFavorite == true)
        assertFalse(viewModel.isSelectionMode.value)

        // Select all and batch move to trash
        viewModel.selectAllFiltered()
        testScheduler.runCurrent()
        var deletedCount = 0
        viewModel.batchMoveToTrash { count -> deletedCount = count }
        testScheduler.runCurrent()
        assertEquals(2, deletedCount)
        assertFalse(viewModel.isSelectionMode.value)
    }

    @Test
    fun testSortOptions() = runTest(testDispatcher) {
        val itemA = VaultItem(id = "sort_a", title = "Amazon", password = "123", createdAt = 1000L, lastUsedAt = 500L)
        val itemZ = VaultItem(id = "sort_z", title = "Zendesk", password = "SuperLongComplexPassword!@#$999", createdAt = 2000L, lastUsedAt = 2000L)
        viewModel.saveItem(itemA) {}
        viewModel.saveItem(itemZ) {}
        testScheduler.runCurrent()

        // Test Alphabetical A-Z
        viewModel.setSortOption(VaultViewModel.SortOption.NAME_ASC)
        testScheduler.runCurrent()
        assertEquals("Amazon", viewModel.filteredItems.value.first().title)

        // Test Alphabetical Z-A
        viewModel.setSortOption(VaultViewModel.SortOption.NAME_DESC)
        testScheduler.runCurrent()
        assertEquals("Zendesk", viewModel.filteredItems.value.first().title)

        // Test Weakest First
        viewModel.setSortOption(VaultViewModel.SortOption.WEAKEST_FIRST)
        testScheduler.runCurrent()
        assertEquals("Amazon", viewModel.filteredItems.value.first().title)

        // Test Newest First
        viewModel.setSortOption(VaultViewModel.SortOption.NEWEST_FIRST)
        testScheduler.runCurrent()
        assertEquals("Zendesk", viewModel.filteredItems.value.first().title)
    }
}

