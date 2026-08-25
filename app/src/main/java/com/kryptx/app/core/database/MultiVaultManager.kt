package com.kryptx.app.core.database

import com.kryptx.app.core.model.CustomField
import com.kryptx.app.core.model.VaultItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * Manages discrete, isolated multi-vault partitions (Personal, Work, Sovereign, Family, etc.).
 * Each partition maintains separate derivation parameters and logical isolation.
 */
class MultiVaultManager(
    private val preferencesRepository: IPreferencesRepository? = null
) {
    data class VaultPartition(
        val id: String,
        val name: String,
        val icon: String = "shield",
        val colorHex: String = "#00D4FF",
        val isDefault: Boolean = false,
        val saltBase64: String = "",
        val createdAt: Long = System.currentTimeMillis()
    ) {
        companion object {
            val DEFAULT_PERSONAL = VaultPartition(
                id = "vault_personal_primary",
                name = "Personal",
                icon = "person",
                colorHex = "#00D4FF",
                isDefault = true
            )
            val DEFAULT_WORK = VaultPartition(
                id = "vault_work_primary",
                name = "Work & Enterprise",
                icon = "business_center",
                colorHex = "#7C3AED",
                isDefault = false
            )
            val DEFAULT_SOVEREIGN = VaultPartition(
                id = "vault_sovereign_primary",
                name = "Sovereign Vault",
                icon = "lock",
                colorHex = "#10B981",
                isDefault = false
            )
        }
    }

    private val _vaults = MutableStateFlow<List<VaultPartition>>(
        listOf(
            VaultPartition.DEFAULT_PERSONAL,
            VaultPartition.DEFAULT_WORK,
            VaultPartition.DEFAULT_SOVEREIGN
        )
    )
    val vaults: StateFlow<List<VaultPartition>> = _vaults.asStateFlow()

    private val _activeVaultId = MutableStateFlow(VaultPartition.DEFAULT_PERSONAL.id)
    val activeVaultId: StateFlow<String> = _activeVaultId.asStateFlow()

    /**
     * Retrieves the active VaultPartition.
     */
    val activeVault: VaultPartition
        get() = _vaults.value.find { it.id == _activeVaultId.value } ?: VaultPartition.DEFAULT_PERSONAL

    /**
     * Switches the active vault partition.
     */
    fun switchActiveVault(vaultId: String) {
        if (_vaults.value.any { it.id == vaultId }) {
            _activeVaultId.value = vaultId
        }
    }

    /**
     * Creates a new isolated vault partition with its own cryptographically secure salt.
     */
    fun createVault(name: String, icon: String = "folder", colorHex: String = "#00D4FF"): VaultPartition {
        val random = SecureRandom()
        val saltBytes = ByteArray(32)
        random.nextBytes(saltBytes)
        val saltBase64 = Base64.getEncoder().encodeToString(saltBytes)

        val newVault = VaultPartition(
            id = "vault_${UUID.randomUUID()}",
            name = name,
            icon = icon,
            colorHex = colorHex,
            isDefault = false,
            saltBase64 = saltBase64
        )

        _vaults.value = _vaults.value + newVault
        return newVault
    }

    /**
     * Filters a list of VaultItems to only return items matching the specified vault partition.
     */
    fun filterItemsForVault(items: List<VaultItem>, vaultId: String): List<VaultItem> {
        return items.filter { item ->
            // If item has no vault ID assigned, it belongs to the default personal vault
            val itemVaultId = item.customFields.find { it.label == "__vault_partition_id__" }?.value
                ?: VaultPartition.DEFAULT_PERSONAL.id
            itemVaultId == vaultId
        }
    }

    /**
     * Tags a VaultItem to associate it with a specific vault partition.
     */
    fun assignItemToVault(item: VaultItem, vaultId: String): VaultItem {
        val updatedFields = item.customFields.filterNot { it.label == "__vault_partition_id__" } +
            CustomField(
                id = UUID.randomUUID().toString(),
                label = "__vault_partition_id__",
                value = vaultId,
                isSecured = true
            )
        return item.copy(customFields = updatedFields)
    }
}
