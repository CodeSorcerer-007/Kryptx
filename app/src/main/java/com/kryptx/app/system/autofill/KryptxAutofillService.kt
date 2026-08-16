package com.kryptx.app.system.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillContext
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.kryptx.app.KryptxApplication
import com.kryptx.app.R
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.net.URI
import java.util.UUID

/**
 * Native Android AutofillService implementation.
 * Traverses AssistStructure with advanced heuristics to detect login/password fields
 * and browser address bars, matches credentials from the encrypted vault,
 * and securely fills credentials into third-party apps and browsers.
 */
@RequiresApi(Build.VERSION_CODES.O)
@Suppress("DEPRECATION")
class KryptxAutofillService : AutofillService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val app = application as? KryptxApplication
        if (app == null) {
            callback.onSuccess(null)
            return
        }

        val contexts: List<FillContext> = request.fillContexts
        val lastContext = contexts.lastOrNull()
        val structure: AssistStructure? = lastContext?.structure

        if (structure == null) {
            callback.onSuccess(null)
            return
        }

        val fieldDetector = AutofillFieldDetector()
        fieldDetector.traverseStructure(structure)

        val usernameId = fieldDetector.usernameFieldId
        val passwordId = fieldDetector.passwordFieldId
        val detectedDomain = fieldDetector.detectedDomain

        if (usernameId == null && passwordId == null) {
            callback.onSuccess(null)
            return
        }

        val sessionManager = app.sessionManager
        val isUnlocked = sessionManager.isUnlocked.value

        if (!isUnlocked) {
            // Build an authentication prompt dataset so user can unlock with Biometrics
            val responseBuilder = FillResponse.Builder()
            val intent = Intent(this, AutofillAuthActivity::class.java).apply {
                putExtra("domain", detectedDomain)
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                1001,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
            )

            val remoteViews = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, "🔒 Unlock Kryptx to Autofill")
            }

            val targetId = usernameId ?: passwordId!!
            val authDataset = Dataset.Builder(remoteViews)
                .setAuthentication(pendingIntent.intentSender)
                .setValue(targetId, AutofillValue.forText(""))
                .build()

            responseBuilder.addDataset(authDataset)
            callback.onSuccess(responseBuilder.build())
            return
        }

        // Vault is unlocked - query matching credentials
        serviceScope.launch {
            val allItems = app.vaultRepository.getItems().firstOrNull() ?: emptyList()
            val loginItems = allItems.filter { it.type == ItemType.LOGIN }

            val cleanDomain = sanitizeDomain(detectedDomain)
            val matchingItems = loginItems.filter { item ->
                if (cleanDomain.isNotBlank()) {
                    val itemDomain = sanitizeDomain(item.website.ifBlank { item.domain })
                    itemDomain.contains(cleanDomain, ignoreCase = true) ||
                            cleanDomain.contains(itemDomain, ignoreCase = true) ||
                            item.title.contains(cleanDomain, ignoreCase = true)
                } else true
            }.sortedWith(
                compareByDescending<VaultItem> { it.isFavorite }
                    .thenByDescending { it.lastUsedAt }
                    .thenByDescending { it.updatedAt }
            )

            if (matchingItems.isEmpty()) {
                callback.onSuccess(null)
                return@launch
            }

            val responseBuilder = FillResponse.Builder()

            for (item in matchingItems.take(5)) {
                val remoteViews = RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
                    setTextViewText(android.R.id.text1, item.title)
                    setTextViewText(android.R.id.text2, item.username.ifBlank { item.website })
                }

                val datasetBuilder = Dataset.Builder(remoteViews)
                if (usernameId != null && item.username.isNotBlank()) {
                    datasetBuilder.setValue(usernameId, AutofillValue.forText(item.username))
                }
                if (passwordId != null && item.password.isNotBlank()) {
                    datasetBuilder.setValue(passwordId, AutofillValue.forText(item.password))
                }

                responseBuilder.addDataset(datasetBuilder.build())
            }

            // SaveInfo builder for capturing newly entered credentials
            if (passwordId != null) {
                val saveInfoBuilder = SaveInfo.Builder(
                    SaveInfo.SAVE_DATA_TYPE_PASSWORD or SaveInfo.SAVE_DATA_TYPE_USERNAME,
                    listOfNotNull(usernameId, passwordId).toTypedArray()
                )
                responseBuilder.setSaveInfo(saveInfoBuilder.build())
            }

            callback.onSuccess(responseBuilder.build())
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val app = application as? KryptxApplication
        if (app == null || !app.sessionManager.isUnlocked.value) {
            callback.onSuccess()
            return
        }

        val contexts = request.fillContexts
        val lastStructure = contexts.lastOrNull()?.structure ?: run {
            callback.onSuccess()
            return
        }

        val fieldDetector = AutofillFieldDetector()
        fieldDetector.traverseStructure(lastStructure)

        val username = fieldDetector.extractedUsername.orEmpty()
        val password = fieldDetector.extractedPassword.orEmpty()
        val domain = sanitizeDomain(fieldDetector.detectedDomain)

        if (password.isNotBlank()) {
            serviceScope.launch {
                val title = if (domain.isNotBlank()) domain.replaceFirstChar { it.uppercase() } else "New Login"
                val newItem = VaultItem(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    type = ItemType.LOGIN,
                    username = username,
                    password = password,
                    website = if (domain.isNotBlank()) "https://$domain" else "",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    lastUsedAt = System.currentTimeMillis()
                )
                app.vaultRepository.saveItem(newItem)
                callback.onSuccess()
            }
        } else {
            callback.onSuccess()
        }
    }

    private fun sanitizeDomain(urlOrDomain: String): String {
        if (urlOrDomain.isBlank()) return ""
        return try {
            val url = if (!urlOrDomain.startsWith("http://") && !urlOrDomain.startsWith("https://")) {
                "https://$urlOrDomain"
            } else {
                urlOrDomain
            }
            val host = URI(url).host ?: urlOrDomain
            host.removePrefix("www.").lowercase()
        } catch (_: Exception) {
            urlOrDomain.removePrefix("www.").lowercase()
        }
    }
}

