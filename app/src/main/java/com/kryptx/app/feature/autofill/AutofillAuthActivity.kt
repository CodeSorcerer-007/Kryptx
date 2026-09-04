package com.kryptx.app.feature.autofill

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import android.service.autofill.Dataset
import com.kryptx.app.KryptxApplication
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sovereign Autofill Authentication & Credential Selection Activity.
 *
 * Intercepts Android Autofill auth callbacks, securely verifies biometric or master password
 * credentials, matches items to the requesting domain/app, and returns EXTRA_AUTHENTICATION_RESULT
 * so target apps (browsers, native apps) are filled seamlessly.
 */
class AutofillAuthActivity : FragmentActivity() {

    companion object {
        const val EXTRA_WEB_DOMAIN = "com.kryptx.autofill.WEB_DOMAIN"
        const val EXTRA_PACKAGE_NAME = "com.kryptx.autofill.PACKAGE_NAME"
        const val EXTRA_USERNAME_ID = "com.kryptx.autofill.USERNAME_ID"
        const val EXTRA_PASSWORD_ID = "com.kryptx.autofill.PASSWORD_ID"
    }

    private var targetDomain: String? = null
    private var targetPackage: String? = null
    private var usernameFieldId: AutofillId? = null
    private var passwordFieldId: AutofillId? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immediately enforce hardware window screenshot & screen-recording protection
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        val app = application as KryptxApplication
        app.sessionManager.setPickerActive(true)

        // Observe user preferences for FLAG_SECURE
        lifecycleScope.launch {
            app.preferencesRepository.flagSecureEnabled.collect { enabled ->
                com.kryptx.app.core.security.ScreenshotProtection.apply(this@AutofillAuthActivity, enabled)
            }
        }

