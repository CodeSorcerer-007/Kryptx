package com.kryptx.app.feature.totp

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
class TotpViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeVaultRepository: FakeVaultRepository
    private lateinit var fakeClipboard: FakeClipboardSecurityManager
    private lateinit var viewModel: TotpViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeVaultRepository = FakeVaultRepository()
        fakeClipboard = FakeClipboardSecurityManager()
        viewModel = TotpViewModel(fakeVaultRepository, fakeClipboard)
    }

    @After
    fun tearDown() {
        viewModel.stopTicker()
        Dispatchers.resetMain()
    }

    @Test
    fun testTotpAccountsLoadedFromVault() = runTest(testDispatcher) {
        val itemWithTotp = VaultItem(
            id = "totp_1",
            title = "AWS Cloud",
            type = ItemType.LOGIN,
            username = "admin@aws.com",
            totpSecret = "JBSWY3DPEHPK3PXP"
        )
        val itemWithoutTotp = VaultItem(
            id = "no_totp",
            title = "Basic Site",
            type = ItemType.LOGIN,
            username = "user"
        )

        fakeVaultRepository.saveItem(itemWithTotp)
        fakeVaultRepository.saveItem(itemWithoutTotp)
        testScheduler.runCurrent()

        assertEquals(1, viewModel.totpAccounts.value.size)
        val account = viewModel.totpAccounts.value.first()
        assertEquals("AWS Cloud", account.item.title)
    }

    @Test
    fun testCopyTotpCode() = runTest(testDispatcher) {
        val itemWithTotp = VaultItem(
            id = "totp_2",
            title = "Google",
            type = ItemType.LOGIN,
            totpSecret = "JBSWY3DPEHPK3PXP"
        )
        fakeVaultRepository.saveItem(itemWithTotp)
        testScheduler.runCurrent()

        val account = viewModel.totpAccounts.value.first()
        viewModel.copyCode(account)
        assertEquals("Google", fakeClipboard.lastCopiedLabel)
    }

    @Test
    fun testSearchFilterTotpAccounts() = runTest(testDispatcher) {
        val item1 = VaultItem(id = "1", title = "GitHub", type = ItemType.LOGIN, totpSecret = "JBSWY3DPEHPK3PXP")
        val item2 = VaultItem(id = "2", title = "Amazon AWS", type = ItemType.LOGIN, totpSecret = "JBSWY3DPEHPK3PXP")
        fakeVaultRepository.saveItem(item1)
        fakeVaultRepository.saveItem(item2)
        testScheduler.runCurrent()

        assertEquals(2, viewModel.filteredTotpAccounts.value.size)

        viewModel.updateSearchQuery("Git")
        testScheduler.runCurrent()

        assertEquals(1, viewModel.filteredTotpAccounts.value.size)
        assertEquals("GitHub", viewModel.filteredTotpAccounts.value.first().item.title)
    }

    @Test
    fun testCategoryFilterPersonalExcludesWorkNotesAndUsername() = runTest(testDispatcher) {
        val workByTitle = VaultItem(id = "1", title = "Work Slack", type = ItemType.LOGIN, totpSecret = "JBSWY3DPEHPK3PXP")
        val workByNotes = VaultItem(id = "2", title = "Internal Portal", type = ItemType.LOGIN, notes = "for work use only", totpSecret = "JBSWY3DPEHPK3PXP")
        val workByUsername = VaultItem(id = "3", title = "VPN Gateway", type = ItemType.LOGIN, username = "john.work", totpSecret = "JBSWY3DPEHPK3PXP")
        val personalItem = VaultItem(id = "4", title = "Personal Steam", type = ItemType.LOGIN, totpSecret = "JBSWY3DPEHPK3PXP")

        fakeVaultRepository.saveItem(workByTitle)
        fakeVaultRepository.saveItem(workByNotes)
        fakeVaultRepository.saveItem(workByUsername)
        fakeVaultRepository.saveItem(personalItem)
        testScheduler.runCurrent()

        viewModel.selectCategory("WORK")
        testScheduler.runCurrent()
        assertEquals(3, viewModel.filteredTotpAccounts.value.size)

        viewModel.selectCategory("PERSONAL")
        testScheduler.runCurrent()
        assertEquals(1, viewModel.filteredTotpAccounts.value.size)
        assertEquals("Personal Steam", viewModel.filteredTotpAccounts.value.first().item.title)
    }

    @Test
    fun testCategoryFilterFavorites() = runTest(testDispatcher) {
        val item1 = VaultItem(id = "1", title = "GitHub", type = ItemType.LOGIN, totpSecret = "JBSWY3DPEHPK3PXP", isFavorite = true)
        val item2 = VaultItem(id = "2", title = "Discord", type = ItemType.LOGIN, totpSecret = "JBSWY3DPEHPK3PXP", isFavorite = false)
        fakeVaultRepository.saveItem(item1)
        fakeVaultRepository.saveItem(item2)
        testScheduler.runCurrent()

        viewModel.selectCategory("FAVORITES")
        testScheduler.runCurrent()

        assertEquals(1, viewModel.filteredTotpAccounts.value.size)
        assertEquals("GitHub", viewModel.filteredTotpAccounts.value.first().item.title)
    }

    @Test
    fun testTickerAutoStartedInInit() = runTest(testDispatcher) {
        val itemWithTotp = VaultItem(
            id = "totp_ticker",
            title = "Live Ticker Test",
            type = ItemType.LOGIN,
            totpSecret = "JBSWY3DPEHPK3PXP"
        )
        fakeVaultRepository.saveItem(itemWithTotp)
        testScheduler.runCurrent()

        assertEquals(1, viewModel.totpAccounts.value.size)
        val initialAccount = viewModel.totpAccounts.value.first()
        org.junit.Assert.assertNotNull(initialAccount.code)

        // Advance 1000ms - ticker launched in init emits new tick
        testScheduler.advanceTimeBy(1001L)
        testScheduler.runCurrent()

        val updatedAccount = viewModel.totpAccounts.value.first()
        org.junit.Assert.assertNotNull(updatedAccount.code)
    }

    @Test
    fun testAddTotpReconcilesWithExistingItem() = runTest(testDispatcher) {
        val existingItem = VaultItem(
            id = "existing_github",
            title = "GitHub",
            type = ItemType.LOGIN,
            username = "octocat",
            password = "SecurePassword123!"
        )
        fakeVaultRepository.saveItem(existingItem)
        testScheduler.runCurrent()

        var successCalled = false
        viewModel.addTotpManual("GitHub", "octocat", "JBSWY3DPEHPK3PXP") {
            successCalled = true
        }
        testScheduler.runCurrent()

        org.junit.Assert.assertTrue(successCalled)
        assertEquals(1, viewModel.totpAccounts.value.size)
        val reconciledItem = viewModel.totpAccounts.value.first().item
        assertEquals("existing_github", reconciledItem.id)
        assertEquals("JBSWY3DPEHPK3PXP", reconciledItem.totpSecret)
        assertEquals("SecurePassword123!", reconciledItem.password)
    }
}
