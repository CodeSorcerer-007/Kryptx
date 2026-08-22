package com.kryptx.app.core.sync

import com.kryptx.app.core.database.VaultRepository

/**
 * Interface contract for zero-cloud peer-to-peer Wi-Fi Direct/Hotspot vault synchronization.
 */
interface ILocalP2PSyncManager {
    fun getLocalIpAddress(): String?
    suspend fun startSenderServer(): LocalP2PSyncManager.SyncServerSession?
    suspend fun waitForReceiverAndSend(
        session: LocalP2PSyncManager.SyncServerSession,
        vaultRepository: VaultRepository
    ): LocalP2PSyncManager.SyncResult
    suspend fun receiveVaultFromSender(
        ip: String,
        port: Int,
        pin: String,
        transferKeyBase64: String,
        vaultRepository: VaultRepository
    ): LocalP2PSyncManager.SyncResult
}
