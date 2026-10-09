package com.kryptx.app.feature.credentials

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.service.credentials.Action
import android.service.credentials.BeginCreateCredentialRequest
import android.service.credentials.BeginCreateCredentialResponse
import android.service.credentials.BeginGetCredentialOption
import android.service.credentials.BeginGetCredentialRequest
import android.service.credentials.BeginGetCredentialResponse
import android.service.credentials.ClearCredentialStateRequest
import android.service.credentials.CreateEntry
import android.service.credentials.CredentialEntry
import android.service.credentials.CredentialProviderService
import androidx.annotation.RequiresApi
import com.kryptx.app.KryptxApplication
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.security.DomainMatcher
import com.kryptx.app.core.security.SecurityLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Sovereign Android 14+ Credential Provider Service for Kryptx.
 *
 * Implements native CredentialManager integration for WebAuthn Passkeys and Password
 * credentials without cloud dependencies, preserving the 100% air-gap architecture.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class KryptxCredentialProviderService : CredentialProviderService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBeginGetCredential(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, android.credentials.GetCredentialException>
    ) {
        if (cancellationSignal.isCanceled) return

        val app = applicationContext as? KryptxApplication ?: run {
            callback.onResult(BeginGetCredentialResponse.Builder().build())
            return
        }

        val callingAppInfo = request.callingAppInfo
        val targetOrigin = callingAppInfo?.origin
        val targetPackage = callingAppInfo?.packageName

        val options = request.beginGetCredentialOptions
        val passkeyOptions = options.filter {
            it.type == KryptxCredentialProviderSliceHelper.TYPE_PUBLIC_KEY_CREDENTIAL_ANDROIDX ||
            it.type == KryptxCredentialProviderSliceHelper.TYPE_PUBLIC_KEY_CREDENTIAL_FRAMEWORK
        }
        val passwordOptions = options.filter {
            it.type == KryptxCredentialProviderSliceHelper.TYPE_PASSWORD_CREDENTIAL
        }

        val isUnlocked = app.sessionManager.isUnlocked.value

        // If vault is locked, return an Action entry prompting the user to unlock Kryptx
        if (!isUnlocked) {
            val unlockIntent = Intent(this, CredentialAuthActivity::class.java).apply {
                action = CredentialConstants.ACTION_UNLOCK_VAULT
                putExtra(CredentialConstants.EXTRA_AUTH_TOKEN, CredentialAuthTokenManager.generateToken())
                putExtra(CredentialConstants.EXTRA_ORIGIN, targetOrigin)
                putExtra(CredentialConstants.EXTRA_PACKAGE_NAME, targetPackage)
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                REQUEST_CODE_UNLOCK,
                unlockIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val actionSlice = KryptxCredentialProviderSliceHelper.createActionSlice(
                title = "🔒 Unlock Kryptx Vault",
                subtitle = "Authenticate to select credentials",
                pendingIntent = pendingIntent
            )

            val response = BeginGetCredentialResponse.Builder()
                .addAction(Action(actionSlice))
                .build()

            callback.onResult(response)
            return
        }

        // Vault is unlocked: query matching credentials off the binder thread
        serviceScope.launch {
            try {
                if (cancellationSignal.isCanceled) return@launch

                val allItems = app.vaultRepository.getItems().first()
                val matchedItems = filterMatchingItems(allItems, targetOrigin, targetPackage)

                val responseBuilder = BeginGetCredentialResponse.Builder()
                var entryCount = 0

                for (item in matchedItems) {
                    if (cancellationSignal.isCanceled) return@launch

                    // Match Passkeys
                    if (item.type == ItemType.PASSKEY && passkeyOptions.isNotEmpty()) {
                        val option = passkeyOptions.first()
                        val intent = Intent(this@KryptxCredentialProviderService, CredentialAuthActivity::class.java).apply {
                            action = CredentialConstants.ACTION_ASSERT_PASSKEY
                            putExtra(CredentialConstants.EXTRA_AUTH_TOKEN, CredentialAuthTokenManager.generateToken())
                            putExtra(CredentialConstants.EXTRA_ITEM_ID, item.id)
                            putExtra(CredentialConstants.EXTRA_OPTION_ID, option.id)
                            putExtra(CredentialConstants.EXTRA_RP_ID, item.passkeyRpId.ifBlank { targetOrigin ?: "" })
                            putExtra(CredentialConstants.EXTRA_CREDENTIAL_TYPE, option.type)
                            putExtra(CredentialConstants.EXTRA_ORIGIN, targetOrigin)
                            putExtra(CredentialConstants.EXTRA_PACKAGE_NAME, targetPackage)
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            this@KryptxCredentialProviderService,
                            item.id.hashCode(),
                            intent,
                            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        val slice = KryptxCredentialProviderSliceHelper.createCredentialEntrySlice(
                            context = this@KryptxCredentialProviderService,
                            optionId = option.id,
                            type = option.type,
                            title = item.username.ifBlank { item.title },
                            subtitle = item.passkeyRpId.ifBlank { item.title },
                            typeDisplayName = "Passkey",
                            pendingIntent = pendingIntent,
                            lastUsedTimeMillis = item.lastUsedAt
                        )
                        responseBuilder.addCredentialEntry(CredentialEntry(option.id, slice))
                        entryCount++
                    }

                    // Match Password Logins
                    if (item.type == ItemType.LOGIN && passwordOptions.isNotEmpty()) {
                        val option = passwordOptions.first()
                        val intent = Intent(this@KryptxCredentialProviderService, CredentialAuthActivity::class.java).apply {
                            action = CredentialConstants.ACTION_GET_PASSWORD
                            putExtra(CredentialConstants.EXTRA_AUTH_TOKEN, CredentialAuthTokenManager.generateToken())
                            putExtra(CredentialConstants.EXTRA_ITEM_ID, item.id)
                            putExtra(CredentialConstants.EXTRA_OPTION_ID, option.id)
                            putExtra(CredentialConstants.EXTRA_CREDENTIAL_TYPE, option.type)
                            putExtra(CredentialConstants.EXTRA_ORIGIN, targetOrigin)
                            putExtra(CredentialConstants.EXTRA_PACKAGE_NAME, targetPackage)
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            this@KryptxCredentialProviderService,
                            item.id.hashCode(),
                            intent,
                            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        val slice = KryptxCredentialProviderSliceHelper.createCredentialEntrySlice(
                            context = this@KryptxCredentialProviderService,
                            optionId = option.id,
                            type = option.type,
                            title = item.username.ifBlank { item.title },
                            subtitle = item.domain.ifBlank { item.website },
                            typeDisplayName = "Password",
                            pendingIntent = pendingIntent,
                            lastUsedTimeMillis = item.lastUsedAt
                        )
                        responseBuilder.addCredentialEntry(CredentialEntry(option.id, slice))
                        entryCount++
                    }
                }

                // If no direct matches, offer an Action entry to search or choose any vault item
                if (entryCount == 0) {
                    val searchIntent = Intent(this@KryptxCredentialProviderService, CredentialAuthActivity::class.java).apply {
                        action = CredentialConstants.ACTION_SEARCH_VAULT
                        putExtra(CredentialConstants.EXTRA_AUTH_TOKEN, CredentialAuthTokenManager.generateToken())
                        putExtra(CredentialConstants.EXTRA_ORIGIN, targetOrigin)
                        putExtra(CredentialConstants.EXTRA_PACKAGE_NAME, targetPackage)
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        this@KryptxCredentialProviderService,
                        REQUEST_CODE_SEARCH,
                        searchIntent,
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    val actionSlice = KryptxCredentialProviderSliceHelper.createActionSlice(
                        title = "Search Kryptx Vault",
                        subtitle = "Select credential for ${targetOrigin ?: targetPackage ?: "app"}",
                        pendingIntent = pendingIntent
                    )
                    responseBuilder.addAction(Action(actionSlice))
                }

                callback.onResult(responseBuilder.build())
            } catch (t: Throwable) {
                SecurityLogger.error("CredentialProvider", "Error in onBeginGetCredential", t)
                callback.onResult(BeginGetCredentialResponse.Builder().build())
            }
        }
    }

    override fun onBeginCreateCredential(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, android.credentials.CreateCredentialException>
    ) {
        if (cancellationSignal.isCanceled) return

        val callingAppInfo = request.callingAppInfo
        val targetOrigin = callingAppInfo?.origin
        val targetPackage = callingAppInfo?.packageName
        val requestType = request.type
        val requestData = request.data

        var accountPreview = ""
        var description = "Save to Kryptx Vault"

        if (requestType == KryptxCredentialProviderSliceHelper.TYPE_PUBLIC_KEY_CREDENTIAL_ANDROIDX ||
            requestType == KryptxCredentialProviderSliceHelper.TYPE_PUBLIC_KEY_CREDENTIAL_FRAMEWORK
        ) {
            val reqJsonStr = requestData.getString(CredentialConstants.BUNDLE_KEY_REQUEST_JSON) ?: ""
            if (reqJsonStr.isNotBlank()) {
                try {
                    val json = JSONObject(reqJsonStr)
                    val userObj = json.optJSONObject("user")
                    val rpObj = json.optJSONObject("rp")
                    val userName = userObj?.optString("name")?.ifBlank { userObj.optString("displayName") } ?: ""
                    val rpName = rpObj?.optString("name")?.ifBlank { rpObj.optString("id") } ?: ""
                    if (userName.isNotBlank()) {
                        accountPreview = userName
                    }
                    if (rpName.isNotBlank()) {
                        description = "Save Passkey for $rpName"
                    }
                } catch (_: Exception) {}
            }
            if (accountPreview.isBlank()) {
                accountPreview = targetOrigin?.removePrefix("https://")?.removePrefix("http://")
                    ?: targetPackage
                    ?: "New Passkey"
            }
        } else {
            accountPreview = targetOrigin?.removePrefix("https://")?.removePrefix("http://")
                ?: targetPackage
                ?: "New Password"
            description = "Save Password in Kryptx"
        }

        val createIntent = Intent(this, CredentialAuthActivity::class.java).apply {
            action = CredentialConstants.ACTION_CREATE_CREDENTIAL
            putExtra(CredentialConstants.EXTRA_AUTH_TOKEN, CredentialAuthTokenManager.generateToken())
            putExtra(CredentialConstants.EXTRA_CREDENTIAL_TYPE, requestType)
            putExtra(CredentialConstants.EXTRA_ORIGIN, targetOrigin)
            putExtra(CredentialConstants.EXTRA_PACKAGE_NAME, targetPackage)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_CREATE,
            createIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val createSlice = KryptxCredentialProviderSliceHelper.createCreateEntrySlice(
            context = this,
            accountName = accountPreview,
            description = description,
            pendingIntent = pendingIntent
        )

        val response = BeginCreateCredentialResponse.Builder()
            .addCreateEntry(CreateEntry(createSlice))
            .build()

        callback.onResult(response)
    }

    override fun onClearCredentialState(
        request: ClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void, android.credentials.ClearCredentialStateException>
    ) {
        if (cancellationSignal.isCanceled) return
        callback.onResult(null)
    }

    private fun filterMatchingItems(
        allItems: List<VaultItem>,
        targetOrigin: String?,
        targetPackage: String?
    ): List<VaultItem> {
        val nonDeleted = allItems.filter { !it.isDeleted }
        return nonDeleted.filter { item ->
            when {
                !targetOrigin.isNullOrBlank() -> {
                    val itemTarget = if (item.type == ItemType.PASSKEY) item.passkeyRpId else item.website
                    DomainMatcher.isDomainMatch(targetOrigin, itemTarget)
                }
                !targetPackage.isNullOrBlank() -> {
                    DomainMatcher.isPackageMatch(targetPackage, item.website, item.title)
                }
                else -> false
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_UNLOCK = 2001
        private const val REQUEST_CODE_CREATE = 2002
        private const val REQUEST_CODE_SEARCH = 2003
    }
}
