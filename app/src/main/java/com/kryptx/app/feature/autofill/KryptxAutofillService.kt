package com.kryptx.app.feature.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.kryptx.app.KryptxApplication
import com.kryptx.app.MainActivity
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Intelligent sovereign autofill service.
 *
 * When the vault is unlocked in RAM, parses web domains and app package names to present
 * direct 1-tap autofill datasets for matched credentials. When locked, provides an
 * authenticated prompt to unlock with biometrics and fill.
 */
class KryptxAutofillService : AutofillService() {

    // Service-scoped coroutine scope; cancelled in onDestroy to avoid leaks.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    data class ParsedForm(
        var webDomain: String? = null,
        var packageName: String? = null,
        var usernameFieldId: AutofillId? = null,
        var passwordFieldId: AutofillId? = null,
        var focusedFieldId: AutofillId? = null,
        val allAutofillIds: MutableList<AutofillId> = mutableListOf()
    )

    @Suppress("DEPRECATION")
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        if (cancellationSignal.isCanceled) return

        val context = request.fillContexts.lastOrNull() ?: run {
            callback.onSuccess(null)
            return
        }

        val structure = context.structure
        val parsedForm = ParsedForm()
        parsedForm.packageName = structure.activityComponent?.packageName

        for (i in 0 until structure.windowNodeCount) {
            if (cancellationSignal.isCanceled) return
            val windowNode = structure.getWindowNodeAt(i)
            val root = windowNode?.rootViewNode
            if (root != null) {
                traverseNode(root, parsedForm, 0)
            }
        }

        if (parsedForm.allAutofillIds.isEmpty() || cancellationSignal.isCanceled) {
            callback.onSuccess(null)
            return
        }

        val app = applicationContext as? KryptxApplication ?: run {
            callback.onSuccess(null)
            return
        }
        val isUnlocked = app.sessionManager.isUnlocked.value

        val responseBuilder = FillResponse.Builder()

        // 1. If vault is unlocked, search for matching credentials off the binder thread
        if (isUnlocked) {
            serviceScope.launch {
                val matchedItems = findMatchingItems(app, parsedForm)
                if (matchedItems.isNotEmpty()) {
                    var addedDatasets = 0
                    matchedItems.take(5).forEach { item ->
                        val presentation = createItemPresentation(item)
                        val datasetBuilder = Dataset.Builder()
                        var fieldAssigned = false

                        parsedForm.usernameFieldId?.let { uId ->
                            if (item.username.isNotBlank()) {
                                datasetBuilder.setValue(uId, AutofillValue.forText(item.username), presentation)
                                fieldAssigned = true
                            }
                        }

                        parsedForm.passwordFieldId?.let { pId ->
                            if (item.password.isNotBlank()) {
                                datasetBuilder.setValue(pId, AutofillValue.forText(item.password), presentation)
                                fieldAssigned = true
                            }
                        }

                        if (fieldAssigned) {
                            try {
                                responseBuilder.addDataset(datasetBuilder.build())
                                addedDatasets++
                            } catch (e: Exception) {}
                        }
                    }

                    if (addedDatasets > 0) {
                        // Add SaveInfo to prompt user to save new/updated credentials
                        parsedForm.passwordFieldId?.let { passId ->
                            val saveInfoBuilder = SaveInfo.Builder(
                                SaveInfo.SAVE_DATA_TYPE_PASSWORD,
                                arrayOf(passId)
                            )
                            parsedForm.usernameFieldId?.let { uId ->
                                saveInfoBuilder.setOptionalIds(arrayOf(uId))
                            }
                            responseBuilder.setSaveInfo(saveInfoBuilder.build())
                        }

                        callback.onSuccess(responseBuilder.build())
                        return@launch
                    }
                }
                // No match found or no fields populated — fall through to auth route
                callback.onSuccess(buildAuthFillResponse(parsedForm, isUnlocked))
            }
            return
        }

        callback.onSuccess(buildAuthFillResponse(parsedForm, isUnlocked))
    }

    private fun buildAuthFillResponse(parsedForm: ParsedForm, isUnlocked: Boolean): FillResponse {
        // Locked or no direct match: route to AutofillAuthActivity
        // If neither username nor password ID was explicitly found, fallback to focused or first editable field
        val effectivePasswordId = parsedForm.passwordFieldId
            ?: (if (parsedForm.usernameFieldId == null) parsedForm.focusedFieldId ?: parsedForm.allAutofillIds.firstOrNull() else null)

        val intent = Intent(this, AutofillAuthActivity::class.java).apply {
            parsedForm.webDomain?.let { putExtra(AutofillAuthActivity.EXTRA_WEB_DOMAIN, it) }
            parsedForm.packageName?.let { putExtra(AutofillAuthActivity.EXTRA_PACKAGE_NAME, it) }
            parsedForm.usernameFieldId?.let { putExtra(AutofillAuthActivity.EXTRA_USERNAME_ID, it) }
            effectivePasswordId?.let { putExtra(AutofillAuthActivity.EXTRA_PASSWORD_ID, it) }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(this, 1001, intent, flags)

        val authPresentation = RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
            setTextViewText(android.R.id.text1, "🔒 Kryptx Sovereign Autofill")
            setTextViewText(android.R.id.text2, if (isUnlocked) "Search vault for matching credentials" else "Tap to unlock vault & autofill")
        }

        val authResponseBuilder = FillResponse.Builder()
            .setAuthentication(
                parsedForm.allAutofillIds.toTypedArray(),
                pendingIntent.intentSender,
                authPresentation
            )

        parsedForm.passwordFieldId?.let { passId ->
            val saveInfoBuilder = SaveInfo.Builder(
                SaveInfo.SAVE_DATA_TYPE_PASSWORD or SaveInfo.SAVE_DATA_TYPE_USERNAME,
                arrayOf(passId)
            )
            parsedForm.usernameFieldId?.let { uId ->
                saveInfoBuilder.setOptionalIds(arrayOf(uId))
            }
            authResponseBuilder.setSaveInfo(saveInfoBuilder.build())
        }

        return authResponseBuilder.build()
    }

    private suspend fun findMatchingItems(app: KryptxApplication, parsedForm: ParsedForm): List<VaultItem> {
        return try {
            val allItems = app.vaultRepository.getItems().first()

            val targetDomain = parsedForm.webDomain
            val targetPackage = parsedForm.packageName

            allItems.filter { item ->
                if (item.type != ItemType.LOGIN && item.type != ItemType.PASSKEY) return@filter false

                when {
                    // Strict host and subdomain boundary matching for web domains
                    !targetDomain.isNullOrBlank() -> com.kryptx.app.core.security.DomainMatcher.isDomainMatch(targetDomain, item.website)
                    // Package matching for native Android applications
                    !targetPackage.isNullOrBlank() -> com.kryptx.app.core.security.DomainMatcher.isPackageMatch(targetPackage, item.website, item.title)
                    else -> false
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun createItemPresentation(item: VaultItem): RemoteViews {
        return RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
            setTextViewText(android.R.id.text1, item.title)
            setTextViewText(android.R.id.text2, item.username.ifBlank { "Password autofill" })
        }
    }

    private fun traverseNode(node: AssistStructure.ViewNode?, parsed: ParsedForm, depth: Int = 0) {
        if (node == null || depth > 30) return

        node.autofillId?.let { id ->
            if (node.isFocused && parsed.focusedFieldId == null) {
                parsed.focusedFieldId = id
            }

            // Extract web domain from browser node
            node.webDomain?.let { domain ->
                if (parsed.webDomain == null && domain.isNotBlank()) {
                    parsed.webDomain = domain
                }
            }

            // Extract hints
            val hints = node.autofillHints?.map { it.lowercase() } ?: emptyList()
            val textId = node.idEntry?.lowercase() ?: ""
            val hintText = node.hint?.lowercase() ?: ""
            val inputType = node.inputType

            val isPassword = hints.any { it.contains("password") || it.contains("pin") } ||
                    textId.contains("password") ||
                    textId.contains("passwd") ||
                    textId.contains("pin") ||
                    hintText.contains("password") ||
                    hintText.contains("pin") ||
                    (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    (inputType and InputType.TYPE_NUMBER_VARIATION_PASSWORD) == InputType.TYPE_NUMBER_VARIATION_PASSWORD

            val isUsername = hints.any { it.contains("username") || it.contains("email") } ||
                    textId.contains("username") ||
                    textId.contains("email") ||
                    textId.contains("login") ||
                    hintText.contains("username") ||
                    hintText.contains("email") ||
                    (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

            if (isPassword && parsed.passwordFieldId == null) {
                parsed.passwordFieldId = id
                if (!parsed.allAutofillIds.contains(id)) parsed.allAutofillIds.add(id)
            } else if (isUsername && parsed.usernameFieldId == null) {
                parsed.usernameFieldId = id
                if (!parsed.allAutofillIds.contains(id)) parsed.allAutofillIds.add(id)
            } else if (parsed.allAutofillIds.size < 40) {
                val isEditable = node.isFocused ||
                        node.autofillType == View.AUTOFILL_TYPE_TEXT ||
                        node.className?.contains("EditText", ignoreCase = true) == true
                if (isEditable && !parsed.allAutofillIds.contains(id)) {
                    parsed.allAutofillIds.add(id)
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i)
            if (child != null) {
                traverseNode(child, parsed, depth + 1)
            }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val contexts = request.fillContexts
        if (contexts.isEmpty()) {
            callback.onSuccess()
            return
        }

        val lastStructure = contexts.last().structure
        var extractedUsername = ""
        var extractedPassword = ""
        var extractedDomain: String? = null

        fun extractFromNode(node: AssistStructure.ViewNode?) {
            if (node == null) return

            node.webDomain?.let { domain ->
                if (extractedDomain == null && domain.isNotBlank()) {
                    extractedDomain = domain
                }
            }

            val textVal = node.autofillValue?.textValue?.toString()?.trim()
                ?: node.text?.toString()?.trim()
                ?: ""

            if (textVal.isNotEmpty()) {
                val hints = node.autofillHints?.map { it.lowercase() } ?: emptyList()
                val textId = node.idEntry?.lowercase() ?: ""
                val hintText = node.hint?.lowercase() ?: ""
                val inputType = node.inputType

                val isPassword = hints.any { it.contains("password") || it.contains("pin") } ||
                        textId.contains("password") ||
                        textId.contains("passwd") ||
                        textId.contains("pin") ||
                        hintText.contains("password") ||
                        hintText.contains("pin") ||
                        (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                        (inputType and InputType.TYPE_NUMBER_VARIATION_PASSWORD) == InputType.TYPE_NUMBER_VARIATION_PASSWORD

                val isUsername = hints.any { it.contains("username") || it.contains("email") } ||
                        textId.contains("username") ||
                        textId.contains("email") ||
                        textId.contains("login") ||
                        hintText.contains("username") ||
                        hintText.contains("email") ||
                        (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

                if (isPassword && extractedPassword.isBlank()) {
                    extractedPassword = textVal
                } else if (isUsername && extractedUsername.isBlank()) {
                    extractedUsername = textVal
                }
            }

            for (i in 0 until node.childCount) {
                extractFromNode(node.getChildAt(i))
            }
        }

        for (i in 0 until lastStructure.windowNodeCount) {
            val root = lastStructure.getWindowNodeAt(i)?.rootViewNode
            if (root != null) {
                extractFromNode(root)
            }
        }

        val appPackage = lastStructure.activityComponent?.packageName

        if (extractedPassword.isNotBlank()) {
            val app = applicationContext as? KryptxApplication
            if (app != null && app.sessionManager.isUnlocked.value) {
                val rawTarget = extractedDomain?.removePrefix("www.")
                    ?: appPackage?.substringAfterLast('.')?.replaceFirstChar { it.uppercase() }
                    ?: "Saved Login"

                val domainSafe = extractedDomain?.takeIf { it.isNotBlank() }
                val formattedTitle = if (domainSafe != null) {
                    domainSafe.removePrefix("www.").substringBefore('.').replaceFirstChar { it.uppercase() }
                } else {
                    rawTarget
                }
                val websiteUrl = if (domainSafe != null) "https://${domainSafe.removePrefix("www.")}" else ""

                serviceScope.launch {
                    try {
                        val existingItems = app.vaultRepository.getItems().first()

                        // Use the same strict boundary-aware DomainMatcher as onFillRequest to prevent
                        // substring-spoof attacks (e.g. "evil-paypal.com" matching "paypal.com" via contains()).
                        val match = existingItems.firstOrNull { item ->
                            item.type == ItemType.LOGIN && (
                                com.kryptx.app.core.security.DomainMatcher.isDomainMatch(extractedDomain, item.website) ||
                                (!extractedDomain.isNullOrBlank() && com.kryptx.app.core.security.DomainMatcher.isPackageMatch(appPackage, item.website, item.title)) ||
                                item.title.equals(formattedTitle, ignoreCase = true)
                            ) && (extractedUsername.isBlank() || item.username.equals(extractedUsername, ignoreCase = true))
                        }

                        if (match != null) {
                            val updated = match.copy(
                                password = extractedPassword,
                                username = if (extractedUsername.isNotBlank()) extractedUsername else match.username,
                                updatedAt = System.currentTimeMillis(),
                                lastUsedAt = System.currentTimeMillis()
                            )
                            app.vaultRepository.saveItem(updated)
                            app.activityLogManager.logEvent("Autofill", "Updated credential for ${updated.title}")
                        } else {
                            val newItem = VaultItem(
                                title = formattedTitle,
                                type = ItemType.LOGIN,
                                username = extractedUsername,
                                password = extractedPassword,
                                website = websiteUrl,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                                lastUsedAt = System.currentTimeMillis()
                            )
                            app.vaultRepository.saveItem(newItem)
                            app.activityLogManager.logEvent("Autofill", "Saved new credential for ${newItem.title}")
                        }
                    } catch (e: Exception) {
                        // ignore and proceed
                    }
                }
            }
        }

        callback.onSuccess()
    }
}
