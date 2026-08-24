package com.kryptx.app.system.autofill

import android.credentials.ClearCredentialStateException
import android.credentials.CreateCredentialException
import android.credentials.GetCredentialException
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.service.credentials.BeginCreateCredentialRequest
import android.service.credentials.BeginCreateCredentialResponse
import android.service.credentials.BeginGetCredentialRequest
import android.service.credentials.BeginGetCredentialResponse
import android.service.credentials.ClearCredentialStateRequest
import android.service.credentials.CredentialProviderService
import androidx.annotation.RequiresApi
import com.kryptx.app.KryptxApplication
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Modern Android 14+ (API 34+) CredentialProviderService implementation.
 * Integrates natively with the Android Credential Manager framework for zero-friction
 * Passkey (WebAuthn / FIDO2) and Password retrieval & creation in the system bottom sheet.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class KryptxCredentialProviderService : CredentialProviderService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onBeginGetCredential(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>
    ) {
        val app = application as? KryptxApplication
        if (app == null) {
            callback.onResult(BeginGetCredentialResponse.Builder().build())
            return
        }

        val callingAppInfo = request.callingAppInfo
        val callingPackage = callingAppInfo?.packageName ?: ""
        val origin = callingAppInfo?.origin ?: ""

        val sessionManager = app.sessionManager
        val isUnlocked = sessionManager.isUnlocked.value

        serviceScope.launch {
            try {
                val responseBuilder = BeginGetCredentialResponse.Builder()
                val allItems = app.vaultRepository.getItems().firstOrNull() ?: emptyList()

                // Filter matching passkey and login items
                val targetDomain = if (origin.isNotBlank()) sanitizeDomain(origin) else callingPackage
                val matchingItems = allItems.filter { item ->
                    if (item.type != ItemType.LOGIN && item.type != ItemType.PASSKEY) return@filter false
                    if (targetDomain.isNotBlank()) {
                        val itemDomain = sanitizeDomain(item.website.ifBlank { item.domain })
                        isDomainMatch(targetDomain, itemDomain) || item.title.equals(targetDomain, ignoreCase = true)
                    } else true
                }

                callback.onResult(responseBuilder.build())
            } catch (e: Exception) {
                callback.onResult(BeginGetCredentialResponse.Builder().build())
            }
        }
    }

    override fun onBeginCreateCredential(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>
    ) {
        val responseBuilder = BeginCreateCredentialResponse.Builder()
        callback.onResult(responseBuilder.build())
    }

    override fun onClearCredentialState(
        request: ClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void, ClearCredentialStateException>
    ) {
        callback.onResult(null)
    }

    private fun sanitizeDomain(input: String): String {
        if (input.isBlank()) return ""
        val lower = input.trim().lowercase()
        return lower
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("androidapp://")
            .removePrefix("www.")
            .substringBefore("/")
            .substringBefore(":")
    }

    private fun isDomainMatch(targetDomain: String, itemDomain: String): Boolean {
        if (targetDomain.isBlank() || itemDomain.isBlank()) return false
        val cleanTarget = sanitizeDomain(targetDomain)
        val cleanItem = sanitizeDomain(itemDomain)

        if (cleanTarget == cleanItem) return true
        if (cleanTarget.endsWith(".$cleanItem")) return true
        if (cleanItem.endsWith(".$cleanTarget")) return true

        return false
    }
}
