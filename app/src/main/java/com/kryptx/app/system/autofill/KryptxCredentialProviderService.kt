package com.kryptx.app.system.autofill

import android.app.PendingIntent
import android.content.Intent
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
import android.service.credentials.CreateEntry
import android.service.credentials.CredentialEntry
import android.service.credentials.CredentialProviderService
import androidx.annotation.RequiresApi
import com.kryptx.app.KryptxApplication
import com.kryptx.app.MainActivity
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
@Suppress("DEPRECATION")
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

                for (item in matchingItems) {
                    val intent = Intent(this@KryptxCredentialProviderService, AutofillAuthActivity::class.java).apply {
                        putExtra("EXTRA_ITEM_ID", item.id)
                        putExtra("EXTRA_ITEM_TITLE", item.title)
                        putExtra("EXTRA_ITEM_USERNAME", item.username)
                        putExtra("EXTRA_ITEM_TYPE", item.type.name)
                        putExtra("EXTRA_TARGET_DOMAIN", targetDomain)
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        this@KryptxCredentialProviderService,
                        item.id.hashCode(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )

                    val slice = android.app.slice.Slice.Builder(
                        android.net.Uri.parse("content://${packageName}.credentialprovider/entry/${item.id}"),
                        android.app.slice.SliceSpec("credential", 1)
                    )
                        .addText(item.title, "title", listOf(android.app.slice.Slice.HINT_TITLE))
                        .addText(item.username.ifBlank { item.website }, "summary", listOf(android.app.slice.Slice.HINT_SUMMARY))
                        .build()

                    val entryType = if (item.type == ItemType.PASSKEY) {
                        "android.credentials.TYPE_PUBLIC_KEY_CREDENTIAL"
                    } else {
                        "android.credentials.TYPE_PASSWORD_CREDENTIAL"
                    }

                    val credentialEntry = CredentialEntry(
                        entryType,
                        slice
                    )
                    responseBuilder.addCredentialEntry(credentialEntry)
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
        val callingAppInfo = request.callingAppInfo
        val callingPackage = callingAppInfo?.packageName ?: ""
        val origin = callingAppInfo?.origin ?: ""
        val targetDomain = if (origin.isNotBlank()) sanitizeDomain(origin) else callingPackage

        try {
            val responseBuilder = BeginCreateCredentialResponse.Builder()
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_QUICK_ACTION", "ADD_ITEM")
                putExtra("EXTRA_PREFILL_DOMAIN", targetDomain)
                putExtra("EXTRA_CALLING_PACKAGE", callingPackage)
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                8888,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val createSlice = android.app.slice.Slice.Builder(
                android.net.Uri.parse("content://${packageName}.credentialprovider/create/save"),
                android.app.slice.SliceSpec("create", 1)
            )
                .addText("Save to Kryptx Vault", "title", listOf(android.app.slice.Slice.HINT_TITLE))
                .addText(targetDomain, "summary", listOf(android.app.slice.Slice.HINT_SUMMARY))
                .build()

            val createEntry = CreateEntry(createSlice)
            responseBuilder.addCreateEntry(createEntry)
            callback.onResult(responseBuilder.build())
        } catch (e: Exception) {
            callback.onResult(BeginCreateCredentialResponse.Builder().build())
        }
    }

    override fun onClearCredentialState(
        request: ClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void, ClearCredentialStateException>
    ) {
        callback.onResult(null)
    }

    fun sanitizeDomain(input: String): String {
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

    fun isDomainMatch(targetDomain: String, itemDomain: String): Boolean {
        if (targetDomain.isBlank() || itemDomain.isBlank()) return false
        val cleanTarget = sanitizeDomain(targetDomain)
        val cleanItem = sanitizeDomain(itemDomain)

        if (cleanTarget == cleanItem) return true
        if (cleanTarget.endsWith(".$cleanItem")) return true
        if (cleanItem.endsWith(".$cleanTarget")) return true

        return false
    }
}
