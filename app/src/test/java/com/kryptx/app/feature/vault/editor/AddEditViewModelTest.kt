package com.kryptx.app.feature.vault.editor

import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.security.ActivityLogManager
import com.kryptx.app.core.security.VaultSessionManager
import com.kryptx.app.fake.FakeAttachmentManager
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeVaultRepository: FakeVaultRepository
    private lateinit var sessionManager: VaultSessionManager
    private lateinit var fakeAttachmentManager: FakeAttachmentManager
    private lateinit var mockActivityLogManager: ActivityLogManager
    private lateinit var viewModel: AddEditViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeVaultRepository = FakeVaultRepository()
        sessionManager = VaultSessionManager(testScope)
        fakeAttachmentManager = FakeAttachmentManager()
        mockActivityLogManager = mock()

        viewModel = AddEditViewModel(
            vaultRepository = fakeVaultRepository,
            sessionManager = sessionManager,
            attachmentManager = fakeAttachmentManager,
            activityLogManager = mockActivityLogManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitNewItem_populatesEditorState() {
        viewModel.initNewItem(ItemType.LOGIN)
        val state = viewModel.editorState.value
        assertNotNull(state)
        assertEquals(ItemType.LOGIN, state?.itemType)
    }

    @Test
    fun testAutoLockClearsAndZeroesEditorState() = runTest(testDispatcher) {
        val item = VaultItem(
            id = "test_item_lock",
            title = "Test Credentials",
            type = ItemType.LOGIN,
            username = "admin",
            password = "SuperSecretPassword123!"
        )
        viewModel.loadItem(item)
        val loginState = viewModel.editorState.value as? ItemEditorState.Login
        assertNotNull(loginState)
        assertEquals("SuperSecretPassword123!", loginState?.password?.value)

        // Lock the session
        sessionManager.lock()
        testScheduler.runCurrent()

        // Verify editor state was zeroized and nullified
        assertNull(viewModel.editorState.value)
    }

    @Test
    fun testDiscardChangesClearsEditorState() = runTest(testDispatcher) {
        viewModel.initNewItem(ItemType.LOGIN)
        assertNotNull(viewModel.editorState.value)

        val context = mock<android.content.Context>()
        viewModel.discardChanges(context)
        testScheduler.runCurrent()

        assertNull(viewModel.editorState.value)
    }
}
