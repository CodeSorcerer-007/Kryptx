package com.kryptx.app.feature.vault.editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.KryptxResult
import com.kryptx.app.core.model.VaultAttachment
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.database.VaultRepository
import com.kryptx.app.core.security.ActivityLogManager
import com.kryptx.app.core.security.IAttachmentManager
import com.kryptx.app.core.security.VaultSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
/**
 * ViewModel for the Add / Edit vault item screen.
 *
 * Owns a [ItemEditorState] sealed instance that represents the typed, mutable
 * form state for the currently-edited item. The sealed hierarchy gives compile-time
 * exhaustive coverage: only fields relevant to the active [ItemType] are accessible.
 *
 * The flat [VaultItem] data class remains the persistence DTO — this ViewModel
 * bridges between the typed UI state and the repository layer without any DB migration.
 */
class AddEditViewModel(
    private val vaultRepository: VaultRepository,
    private val sessionManager: VaultSessionManager,
    private val attachmentManager: IAttachmentManager,
    private val activityLogManager: ActivityLogManager,
    savedStateHandle: SavedStateHandle? = null
) : ViewModel() {

    // ── Sealed editor state ───────────────────────────────────────────────────

    private val _editorState = MutableStateFlow<ItemEditorState?>(null)

    /** The live typed form state. Null until [loadItem] or [initNewItem] is called. */
    val editorState: StateFlow<ItemEditorState?> = _editorState.asStateFlow()

    // ── Save operation state ──────────────────────────────────────────────────

    sealed class SaveResult {
        data object Idle : SaveResult()
        data object Saving : SaveResult()
        data object Saved : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    private val _saveResult = MutableStateFlow<SaveResult>(SaveResult.Idle)
    val saveResult: StateFlow<SaveResult> = _saveResult.asStateFlow()

    // ── Dirty / unsaved tracking ──────────────────────────────────────────────

    /** The original item snapshot, used to detect unsaved changes. */
    private var originalItem: VaultItem? = null

    /** New attachments added in this session — cleaned up if the user discards. */
    private val _newAttachments = mutableListOf<VaultAttachment>()

    /** Attachments queued for deletion when save is confirmed. */
    private val _deletedAttachments = mutableListOf<VaultAttachment>()

    val isDirty: Boolean
        get() = _editorState.value?.isDirtyRelativeTo(originalItem) ?: false

    // ── Initialisation ────────────────────────────────────────────────────────

    /**
     * Loads an existing [VaultItem] into the editor. Must be called before the screen
     * is displayed when editing an existing item.
     */
    fun loadItem(item: VaultItem) {
        originalItem = item
        _editorState.value = ItemEditorState.fromVaultItem(item)
    }

    /**
     * Initialises the editor for a brand-new item of [type].
     */
    fun initNewItem(type: ItemType) {
        originalItem = null
        _editorState.value = ItemEditorState.forNewItem(type)
    }

    // ── Type switching ────────────────────────────────────────────────────────

    /**
     * Switches the item type when creating a new item.
     * Preserves the title and notes that the user may have already typed,
     * but discards all type-specific fields since they are incompatible.
     *
     * Only valid during new-item creation (no existing item).
     */
    fun switchType(newType: ItemType) {
        if (originalItem != null) return  // Never switch type on existing items
        val current = _editorState.value ?: return
        if (current.itemType == newType) return

        val newState = ItemEditorState.forNewItem(newType)
        newState.title.value = current.title.value
        newState.notes.value = current.notes.value
        newState.isFavorite.value = current.isFavorite.value
        _editorState.value = newState
    }

    // ── Attachment management ─────────────────────────────────────────────────

    /**
     * Encrypts and saves an attachment picked by the user. On success, adds the
     * resulting [VaultAttachment] to the editor state and tracks it for potential cleanup.
     *
     * @return The saved [VaultAttachment], or null on failure.
     */
    suspend fun saveAttachment(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String
    ): VaultAttachment? {
        val saved = attachmentManager.saveAttachmentFromUri(uri, fileName, mimeType) ?: return null
        _editorState.value?.attachments?.add(saved)
        _newAttachments.add(saved)
        return saved
    }

    /**
     * Marks an attachment for deletion. Removes it from editor state immediately;
     * the physical delete is deferred until save is confirmed via [confirmSave].
     */
    fun scheduleAttachmentDeletion(context: Context, attachment: VaultAttachment) {
        _editorState.value?.attachments?.remove(attachment)
        if (_newAttachments.remove(attachment)) {
            // Newly-added attachment removed before save — delete immediately
            viewModelScope.launch { attachmentManager.deleteAttachment(attachment) }
        } else {
            _deletedAttachments.add(attachment)
        }
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    /**
     * Validates the current editor state.
     *
     * @return null on success, or a user-facing error message string.
     */
    fun validate(): String? {
        val state = _editorState.value ?: return "Editor not initialised"

        if (state.title.value.isBlank()) return "Title cannot be empty"

        if (state is ItemEditorState.CreditCard) {
            val cleanCard = state.cardNumber.value.filter { it.isDigit() }
            if (cleanCard.isNotEmpty()) {
                if (cleanCard.length !in 12..19 || !isValidLuhn(cleanCard)) {
                    return "Invalid card number (checksum failed)"
                }
            }
            if (state.cardExpiry.value.isNotBlank()) {
                val expiryRegex = Regex("""^(0[1-9]|1[0-2])\s*/\s*([0-9]{2}|[0-9]{4})$""")
                val match = expiryRegex.matchEntire(state.cardExpiry.value.trim())
                    ?: return "Card expiry must be in MM/YY format (e.g. 12/28)"
                val month = match.groupValues[1].toInt()
                val yearStr = match.groupValues[2]
                val year = if (yearStr.length == 2) 2000 + yearStr.toInt() else yearStr.toInt()
                val cal = java.util.Calendar.getInstance()
                if (year < cal.get(java.util.Calendar.YEAR) ||
                    (year == cal.get(java.util.Calendar.YEAR) &&
                        month < cal.get(java.util.Calendar.MONTH) + 1)
                ) {
                    return "Card is already expired"
                }
            }
        }

        return null
    }

    private fun isValidLuhn(digits: String): Boolean {
        var sum = 0
        digits.reversed().forEachIndexed { index, c ->
            var n = c.digitToInt()
            if (index % 2 == 1) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
        }
        return sum % 10 == 0
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    /**
     * Validates and persists the current editor state.
     *
     * On success:
     * - Deletes any attachments scheduled for removal
     * - Emits [SaveResult.Saved]
     *
     * On error:
     * - Emits [SaveResult.Error] with a user-facing message
     */
    fun confirmSave(context: Context) {
        val state = _editorState.value ?: run {
            _saveResult.value = SaveResult.Error("Editor not initialised")
            return
        }

        val validationError = validate()
        if (validationError != null) {
            _saveResult.value = SaveResult.Error(validationError)
            return
        }

        _saveResult.value = SaveResult.Saving

        viewModelScope.launch {
            val computedExpiry = computeExpiry(state)
            val item = state.toVaultItem(originalItem, computedExpiry)

            when (val result = vaultRepository.saveItem(item)) {
                is KryptxResult.Success -> {
                    // Physical-delete attachments the user removed during editing
                    _deletedAttachments.forEach { att ->
                        try { attachmentManager.deleteAttachment(att) } catch (_: Exception) {}
                    }
                    _deletedAttachments.clear()
                    _newAttachments.clear()
                    activityLogManager.logEvent("Security", "Saved item '${item.title}'")
                    _saveResult.value = SaveResult.Saved
                }
                is KryptxResult.Error -> {
                    _saveResult.value = SaveResult.Error(result.message ?: "Failed to save item")
                }
            }
        }
    }

    /**
     * Discards edits and cleans up any newly-added attachments.
     * Call this when the user cancels without saving.
     */
    fun discardChanges(context: Context) {
        viewModelScope.launch {
            _newAttachments.forEach { att ->
                try { attachmentManager.deleteAttachment(att) } catch (_: Exception) {}
            }
            _newAttachments.clear()
            _deletedAttachments.clear()
        }
        _saveResult.value = SaveResult.Idle
    }

    /** Resets [SaveResult] back to [SaveResult.Idle] after the screen has handled the state. */
    fun resetSaveResult() {
        _saveResult.value = SaveResult.Idle
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Resolves the expiry timestamp from the current [rotationIntervalDays] value vs
     * the original item's stored [VaultItem.expiresAt].
     */
    private fun computeExpiry(state: ItemEditorState): Long? {
        val interval = state.rotationIntervalDays.value
        val existingExpiry = originalItem?.expiresAt
        val existingInterval = originalItem?.rotationIntervalDays

        return when {
            interval != existingInterval -> {
                if (interval != null && interval > 0) {
                    System.currentTimeMillis() + interval * 24L * 60 * 60 * 1000L
                } else null
            }
            else -> existingExpiry
        }
    }
}
