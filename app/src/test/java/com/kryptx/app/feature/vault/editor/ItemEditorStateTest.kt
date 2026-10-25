package com.kryptx.app.feature.vault.editor

import com.kryptx.app.core.model.CustomField
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.fake.FakePreferencesRepository
import com.kryptx.app.fake.FakeVaultRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class ItemEditorStateTest {

    @Test
    fun testForNewItem_bankAccount_hasCorrectItemTypeAndTemplateFields() {
        val state = ItemEditorState.forNewItem(ItemType.BANK_ACCOUNT)
        assertEquals(ItemType.BANK_ACCOUNT, state.itemType)
        assertTrue(state is ItemEditorState.Custom)

        val labels = state.customFields.map { it.label }
        assertEquals(listOf("Bank Name", "Account Number", "Routing Number", "SWIFT / BIC"), labels)

        val accountField = state.customFields.first { it.label == "Account Number" }
        assertTrue("Account number should be masked as secured", accountField.isSecured)
    }

    @Test
    fun testForNewItem_cryptoWallet_hasCorrectItemTypeAndTemplateFields() {
        val state = ItemEditorState.forNewItem(ItemType.CRYPTO_WALLET)
        assertEquals(ItemType.CRYPTO_WALLET, state.itemType)
        assertTrue(state is ItemEditorState.Custom)

        val labels = state.customFields.map { it.label }
        assertEquals(listOf("Address", "Seed Phrase", "Network"), labels)

        val seedField = state.customFields.first { it.label == "Seed Phrase" }
        assertTrue("Seed phrase should be masked as secured", seedField.isSecured)
    }

    @Test
    fun testForNewItem_sshKey_hasCorrectItemTypeAndTemplateFields() {
        val state = ItemEditorState.forNewItem(ItemType.SSH_KEY)
        assertEquals(ItemType.SSH_KEY, state.itemType)
        assertTrue(state is ItemEditorState.Custom)

        val labels = state.customFields.map { it.label }
        assertEquals(listOf("Public Key", "Private Key", "Host"), labels)

        val privateKeyField = state.customFields.first { it.label == "Private Key" }
        assertTrue("Private key should be masked as secured", privateKeyField.isSecured)
    }

    @Test
    fun testForNewItem_custom_hasCorrectItemType() {
        val state = ItemEditorState.forNewItem(ItemType.CUSTOM)
        assertEquals(ItemType.CUSTOM, state.itemType)
        assertTrue(state is ItemEditorState.Custom)
        assertTrue(state.customFields.isEmpty())
    }

    @Test
    fun testFromVaultItem_preservesExactItemType() {
        val types = listOf(ItemType.BANK_ACCOUNT, ItemType.CRYPTO_WALLET, ItemType.SSH_KEY, ItemType.CUSTOM)
        for (type in types) {
            val item = VaultItem(
                title = "Test Item for $type",
                type = type,
                customFields = listOf(CustomField(label = "Custom Label", value = "Custom Value"))
            )
            val state = ItemEditorState.fromVaultItem(item)
            assertEquals("ItemType must match for $type", type, state.itemType)
        }
    }

    @Test
    fun testToVaultItem_persistsExactItemType() {
        val types = listOf(ItemType.BANK_ACCOUNT, ItemType.CRYPTO_WALLET, ItemType.SSH_KEY, ItemType.CUSTOM)
        for (type in types) {
            val state = ItemEditorState.forNewItem(type)
            state.title.value = "Test $type"
            val savedItem = state.toVaultItem(null, null)
            assertEquals("toVaultItem must preserve $type", type, savedItem.type)
        }
    }

    @Test
    fun testIsDirtyRelativeTo_cleanNewItemWithTemplateFields_isNotDirty() {
        val state = ItemEditorState.forNewItem(ItemType.BANK_ACCOUNT)
        assertFalse("Clean new item with blank template fields should not be dirty", state.isDirtyRelativeTo(null))
    }

    @Test
    fun testIsDirtyRelativeTo_modifiedNewItem_isDirty() {
        val state = ItemEditorState.forNewItem(ItemType.BANK_ACCOUNT)
        state.title.value = "My Bank"
        assertTrue("Item with title entered should be dirty", state.isDirtyRelativeTo(null))
    }

    @Test
    fun testIsDirtyRelativeTo_cleanExistingItem_isNotDirty() {
        val item = VaultItem(
            title = "Existing Account",
            type = ItemType.BANK_ACCOUNT,
            customFields = listOf(CustomField(label = "Bank Name", value = "Chase")),
            updatedAt = 50000L
        )
        val state = ItemEditorState.fromVaultItem(item)
        assertFalse("Unmodified loaded item must not be dirty", state.isDirtyRelativeTo(item))
    }

    @Test
    fun testSwitchType_preservesItemTypeAndTagsAndAttachments() {
        val fakeVaultRepo = FakeVaultRepository()
        val sessionManager = com.kryptx.app.core.security.VaultSessionManager()
        val mockAttachmentManager = mock<com.kryptx.app.core.security.IAttachmentManager>()
        val mockLogManager = mock<com.kryptx.app.core.security.ActivityLogManager>()

        val viewModel = AddEditViewModel(
            vaultRepository = fakeVaultRepo,
            sessionManager = sessionManager,
            attachmentManager = mockAttachmentManager,
            activityLogManager = mockLogManager
        )

        viewModel.initNewItem(ItemType.LOGIN)
        assertEquals(ItemType.LOGIN, viewModel.editorState.value?.itemType)

        viewModel.editorState.value?.title?.value = "Cross Type Title"
        viewModel.editorState.value?.notes?.value = "Secret note across types"
        viewModel.editorState.value?.tags?.value = listOf("finance", "important")

        // Switch to BANK_ACCOUNT
        viewModel.switchType(ItemType.BANK_ACCOUNT)
        assertEquals(ItemType.BANK_ACCOUNT, viewModel.editorState.value?.itemType)
        assertEquals("Cross Type Title", viewModel.editorState.value?.title?.value)
        assertEquals("Secret note across types", viewModel.editorState.value?.notes?.value)
        assertEquals(listOf("finance", "important"), viewModel.editorState.value?.tags?.value)

        // Switch to CRYPTO_WALLET
        viewModel.switchType(ItemType.CRYPTO_WALLET)
        assertEquals(ItemType.CRYPTO_WALLET, viewModel.editorState.value?.itemType)
        assertEquals("Cross Type Title", viewModel.editorState.value?.title?.value)

        // Switch to SSH_KEY
        viewModel.switchType(ItemType.SSH_KEY)
        assertEquals(ItemType.SSH_KEY, viewModel.editorState.value?.itemType)
        assertEquals("Cross Type Title", viewModel.editorState.value?.title?.value)

        // Switch to CUSTOM
        viewModel.switchType(ItemType.CUSTOM)
        assertEquals(ItemType.CUSTOM, viewModel.editorState.value?.itemType)
        assertEquals("Cross Type Title", viewModel.editorState.value?.title?.value)
    }
}
