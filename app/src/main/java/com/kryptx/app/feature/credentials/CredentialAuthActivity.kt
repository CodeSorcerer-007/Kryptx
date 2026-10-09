package com.kryptx.app.feature.credentials

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.kryptx.app.KryptxApplication
import com.kryptx.app.core.crypto.PasskeyEngine
import com.kryptx.app.core.crypto.SecureMemory
import com.kryptx.app.core.designsystem.components.KryptxHaptics
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.components.frostedGlass
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.designsystem.theme.KryptxTheme
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.KryptxResult
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.security.DomainMatcher
import com.kryptx.app.core.security.SecurityLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Base64

/**
 * Sovereign Credential Authentication & Confirmation Activity for Android 14+ Credential Manager.
 *
 * Handles WebAuthn Passkey assertions and registrations, password fulfillment, and biometric
 * verification with hardware-backed screenshot protection.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class CredentialAuthActivity : FragmentActivity() {

    private var targetOrigin: String? = null
    private var targetPackage: String? = null
    private var targetRpId: String? = null
    private var targetItemId: String? = null
    private var targetOptionId: String? = null
    private var targetAction: String? = null
    private var targetCredentialType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val field = FragmentActivity::class.java.getDeclaredField("mRequestedPermissionsFromFragment")
            field.isAccessible = true
            field.setBoolean(this, true)
        } catch (_: Throwable) {}

        // Enforce hardware FLAG_SECURE protection
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()

        val authToken = intent.getStringExtra(CredentialConstants.EXTRA_AUTH_TOKEN)
        val isValidToken = CredentialAuthTokenManager.validateAndConsumeToken(authToken)
        if (!isValidToken) {
            SecurityLogger.warn(
                "CredentialAuthActivity",
                "Unauthorized launch attempt without valid auth token. Finishing."
            )
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val app = application as? KryptxApplication ?: run {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        app.sessionManager.setPickerActive(true)

        // Observe user screenshot protection preferences
        lifecycleScope.launch {
            app.preferencesRepository.flagSecureEnabled.collect { enabled ->
                com.kryptx.app.core.security.ScreenshotProtection.apply(this@CredentialAuthActivity, enabled)
            }
        }

        targetAction = intent.action
        targetItemId = intent.getStringExtra(CredentialConstants.EXTRA_ITEM_ID)
        targetOptionId = intent.getStringExtra(CredentialConstants.EXTRA_OPTION_ID)
        targetRpId = intent.getStringExtra(CredentialConstants.EXTRA_RP_ID)
        targetOrigin = intent.getStringExtra(CredentialConstants.EXTRA_ORIGIN)
        targetPackage = intent.getStringExtra(CredentialConstants.EXTRA_PACKAGE_NAME)
        targetCredentialType = intent.getStringExtra(CredentialConstants.EXTRA_CREDENTIAL_TYPE)

        setContent {
            val themeMode by app.preferencesRepository.themeMode.collectAsState()
            val dynamicColor by app.preferencesRepository.dynamicColor.collectAsState()

            KryptxTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                CredentialAuthScreen(
                    targetAction = targetAction,
                    targetOrigin = targetOrigin,
                    targetPackage = targetPackage,
                    targetItemId = targetItemId,
                    onItemSelect = { item -> fulfillGetCredential(item) },
                    onCreateConfirm = { userName, userDisplayName -> fulfillCreateCredential(userName, userDisplayName) },
                    onCancel = { cancelAndFinish() },
                    onBiometricUnlock = { onTriggerBiometrics() }
                )
            }
        }
    }

    private fun cancelAndFinish() {
        val app = application as? KryptxApplication
        app?.sessionManager?.setPickerActive(false)
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    override fun onDestroy() {
        val app = application as? KryptxApplication
        app?.sessionManager?.setPickerActive(false)
        super.onDestroy()
    }

    private fun onTriggerBiometrics() {
        val app = application as? KryptxApplication ?: return
        if (app.sessionManager.lockoutSecondsRemaining.value > 0) return
        if (!app.biometricManager.canAuthenticate(requireStrong = true)) return
        val isConfigured = app.vaultRepository.isBiometricsConfigured() && app.preferencesRepository.biometricEnabled.value
        if (!isConfigured) return

        val decryptCipher = try {
            app.vaultRepository.getBiometricDecryptCipher()
        } catch (_: Throwable) {
            null
        } ?: return

        val cryptoObject = androidx.biometric.BiometricPrompt.CryptoObject(decryptCipher)

        app.biometricManager.promptBiometric(
            activity = this,
            title = "Unlock Kryptx Credentials",
            subtitle = "Touch sensor to authenticate and use credential",
            cryptoObject = cryptoObject,
            onSuccess = { result ->
                val cipher = result.cryptoObject?.cipher ?: return@promptBiometric
                lifecycleScope.launch {
                    app.vaultRepository.unlockWithBiometricCipher(cipher)
                }
            },
            onError = { _, _ -> },
            onFailed = {
                app.sessionManager.recordFailedAttempt()
            }
        )
    }

    /**
     * Completes a GetCredentialRequest for either Passkey or Password.
     */
    private fun fulfillGetCredential(item: VaultItem) {
        val app = application as KryptxApplication
        lifecycleScope.launch {
            if (item.type == ItemType.PASSKEY) {
                fulfillPasskeyAssertion(app, item)
            } else {
                fulfillPasswordGet(app, item)
            }
        }
    }

    private suspend fun fulfillPasskeyAssertion(app: KryptxApplication, item: VaultItem) {
        val frameworkGetReq: android.service.credentials.GetCredentialRequest? =
            intent.getParcelableExtra(
                android.service.credentials.CredentialProviderService.EXTRA_GET_CREDENTIAL_REQUEST,
                android.service.credentials.GetCredentialRequest::class.java
            )

        var requestJsonStr = ""
        var clientDataHashBytes: ByteArray? = null

        frameworkGetReq?.credentialOptions?.forEach { opt ->
            val data = opt.credentialRetrievalData
            val req = data.getString(CredentialConstants.BUNDLE_KEY_REQUEST_JSON)
            if (!req.isNullOrBlank()) {
                requestJsonStr = req
            }
            val hash = data.getByteArray(CredentialConstants.BUNDLE_KEY_CLIENT_DATA_HASH)
            if (hash != null) {
                clientDataHashBytes = hash
            }
        }

        val effectiveRpId = item.passkeyRpId.ifBlank {
            targetRpId ?: targetOrigin?.removePrefix("https://")?.removePrefix("http://")?.substringBefore(':') ?: ""
        }

        var challengeBase64 = ""
        if (requestJsonStr.isNotBlank()) {
            try {
                val json = JSONObject(requestJsonStr)
                challengeBase64 = json.optString("challenge")
            } catch (_: Exception) {}
        }

        if (challengeBase64.isBlank()) {
            val challengeBytes = ByteArray(32)
            java.security.SecureRandom().nextBytes(challengeBytes)
            challengeBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes)
        }

        val effectiveOrigin = targetOrigin ?: "https://$effectiveRpId"
        val clientDataJsonBytes = PasskeyEngine.buildClientDataJson(
            type = "webauthn.get",
            challengeBase64 = challengeBase64,
            origin = effectiveOrigin
        )

        val assertResult = withContext(Dispatchers.IO) {
            app.vaultRepository.assertPasskey(item, clientDataJsonBytes, effectiveRpId)
        }

        when (assertResult) {
            is KryptxResult.Success -> {
                val assertion = assertResult.data
                val responseJson = JSONObject().apply {
                    put("id", item.passkeyCredentialId)
                    put("rawId", item.passkeyCredentialId)
                    put("type", "public-key")
                    put("authenticatorAttachment", "platform")
                    put("response", JSONObject().apply {
                        put("authenticatorData", assertion.authenticatorDataBase64)
                        put("clientDataJSON", assertion.clientDataJsonBase64)
                        put("signature", assertion.signatureBase64)
                        put("userHandle", assertion.userHandleBase64)
                    })
                    put("clientExtensionResults", JSONObject())
                }.toString()

                val dataBundle = Bundle().apply {
                    putString(CredentialConstants.BUNDLE_KEY_AUTHENTICATION_RESPONSE_JSON, responseJson)
                }

                val credType = targetCredentialType
                    ?: KryptxCredentialProviderSliceHelper.TYPE_PUBLIC_KEY_CREDENTIAL_ANDROIDX
                val credential = android.credentials.Credential(credType, dataBundle)
                val getResponse = android.credentials.GetCredentialResponse(credential)

                val replyIntent = Intent().apply {
                    putExtra(android.service.credentials.CredentialProviderService.EXTRA_GET_CREDENTIAL_RESPONSE, getResponse)
                }
                app.activityLogManager.logEvent("Passkey", "Signed assertion for ${item.title}")
                setResult(Activity.RESULT_OK, replyIntent)
                finish()
            }
            is KryptxResult.Error -> {
                SecurityLogger.error("CredentialAuth", "Passkey assertion failed: ${assertResult.message}")
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
    }

    private fun fulfillPasswordGet(app: KryptxApplication, item: VaultItem) {
        val dataBundle = Bundle().apply {
            putString(CredentialConstants.BUNDLE_KEY_ID, item.username)
            putString(CredentialConstants.BUNDLE_KEY_PASSWORD, item.password)
        }

        val credential = android.credentials.Credential(
            android.credentials.Credential.TYPE_PASSWORD_CREDENTIAL,
            dataBundle
        )
        val getResponse = android.credentials.GetCredentialResponse(credential)

        val replyIntent = Intent().apply {
            putExtra(android.service.credentials.CredentialProviderService.EXTRA_GET_CREDENTIAL_RESPONSE, getResponse)
        }
        app.activityLogManager.logEvent("Credential", "Retrieved password for ${item.title}")
        setResult(Activity.RESULT_OK, replyIntent)
        finish()
    }

    /**
     * Completes a CreateCredentialRequest for Passkeys or Passwords.
     */
    private fun fulfillCreateCredential(userNameInput: String, userDisplayNameInput: String) {
        val app = application as KryptxApplication
        lifecycleScope.launch {
            val frameworkCreateReq: android.service.credentials.CreateCredentialRequest? =
                intent.getParcelableExtra(
                    android.service.credentials.CredentialProviderService.EXTRA_CREATE_CREDENTIAL_REQUEST,
                    android.service.credentials.CreateCredentialRequest::class.java
                )

            val reqData = frameworkCreateReq?.data ?: intent.extras ?: Bundle()
            val requestType = frameworkCreateReq?.type ?: targetCredentialType ?: ""

            if (requestType == KryptxCredentialProviderSliceHelper.TYPE_PUBLIC_KEY_CREDENTIAL_ANDROIDX ||
                requestType == KryptxCredentialProviderSliceHelper.TYPE_PUBLIC_KEY_CREDENTIAL_FRAMEWORK
            ) {
                val reqJsonStr = reqData.getString(CredentialConstants.BUNDLE_KEY_REQUEST_JSON) ?: ""
                var rpId = targetRpId ?: targetOrigin?.removePrefix("https://")?.removePrefix("http://")?.substringBefore(':') ?: ""
                var rpName = rpId
                var userName = userNameInput
                var userHandle = ""
                var challengeBytes = ByteArray(32)

                if (reqJsonStr.isNotBlank()) {
                    try {
                        val json = JSONObject(reqJsonStr)
                        val rpObj = json.optJSONObject("rp")
                        if (rpObj != null) {
                            rpId = rpObj.optString("id", rpId)
                            rpName = rpObj.optString("name", rpName)
                        }
                        val userObj = json.optJSONObject("user")
                        if (userObj != null) {
                            if (userName.isBlank()) {
                                userName = userObj.optString("name", userObj.optString("displayName", "User"))
                            }
                            userHandle = userObj.optString("id", "")
                        }
                        val chalB64 = json.optString("challenge")
                        if (chalB64.isNotBlank()) {
                            challengeBytes = Base64.getUrlDecoder().decode(chalB64)
                        }
                    } catch (_: Exception) {}
                }

                if (userName.isBlank()) userName = "User"
                if (userHandle.isBlank()) {
                    userHandle = Base64.getUrlEncoder().withoutPadding().encodeToString(userName.toByteArray(Charsets.UTF_8))
                }

                val registerResult = withContext(Dispatchers.IO) {
                    app.vaultRepository.registerPasskey(
                        rpId = rpId,
                        rpName = rpName,
                        userHandle = userHandle,
                        userName = userName,
                        challenge = challengeBytes
                    )
                }

                when (registerResult) {
                    is KryptxResult.Success -> {
                        val newItem = registerResult.data
                        withContext(Dispatchers.IO) {
                            app.vaultRepository.saveItem(newItem)
                        }

                        val origin = targetOrigin ?: "https://$rpId"
                        val challengeB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes)
                        val clientDataJsonBytes = PasskeyEngine.buildClientDataJson(
                            type = "webauthn.create",
                            challengeBase64 = challengeB64,
                            origin = origin
                        )

                        val attestationB64 = PasskeyEngine.createAttestationObjectBase64(
                            rpId = rpId,
                            credentialIdBase64 = newItem.passkeyCredentialId,
                            publicKeyCoseBase64 = newItem.passkeyPublicKeyCoseBase64
                        )

                        val regResponseJson = JSONObject().apply {
                            put("id", newItem.passkeyCredentialId)
                            put("rawId", newItem.passkeyCredentialId)
                            put("type", "public-key")
                            put("authenticatorAttachment", "platform")
                            put("response", JSONObject().apply {
                                put("clientDataJSON", Base64.getUrlEncoder().withoutPadding().encodeToString(clientDataJsonBytes))
                                put("attestationObject", attestationB64)
                            })
                            put("clientExtensionResults", JSONObject())
                        }.toString()

                        val responseBundle = Bundle().apply {
                            putString(CredentialConstants.BUNDLE_KEY_REGISTRATION_RESPONSE_JSON, regResponseJson)
                        }

                        val createResponse = android.credentials.CreateCredentialResponse(responseBundle)
                        val replyIntent = Intent().apply {
                            putExtra(android.service.credentials.CredentialProviderService.EXTRA_CREATE_CREDENTIAL_RESPONSE, createResponse)
                        }
                        app.activityLogManager.logEvent("Passkey", "Registered sovereign passkey for $rpName")
                        setResult(Activity.RESULT_OK, replyIntent)
                        finish()
                    }
                    is KryptxResult.Error -> {
                        SecurityLogger.error("CredentialAuth", "Failed to register passkey: ${registerResult.message}")
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                }
            } else {
                // Password creation fallback
                val createResponse = android.credentials.CreateCredentialResponse(Bundle())
                val replyIntent = Intent().apply {
                    putExtra(android.service.credentials.CredentialProviderService.EXTRA_CREATE_CREDENTIAL_RESPONSE, createResponse)
                }
                setResult(Activity.RESULT_OK, replyIntent)
                finish()
            }
        }
    }

    @Composable
    private fun CredentialAuthScreen(
        targetAction: String?,
        targetOrigin: String?,
        targetPackage: String?,
        targetItemId: String?,
        onItemSelect: (VaultItem) -> Unit,
        onCreateConfirm: (String, String) -> Unit,
        onCancel: () -> Unit,
        onBiometricUnlock: () -> Unit
    ) {
        val app = (applicationContext as? KryptxApplication) ?: run {
            onCancel()
            return
        }
        val isUnlocked by app.sessionManager.isUnlocked.collectAsState()
        val lockoutSeconds by app.sessionManager.lockoutSecondsRemaining.collectAsState()
        val isBiometricsConfigured = remember {
            app.vaultRepository.isBiometricsConfigured() && app.preferencesRepository.biometricEnabled.value
        }
        val view = LocalView.current
        val scope = rememberCoroutineScope()

        androidx.activity.compose.BackHandler {
            cancelAndFinish()
        }

        var searchQuery by remember { mutableStateOf("") }
        var masterPasswordInput by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(false) }
        var allItems by remember { mutableStateOf<List<VaultItem>>(emptyList()) }

        // Trigger biometrics on initial launch if locked and configured and not locked out
        LaunchedEffect(isUnlocked, lockoutSeconds) {
            if (!isUnlocked && isBiometricsConfigured && app.biometricManager.canAuthenticate() && lockoutSeconds == 0) {
                onBiometricUnlock()
            } else if (isUnlocked) {
                withContext(Dispatchers.IO) {
                    try {
                        allItems = app.vaultRepository.getItems().first()
                    } catch (_: Exception) {}
                }
            }
        }

        // Auto-fulfill if specific itemId was targeted once unlocked
        LaunchedEffect(isUnlocked, allItems) {
            if (isUnlocked && !targetItemId.isNullOrBlank() && allItems.isNotEmpty()) {
                val found = allItems.firstOrNull { it.id == targetItemId }
                if (found != null) {
                    onItemSelect(found)
                }
            }
        }

        val displaySubtitle = targetOrigin ?: targetPackage ?: "Kryptx Sovereign Credentials"

        val matchingItems = remember(allItems, targetOrigin, targetPackage, searchQuery) {
            val eligible = allItems.filter { !it.isDeleted && (it.type == ItemType.LOGIN || it.type == ItemType.PASSKEY) }
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.lowercase().trim()
                eligible.filter {
                    it.title.lowercase().contains(q) ||
                    it.username.lowercase().contains(q) ||
                    it.website.lowercase().contains(q) ||
                    it.passkeyRpId.lowercase().contains(q)
                }
            } else if (!targetOrigin.isNullOrBlank() || !targetPackage.isNullOrBlank()) {
                val filtered = eligible.filter { item ->
                    when {
                        !targetOrigin.isNullOrBlank() -> {
                            val itemDomain = if (item.type == ItemType.PASSKEY) item.passkeyRpId else item.website
                            DomainMatcher.isDomainMatch(targetOrigin, itemDomain)
                        }
                        !targetPackage.isNullOrBlank() -> {
                            DomainMatcher.isPackageMatch(targetPackage, item.website, item.title)
                        }
                        else -> false
                    }
                }
                if (filtered.isNotEmpty()) filtered else eligible
            } else {
                eligible
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize().atmosphericTopGlow()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(KryptxBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = KryptxBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Kryptx Passkey Sync",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = displaySubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = onCancel) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isUnlocked) {
                        // Locked State: Biometric or Password Prompt
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Vault Locked",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Authenticate to authorize credential operation",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(28.dp))

                            if (isBiometricsConfigured && app.biometricManager.canAuthenticate()) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(KryptxBlue.copy(alpha = 0.15f))
                                        .border(1.dp, KryptxBlue.copy(alpha = 0.4f), CircleShape)
                                        .bounceClick {
                                            if (lockoutSeconds == 0) {
                                                onBiometricUnlock()
                                            } else {
                                                errorMessage = "Too many failed attempts. Try again in ${lockoutSeconds}s."
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "Unlock with Biometrics",
                                        tint = KryptxBlue,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Tap for Biometrics",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = KryptxBlue
                                )

                                Spacer(modifier = Modifier.height(24.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(0.8f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                                    Text(
                                        text = "  OR  ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            KryptxTextField(
                                value = masterPasswordInput,
                                onValueChange = {
                                    masterPasswordInput = it
                                    errorMessage = null
                                },
                                label = "Master Password",
                                isPassword = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            AnimatedVisibility(visible = errorMessage != null || lockoutSeconds > 0) {
                                val text = if (lockoutSeconds > 0) {
                                    "Security backoff active: retry in ${lockoutSeconds}s"
                                } else {
                                    errorMessage ?: ""
                                }
                                Text(
                                    text = text,
                                    color = KryptxRed,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            KryptxPrimaryButton(
                                text = if (lockoutSeconds > 0) "Retry in ${lockoutSeconds}s" else if (isLoading) "Unlocking..." else "Unlock & Authorize",
                                enabled = !isLoading && lockoutSeconds == 0,
                                leadingIcon = if (isLoading) {
                                    {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    }
                                } else null,
                                onClick = {
                                    val remaining = app.sessionManager.lockoutSecondsRemaining.value
                                    if (remaining > 0) {
                                        errorMessage = "Too many failed attempts. Try again in ${remaining}s."
                                        return@KryptxPrimaryButton
                                    }
                                    if (masterPasswordInput.isBlank()) {
                                        errorMessage = "Please enter your master password"
                                        return@KryptxPrimaryButton
                                    }
                                    isLoading = true
                                    errorMessage = null
                                    scope.launch {
                                        val chars = masterPasswordInput.toCharArray()
                                        val res = try {
                                            app.vaultRepository.unlockWithPassword(chars)
                                        } finally {
                                            SecureMemory.wipe(chars)
                                            masterPasswordInput = ""
                                        }
                                        isLoading = false
                                        if (res.isError) {
                                            KryptxHaptics.error(view)
                                            val currentRemaining = app.sessionManager.lockoutSecondsRemaining.value
                                            errorMessage = if (currentRemaining > 0) {
                                                "Too many failed attempts. Try again in ${currentRemaining}s."
                                            } else {
                                                "Incorrect master password"
                                            }
                                        } else {
                                            KryptxHaptics.confirm(view)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else if (targetAction == CredentialConstants.ACTION_CREATE_CREDENTIAL) {
                        // Create Credential View
                        var userNameInput by remember { mutableStateOf("") }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Create Sovereign Passkey",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Save an encrypted WebAuthn passkey in your vault for $displaySubtitle",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            KryptxTextField(
                                value = userNameInput,
                                onValueChange = { userNameInput = it },
                                label = "Account Username (optional)",
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            KryptxPrimaryButton(
                                text = "Save Passkey in Kryptx",
                                onClick = {
                                    KryptxHaptics.confirm(view)
                                    onCreateConfirm(userNameInput, userNameInput)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        // Unlocked Credential Picker List
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            KryptxTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = "Search credentials...",
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            if (matchingItems.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No credentials found for this service",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(matchingItems, key = { it.id }) { item ->
                                        CredentialItemCard(
                                            item = item,
                                            onClick = {
                                                KryptxHaptics.confirm(view)
                                                onItemSelect(item)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CredentialItemCard(
        item: VaultItem,
        onClick: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .frostedGlass()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .clickable { onClick() }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(KryptxBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.type == ItemType.PASSKEY) Icons.Default.Fingerprint else Icons.Default.Key,
                            contentDescription = null,
                            tint = KryptxBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val subtitle = if (item.type == ItemType.PASSKEY) {
                            "Passkey • ${item.username.ifBlank { item.passkeyRpId }}"
                        } else {
                            item.username.ifBlank { "Password credential" }
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Select",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = KryptxBlue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(KryptxBlue.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
