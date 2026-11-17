package com.kryptx.app.core.database

import com.kryptx.app.core.model.KryptxErrorType
import com.kryptx.app.core.model.KryptxResult
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.security.VaultSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext

interface VaultTrashRepository {
    fun getTrashItems(): Flow<List<VaultItem>>
    suspend fun moveToTrash(itemId: String): KryptxResult<Unit>
    suspend fun restoreFromTrash(itemId: String): KryptxResult<Unit>
    suspend fun emptyTrash(): KryptxResult<Int>
}

@OptIn(ExperimentalCoroutinesApi::class)
class VaultTrashRepositoryImpl(
    private val dbHelper: KryptxDatabaseHelper,
    private val decoyDbHelper: KryptxDatabaseHelper,
    private val sessionManager: VaultSessionManager,
    private val onAuditInvalidated: (() -> Unit)? = null
) : VaultTrashRepository {

    override fun getTrashItems(): Flow<List<VaultItem>> = sessionManager.isDecoy.flatMapLatest { isDecoy ->
        if (isDecoy) decoyDbHelper.trashFlow else dbHelper.trashFlow
    }

    override suspend fun moveToTrash(itemId: String): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        sessionManager.withVaultKey { activeVek ->
            try {
                val targetDb = if (sessionManager.isDecoy.value) decoyDbHelper else dbHelper
                val success = targetDb.moveToTrash(itemId, activeVek)
                if (success) {
                    onAuditInvalidated?.invoke()
                    KryptxResult.Success(Unit)
                } else {
                    KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to move item to trash")
                }
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to move item to trash", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override suspend fun restoreFromTrash(itemId: String): KryptxResult<Unit> = withContext(Dispatchers.IO) {
        sessionManager.withVaultKey { activeVek ->
            try {
                val targetDb = if (sessionManager.isDecoy.value) decoyDbHelper else dbHelper
                val success = targetDb.restoreFromTrash(itemId, activeVek)
                if (success) {
                    onAuditInvalidated?.invoke()
                    KryptxResult.Success(Unit)
                } else {
                    KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to restore item from trash")
                }
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to restore item from trash", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }

    override suspend fun emptyTrash(): KryptxResult<Int> = withContext(Dispatchers.IO) {
        sessionManager.withVaultKey { activeVek ->
            try {
                val targetDb = if (sessionManager.isDecoy.value) decoyDbHelper else dbHelper
                val deletedCount = targetDb.emptyTrash(activeVek)
                onAuditInvalidated?.invoke()
                KryptxResult.Success(deletedCount)
            } catch (e: Exception) {
                KryptxResult.Error(KryptxErrorType.DATABASE_ERROR, "Failed to empty trash", e)
            }
        } ?: KryptxResult.Error(KryptxErrorType.VAULT_LOCKED, "Vault is locked")
    }
}