        targetDomain = intent.getStringExtra(EXTRA_WEB_DOMAIN)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME)

        usernameFieldId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_USERNAME_ID, AutofillId::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_USERNAME_ID)
        }

        passwordFieldId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_PASSWORD_ID, AutofillId::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_PASSWORD_ID)
        }

        setContent {
            val themeMode by app.preferencesRepository.themeMode.collectAsState()
            val dynamicColor by app.preferencesRepository.dynamicColor.collectAsState()

            KryptxTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                AutofillAuthScreen(
                    targetDomain = targetDomain,
                    targetPackage = targetPackage,
                    onItemSelect = { item -> fillAndFinish(item) },
                    onCancel = { cancelAndFinish() },
                    onBiometricUnlock = { onTriggerBiometrics() }
                )
            }
        }
    }

    private fun fillAndFinish(item: VaultItem) {
        val app = application as KryptxApplication
        val datasetBuilder = Dataset.Builder()
        var hasValue = false

        val uId = usernameFieldId
        val pId = passwordFieldId

        if (uId != null && item.username.isNotBlank()) {
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, item.username)
            }
            datasetBuilder.setValue(uId, AutofillValue.forText(item.username), presentation)
            hasValue = true
        }

        if (pId != null && item.password.isNotBlank()) {
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, "••••••••")
            }
            datasetBuilder.setValue(pId, AutofillValue.forText(item.password), presentation)
            hasValue = true
        }

        if (hasValue) {
            val replyIntent = Intent().apply {
                putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, datasetBuilder.build())
            }
            app.activityLogManager.logEvent("Autofill", "Filled credentials for ${item.title}")
            setResult(Activity.RESULT_OK, replyIntent)
        } else {
            setResult(Activity.RESULT_CANCELED)
        }

        app.sessionManager.setPickerActive(false)
        finish()
    }

    private fun cancelAndFinish() {
        val app = application as? KryptxApplication
        app?.sessionManager?.setPickerActive(false)
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun onTriggerBiometrics() {
        val app = application as KryptxApplication
        if (!app.biometricManager.canAuthenticate()) return

        val decryptCipher = app.vaultRepository.getBiometricDecryptCipher()
        val cryptoObject = if (decryptCipher != null) {
            androidx.biometric.BiometricPrompt.CryptoObject(decryptCipher)
        } else null

        app.biometricManager.promptBiometric(
            activity = this,
            title = "Unlock Kryptx Autofill",
            subtitle = "Touch sensor to decrypt and autofill credentials",
            cryptoObject = cryptoObject,
            onSuccess = { result ->
                val cipher = result.cryptoObject?.cipher
                lifecycleScope.launch {
                    if (cipher != null) {
                        val res = app.vaultRepository.unlockWithBiometricCipher(cipher)
                        if (res.isError) {
                            app.vaultRepository.unlockWithBiometrics()
                        }
                    } else {
                        app.vaultRepository.unlockWithBiometrics()
                    }
                }
            },
            onError = { _, _ -> },
            onFailed = {}
        )
    }

    override fun onDestroy() {
        val app = application as? KryptxApplication
        app?.sessionManager?.setPickerActive(false)
        super.onDestroy()
    }

    @Composable
    private fun AutofillAuthScreen(
        targetDomain: String?,
        targetPackage: String?,
        onItemSelect: (VaultItem) -> Unit,
        onCancel: () -> Unit,
        onBiometricUnlock: () -> Unit
    ) {
        val app = applicationContext as KryptxApplication
        val isUnlocked by app.sessionManager.isUnlocked.collectAsState()
        val isBiometricsConfigured = remember { app.vaultRepository.isBiometricsConfigured() }
        val view = LocalView.current
        val scope = rememberCoroutineScope()

        var searchQuery by remember { mutableStateOf("") }
        var masterPasswordInput by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(false) }

        var allItems by remember { mutableStateOf<List<VaultItem>>(emptyList()) }

        // Trigger biometrics on initial launch if locked and configured
        LaunchedEffect(isUnlocked) {
            if (!isUnlocked && isBiometricsConfigured && app.biometricManager.canAuthenticate()) {
                onBiometricUnlock()
            } else if (isUnlocked) {
                withContext(Dispatchers.IO) {
                    try {
                        allItems = app.vaultRepository.getItems().first()
                    } catch (_: Exception) {}
                }
            }
        }

        val matchedItems = remember(allItems, targetDomain, targetPackage, searchQuery) {
            val loginItems = allItems.filter { it.type == ItemType.LOGIN || it.type == ItemType.PASSKEY }
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.lowercase().trim()
                loginItems.filter {
                    it.title.lowercase().contains(q) ||
                            it.username.lowercase().contains(q) ||
                            it.website.lowercase().contains(q)
                }
            } else if (!targetDomain.isNullOrBlank() || !targetPackage.isNullOrBlank()) {
                val matched = loginItems.filter { item ->
                    when {
                        !targetDomain.isNullOrBlank() && com.kryptx.app.core.security.DomainMatcher.isDomainMatch(targetDomain, item.website) -> true
                        !targetPackage.isNullOrBlank() && com.kryptx.app.core.security.DomainMatcher.isPackageMatch(targetPackage, item.website, item.title) -> true
                        else -> false
                    }
                }
                if (matched.isNotEmpty()) matched else loginItems
            } else {
                loginItems
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
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
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = KryptxBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Kryptx Autofill",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = targetDomain ?: targetPackage ?: "Sovereign Vault",
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
                        // Locked State: Authenticate with Biometrics or Password
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
                                text = "Authenticate to decrypt credentials for autofill",
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
                                        .bounceClick { onBiometricUnlock() },
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

                            AnimatedVisibility(visible = errorMessage != null) {
                                Text(
                                    text = errorMessage ?: "",
                                    color = KryptxRed,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            KryptxPrimaryButton(
                                text = if (isLoading) "Unlocking..." else "Unlock & Fill",
                                enabled = !isLoading,
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
                                        }
                                        isLoading = false
                                        if (res.isError) {
                                            KryptxHaptics.error(view)
                                            errorMessage = "Incorrect master password"
                                        } else {
                                            KryptxHaptics.confirm(view)
                                            masterPasswordInput = ""
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        // Unlocked State: Credential Picker
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

                            if (matchedItems.isEmpty()) {
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
                                    items(matchedItems, key = { it.id }) { item ->
                                        AutofillCredentialCard(
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
    private fun AutofillCredentialCard(
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
                            imageVector = Icons.Default.Key,
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
                        if (item.username.isNotBlank()) {
                            Text(
                                text = item.username,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = "Fill",
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