/**
 * Traverses Android AssistStructure node tree to discover web domains and login/password fields.
 */
class AutofillFieldDetector {
    var usernameFieldId: AutofillId? = null
    var passwordFieldId: AutofillId? = null
    var detectedDomain: String = ""

    var extractedUsername: String? = null
    var extractedPassword: String? = null

    fun traverseStructure(structure: AssistStructure) {
        val windowNodeCount = structure.windowNodeCount
        for (i in 0 until windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val viewNode = windowNode.rootViewNode
            traverseNode(viewNode)
        }
    }

    private fun traverseNode(node: AssistStructure.ViewNode?) {
        if (node == null) return

        val hints = node.autofillHints
        val webDomain = node.webDomain
        val idEntry = node.idEntry?.lowercase().orEmpty()
        val hintText = node.hint?.lowercase().orEmpty()
        val className = node.className?.lowercase().orEmpty()

        if (!webDomain.isNullOrBlank() && detectedDomain.isBlank()) {
            detectedDomain = webDomain
        }

        // Browser address bar heuristic (Chrome, Firefox, Brave, Samsung Internet, Edge, etc.)
        if (detectedDomain.isBlank() && (idEntry.contains("url_bar") || idEntry.contains("location_bar") || idEntry.contains("address_bar"))) {
            val text = node.text?.toString()?.trim().orEmpty()
            if (text.isNotBlank() && (text.contains(".") || text.contains("http"))) {
                detectedDomain = text
            }
        }

        // Check standard autofill hints
        if (hints != null) {
            for (hint in hints) {
                if (hint.contains("username", ignoreCase = true) || hint.contains("email", ignoreCase = true)) {
                    usernameFieldId = node.autofillId
                    node.text?.toString()?.let { extractedUsername = it }
                }
                if (hint.contains("password", ignoreCase = true)) {
                    passwordFieldId = node.autofillId
                    node.text?.toString()?.let { extractedPassword = it }
                }
            }
        }

        // Heuristic fallback if hints were not provided by developer
        if (usernameFieldId == null && (idEntry.contains("username") || idEntry.contains("user_name") || idEntry.contains("email") || hintText.contains("username") || hintText.contains("email"))) {
            usernameFieldId = node.autofillId
            node.text?.toString()?.let { extractedUsername = it }
        }

        if (passwordFieldId == null && (idEntry.contains("password") || idEntry.contains("passwd") || hintText.contains("password") || className.contains("password"))) {
            passwordFieldId = node.autofillId
            node.text?.toString()?.let { extractedPassword = it }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            traverseNode(node.getChildAt(i))
        }
    }
}
