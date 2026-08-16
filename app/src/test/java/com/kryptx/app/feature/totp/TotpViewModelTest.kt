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
}
