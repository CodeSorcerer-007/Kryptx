package com.kryptx.app.feature.vault

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.core.content.ContextCompat
import com.kryptx.app.KryptxApplication
import com.kryptx.app.core.designsystem.components.KryptxPermissionRationaleDialog
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.QrCodeScannerDialog
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.model.CustomField
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.PasswordHistoryEntry
import com.kryptx.app.core.model.VaultAttachment
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.totp.UriParser
import com.kryptx.app.feature.vault.editor.ApiKeyFormFields
import com.kryptx.app.feature.vault.editor.CreditCardFormFields
import com.kryptx.app.feature.vault.editor.CustomFieldsEditor
import com.kryptx.app.feature.vault.editor.EncryptedAttachmentsSection
import com.kryptx.app.feature.vault.editor.ExpirationPolicySection
import com.kryptx.app.feature.vault.editor.IdentityFormFields
import com.kryptx.app.feature.vault.editor.LoginFormFields
import com.kryptx.app.feature.vault.editor.PasskeyFormFields
import com.kryptx.app.feature.vault.editor.WifiFormFields
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AddEditItemScreen(
    itemId: String?,
    viewModel: VaultViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items by viewModel.rawItems.collectAsState()
    val existingItem = remember(itemId, items) {
        items.firstOrNull { it.id == itemId }
    }

    if (itemId != null && existingItem == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }

    var selectedType by rememberSaveable(itemId) { mutableStateOf(existingItem?.type ?: ItemType.LOGIN) }
    var title by rememberSaveable(itemId) { mutableStateOf(existingItem?.title ?: "") }
    var isFavorite by rememberSaveable(itemId) { mutableStateOf(existingItem?.isFavorite ?: false) }
    var notes by rememberSaveable(itemId) { mutableStateOf(existingItem?.notes ?: "") }
    var showQrScanner by rememberSaveable(itemId) { mutableStateOf(false) }



    // Login fields
    var username by rememberSaveable(itemId) { mutableStateOf(existingItem?.username ?: "") }
    var password by rememberSaveable(itemId) { mutableStateOf(existingItem?.password ?: "") }
    var website by rememberSaveable(itemId) { mutableStateOf(existingItem?.website ?: "") }
    var totpSecret by rememberSaveable(itemId) { mutableStateOf(existingItem?.totpSecret ?: "") }

    // Passkey fields
    var passkeyRpId by rememberSaveable(itemId) { mutableStateOf(existingItem?.passkeyRpId ?: "") }
    var passkeyUserHandle by rememberSaveable(itemId) { mutableStateOf(existingItem?.passkeyUserHandle ?: "") }
    var passkeyCredentialId by rememberSaveable(itemId) { mutableStateOf(existingItem?.passkeyCredentialId ?: "") }
    var passkeyAlgorithm by rememberSaveable(itemId) { mutableStateOf(existingItem?.passkeyAlgorithm ?: "ES256 (ECDSA P-256)") }

    // Credit card fields
    var cardholderName by rememberSaveable(itemId) { mutableStateOf(existingItem?.cardholderName ?: "") }
    var cardNumber by rememberSaveable(itemId) { mutableStateOf(existingItem?.cardNumber ?: "") }
    var cardExpiry by rememberSaveable(itemId) { mutableStateOf(existingItem?.cardExpiry ?: "") }
    var cardCvv by rememberSaveable(itemId) { mutableStateOf(existingItem?.cardCvv ?: "") }
    var cardPin by rememberSaveable(itemId) { mutableStateOf(existingItem?.cardPin ?: "") }

    // Identity fields
    var identityName by rememberSaveable(itemId) { mutableStateOf(existingItem?.identityFullName ?: "") }
    var identityEmail by rememberSaveable(itemId) { mutableStateOf(existingItem?.identityEmail ?: "") }
    var identityPhone by rememberSaveable(itemId) { mutableStateOf(existingItem?.identityPhone ?: "") }
    var identityAddress by rememberSaveable(itemId) { mutableStateOf(existingItem?.identityAddress ?: "") }
    var identityDob by rememberSaveable(itemId) { mutableStateOf(existingItem?.identityDob ?: "") }
    var identityIdNum by rememberSaveable(itemId) { mutableStateOf(existingItem?.identityIdNumber ?: "") }

    // Wi-Fi fields
    var wifiSsid by rememberSaveable(itemId) { mutableStateOf(existingItem?.wifiSsid ?: "") }
    var wifiPassword by rememberSaveable(itemId) { mutableStateOf(existingItem?.wifiPassword ?: "") }

    // API Key fields
    var apiKey by rememberSaveable(itemId) { mutableStateOf(existingItem?.apiKey ?: "") }
    var apiSecret by rememberSaveable(itemId) { mutableStateOf(existingItem?.apiSecret ?: "") }
    var apiEndpoint by rememberSaveable(itemId) { mutableStateOf(existingItem?.apiEndpoint ?: "") }

    // Custom fields list
    val customFields = remember(itemId) {
        mutableStateListOf<CustomField>().apply {
            if (existingItem != null) {
                addAll(existingItem.customFields)
            }
        }
    }

    var rotationIntervalDays by rememberSaveable(itemId) { mutableStateOf(existingItem?.rotationIntervalDays) }
    val attachments = remember(itemId) {
        mutableStateListOf<VaultAttachment>().apply {
            if (existingItem != null) {
                addAll(existingItem.attachments)
            }
        }
    }

    val newAttachments = remember(itemId) { mutableStateListOf<VaultAttachment>() }
    val deletedAttachments = remember(itemId) { mutableStateListOf<VaultAttachment>() }
    var isSaved by remember { mutableStateOf(false) }

    val isDirty by derivedStateOf {
        val original = existingItem
        if (original == null) {
            title.isNotEmpty() || username.isNotEmpty() || password.isNotEmpty() || notes.isNotEmpty() || attachments.isNotEmpty()
        } else {
            title != original.title || username != original.username || password != original.password ||
            notes != original.notes || totpSecret != original.totpSecret || selectedType != original.type ||
            attachments.size != original.attachments.size || customFields.size != original.customFields.size ||
            passkeyRpId != original.passkeyRpId || cardNumber != original.cardNumber || wifiSsid != original.wifiSsid ||
            cardExpiry != original.cardExpiry
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = remember(context) { context.applicationContext as? KryptxApplication }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAttachmentTypeDialog by remember { mutableStateOf(false) }
    var showMediaRationaleDialog by remember { mutableStateOf(false) }
    var hasGrantedMediaConsent by remember { mutableStateOf(false) }
    var pendingAttachmentType by remember { mutableStateOf<AttachmentType?>(null) }

    val requestCameraOrOpenScanner: () -> Unit = {
        showQrScanner = true
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        app?.sessionManager?.setPickerActive(false)
        if (uri != null) {
            scope.launch {
                try {
                    val rawName = resolveFileName(context, uri)
                    val fileName = if (!rawName.contains('.')) "$rawName.jpg" else rawName
                    val mimeType = resolveMimeType(context, uri, fileName)
                    val saved = viewModel.saveAttachment(context, uri, fileName, mimeType)
                    if (saved != null) {
                        attachments.add(saved)
                        newAttachments.add(saved)
                    } else {
                        errorMessage = "Unable to encrypt photo into vault."
                    }
                } catch (t: Throwable) {
                    errorMessage = "Error saving photo: ${t.message}"
                }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        app?.sessionManager?.setPickerActive(false)
        if (uri != null) {
            scope.launch {
                try {
                    val fileName = resolveFileName(context, uri)
                    val mimeType = resolveMimeType(context, uri, fileName)
                    val saved = viewModel.saveAttachment(context, uri, fileName, mimeType)
                    if (saved != null) {
                        attachments.add(saved)
                        newAttachments.add(saved)
                    } else {
                        errorMessage = "Unable to encrypt file into vault."
                    }
                } catch (t: Throwable) {
                    errorMessage = "Error reading file: ${t.message}"
                }
            }
        }
    }

    val launchPhotoPicker: () -> Unit = {
        app?.sessionManager?.setPickerActive(true)
        try {
            photoPickerLauncher.launch("image/*")
        } catch (e: Throwable) {
            app?.sessionManager?.setPickerActive(false)
            errorMessage = "Unable to open photo gallery: ${e.message}"
        }
    }

    val launchDocumentPicker: () -> Unit = {
        app?.sessionManager?.setPickerActive(true)
        try {
            filePickerLauncher.launch("*/*")
        } catch (e: Throwable) {
            app?.sessionManager?.setPickerActive(false)
            errorMessage = "Unable to open file selector: ${e.message}"
        }
    }






    var showDiscardDialog by remember { mutableStateOf(false) }
    val handleDiscardAndBack: () -> Unit = {
        viewModel.cleanupAttachments(context, newAttachments)
        onNavigateBack()
    }
    val safeNavigateBack: () -> Unit = {
        if (isDirty) {
            showDiscardDialog = true
        } else {
            handleDiscardAndBack()
        }
    }
    
    BackHandler(enabled = true) {
        safeNavigateBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            password = ""
            cardPin = ""
            cardCvv = ""
            totpSecret = ""
            apiSecret = ""
            apiKey = ""
            wifiPassword = ""
        }
    }

    Scaffold(
        modifier = modifier
            .testTag("add_edit_item_screen")
            .fillMaxSize()
            .imePadding()
            .atmosphericTopGlow(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            KryptxTopBar(
                title = if (existingItem != null) "Edit Item" else "New Vault Item",
                showBackButton = true,
                onBackClick = safeNavigateBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Type Selector
            if (existingItem == null) {
                Text(
                    text = "ITEM TYPE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ItemType.entries.forEach { type ->
                        val isSelected = selectedType == type
                        Box(
                            modifier = Modifier
                                .testTag("item_type_pill_${type.name.lowercase()}")
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) KryptxBlue else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) KryptxBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    RoundedCornerShape(14.dp)
                                )
                                .bounceClick(scaleDown = 0.94f) { selectedType = type }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Select type: ${type.displayName}"
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = type.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Title Field
            KryptxTextField(
                value = title,
                onValueChange = { title = it },
                label = "Title (e.g. Google, Chase Bank, Home Wi-Fi)",
                placeholder = "Required",
                modifier = Modifier.testTag("item_title_field")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Type-specific Form Inputs
            when (selectedType) {
                ItemType.LOGIN -> LoginFormFields(
                    username = username,
                    onUsernameChange = { username = it },
                    password = password,
                    onPasswordChange = { password = it },
                    website = website,
                    onWebsiteChange = { website = it },
                    totpSecret = totpSecret,
                    onTotpSecretChange = { totpSecret = it },
                    onScanQrClick = requestCameraOrOpenScanner
                )
                ItemType.PASSKEY -> PasskeyFormFields(
                    passkeyRpId = passkeyRpId,
                    onPasskeyRpIdChange = {
                        passkeyRpId = it
                        if (title.isBlank()) title = it.removePrefix("www.").replaceFirstChar { char -> char.uppercase() }
                    },
                    username = username,
                    onUsernameChange = { username = it },
                    passkeyCredentialId = passkeyCredentialId,
                    onPasskeyCredentialIdChange = { passkeyCredentialId = it },
                    passkeyAlgorithm = passkeyAlgorithm,
                    onPasskeyAlgorithmChange = { passkeyAlgorithm = it }
                )
                ItemType.CREDIT_CARD -> CreditCardFormFields(
                    cardholderName = cardholderName,
                    onCardholderNameChange = { cardholderName = it },
                    cardNumber = cardNumber,
                    onCardNumberChange = { cardNumber = it },
                    cardExpiry = cardExpiry,
                    onCardExpiryChange = { cardExpiry = it },
                    cardCvv = cardCvv,
                    onCardCvvChange = { cardCvv = it },
                    cardPin = cardPin,
                    onCardPinChange = { cardPin = it }
                )
                ItemType.IDENTITY -> IdentityFormFields(
                    name = identityName,
                    onNameChange = { identityName = it },
                    email = identityEmail,
                    onEmailChange = { identityEmail = it },
                    phone = identityPhone,
                    onPhoneChange = { identityPhone = it },
                    address = identityAddress,
                    onAddressChange = { identityAddress = it },
                    dob = identityDob,
                    onDobChange = { identityDob = it },
                    idNum = identityIdNum,
                    onIdNumChange = { identityIdNum = it }
                )
                ItemType.WIFI -> WifiFormFields(
                    ssid = wifiSsid,
                    onSsidChange = { wifiSsid = it },
                    password = wifiPassword,
                    onPasswordChange = { wifiPassword = it }
                )
                ItemType.API_KEY -> ApiKeyFormFields(
                    apiKey = apiKey,
                    onApiKeyChange = { apiKey = it },
                    apiSecret = apiSecret,
                    onApiSecretChange = { apiSecret = it },
                    apiEndpoint = apiEndpoint,
                    onApiEndpointChange = { apiEndpoint = it }
                )
                ItemType.SECURE_NOTE -> {
                    Column {
                        if (existingItem == null && notes.isBlank()) {
                            Text(
                                text = "Templates",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val templates = listOf(
                                    "Checklist" to "- [ ] Item 1\n- [ ] Item 2\n- [ ] Item 3",
                                    "Server Config" to "Host:\nIP:\nPort:\nRoot Password:\n\n- [ ] Firewall configured\n- [ ] Backups enabled",
                                    "Meeting" to "Date:\nAttendees:\n\nAgenda:\n- \n\nAction Items:\n- [ ] "
                                )
                                templates.forEach { (name, templateText) ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                            .clickable { notes = templateText }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = name,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        KryptxTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = "Secure Note Content",
                            singleLine = false,
                            maxLines = 10
                        )
                    }
                }
                ItemType.CUSTOM -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Custom Fields
            CustomFieldsEditor(customFields = customFields)

            Spacer(modifier = Modifier.height(14.dp))

            // Password Rotation & Expiration
            ExpirationPolicySection(
                rotationIntervalDays = rotationIntervalDays,
                onIntervalSelected = { rotationIntervalDays = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Encrypted Document & Photo Attachments
            EncryptedAttachmentsSection(
                attachments = attachments,
                onAddClicked = { showAttachmentTypeDialog = true },
                onDeleteClicked = { index, att ->
                    if (newAttachments.contains(att)) {
                        scope.launch { viewModel.deleteAttachment(context, att) }
                        newAttachments.remove(att)
                    } else {
                        deletedAttachments.add(att)
                    }
                    attachments.removeAt(index)
                }
            )

            // Notes field (for non-SECURE_NOTE items)
            if (selectedType != ItemType.SECURE_NOTE) {
                Spacer(modifier = Modifier.height(14.dp))
                KryptxTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Secure Notes",
                    singleLine = false,
                    maxLines = 5
                )
            }

            AnimatedVisibility(visible = errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .testTag("add_edit_error_message")
                        .padding(top = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            KryptxPrimaryButton(
                text = if (existingItem != null) "Save Changes" else "Save to Vault",
                containerColor = KryptxBlue,
                contentColor = Color.White,
                modifier = Modifier.testTag("save_vault_item_button"),
                onClick = {
                    if (title.isBlank()) {
                        errorMessage = "Title cannot be empty"
                        return@KryptxPrimaryButton
                    }

                    if (selectedType == ItemType.CREDIT_CARD) {
                        val cleanCard = cardNumber.filter { it.isDigit() }
                        if (cleanCard.isNotEmpty()) {
                            if (cleanCard.length !in 12..19 || !isValidLuhn(cleanCard)) {
                                errorMessage = "Invalid card number (checksum failed)"
                                return@KryptxPrimaryButton
                            }
                        }
                        if (cardExpiry.isNotBlank()) {
                            val expiryRegex = Regex("""^(0[1-9]|1[0-2])\s*/\s*([0-9]{2}|[0-9]{4})$""")
                            val match = expiryRegex.matchEntire(cardExpiry.trim())
                            if (match == null) {
                                errorMessage = "Card expiry must be in MM/YY format (e.g. 12/28)"
                                return@KryptxPrimaryButton
                            }
                            val month = match.groupValues[1].toInt()
                            val yearStr = match.groupValues[2]
                            val year = if (yearStr.length == 2) 2000 + yearStr.toInt() else yearStr.toInt()
                            val cal = java.util.Calendar.getInstance()
                            val currentYear = cal.get(java.util.Calendar.YEAR)
                            val currentMonth = cal.get(java.util.Calendar.MONTH) + 1
                            if (year < currentYear || (year == currentYear && month < currentMonth)) {
                                errorMessage = "Card is already expired"
                                return@KryptxPrimaryButton
                            }
                        }
                    }

                    val computedExpiry = if (rotationIntervalDays != null && rotationIntervalDays!! > 0) {
                        System.currentTimeMillis() + rotationIntervalDays!! * 24L * 60 * 60 * 1000L
                    } else null

                    val updatedHistory = if (existingItem != null && existingItem.password.isNotBlank() && password != existingItem.password) {
                        listOf(PasswordHistoryEntry(existingItem.password, System.currentTimeMillis())) + existingItem.passwordHistory
                    } else {
                        existingItem?.passwordHistory ?: emptyList()
                    }

                    val updatedItem = (existingItem ?: VaultItem(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        type = selectedType,
                        createdAt = System.currentTimeMillis()
                    )).copy(
                        title = title,
                        type = selectedType,
                        isFavorite = isFavorite,
                        notes = notes,
                        username = username,
                        password = password,
                        passwordHistory = updatedHistory,
                        website = website,
                        totpSecret = totpSecret,
                        passkeyRpId = passkeyRpId,
                        passkeyUserHandle = passkeyUserHandle,
                        passkeyCredentialId = passkeyCredentialId,
                        passkeyAlgorithm = passkeyAlgorithm,
                        cardholderName = cardholderName,
                        cardNumber = cardNumber,
                        cardExpiry = cardExpiry,
                        cardCvv = cardCvv,
                        cardPin = cardPin,
                        identityFullName = identityName,
                        identityEmail = identityEmail,
                        identityPhone = identityPhone,
                        identityAddress = identityAddress,
                        identityDob = identityDob,
                        identityIdNumber = identityIdNum,
                        wifiSsid = wifiSsid,
                        wifiPassword = wifiPassword,
                        apiKey = apiKey,
                        apiSecret = apiSecret,
                        apiEndpoint = apiEndpoint,
                        customFields = customFields.toList(),
                        attachments = attachments.toList(),
                        expiresAt = computedExpiry,
                        rotationIntervalDays = rotationIntervalDays,
                        updatedAt = System.currentTimeMillis()
                    )
                    viewModel.cleanupAttachments(context, deletedAttachments.toList())
                    isSaved = true
                    viewModel.saveItem(updatedItem, onSaved = onNavigateBack)
                }
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showQrScanner) {
        QrCodeScannerDialog(
            onDismiss = { showQrScanner = false },
            onQrCodeScanned = { scannedContent ->
                showQrScanner = false
                val parsed = UriParser.parse(scannedContent)
                if (parsed != null) {
                    totpSecret = parsed.secret
                    if (title.isBlank()) {
                        title = parsed.issuer.ifBlank { parsed.accountName }
                    }
                    if (username.isBlank() && parsed.accountName.isNotBlank()) {
                        username = parsed.accountName
                    }
                } else {
                    totpSecret = scannedContent.trim()
                }
            }
        )
    }

    if (showAttachmentTypeDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAttachmentTypeDialog = false },
            title = {
                Text(
                    text = "Add Private Attachment",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Attachments are AES-256-GCM encrypted in volatile RAM and stored inside Kryptx's zero-disk sandbox.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                            .clickable {
                                showAttachmentTypeDialog = false
                                if (!hasGrantedMediaConsent) {
                                    pendingAttachmentType = AttachmentType.PHOTO
                                    showMediaRationaleDialog = true
                                } else {
                                    launchPhotoPicker()
                                }
                            }
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = KryptxBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Choose Photo from Gallery",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Private photos, ID cards, receipts",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                            .clickable {
                                showAttachmentTypeDialog = false
                                if (!hasGrantedMediaConsent) {
                                    pendingAttachmentType = AttachmentType.DOCUMENT
                                    showMediaRationaleDialog = true
                                } else {
                                    launchDocumentPicker()
                                }
                            }
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Choose Document or Key File",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "PDF, recovery codes, certificates",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAttachmentTypeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showMediaRationaleDialog) {
        val isDoc = pendingAttachmentType == AttachmentType.DOCUMENT
        KryptxPermissionRationaleDialog(
            icon = if (isDoc) Icons.Default.Description else Icons.Default.Image,
            title = if (isDoc) "File & Document Access" else "Photo & Media Access",
            description = if (isDoc) {
                "To select PDFs, documents, or key files for encrypted storage inside your Kryptx vault, Kryptx will open the secure Android document selector. No files are ever shared or uploaded."
            } else {
                "To select photos and ID cards for encrypted storage inside your Kryptx vault, Kryptx will open the secure Android photo selector. No files are ever shared or uploaded."
            },
            privacyGuarantee = "100% Offline: Files are encrypted with AES-256-GCM directly into the vault database and never leave this device.",
            confirmButtonText = "Grant Access",
            dismissButtonText = "Not Now",
            onConfirm = {
                showMediaRationaleDialog = false
                hasGrantedMediaConsent = true
                val isPhoto = pendingAttachmentType != AttachmentType.DOCUMENT
                pendingAttachmentType = null
                if (isPhoto) {
                    launchPhotoPicker()
                } else {
                    launchDocumentPicker()
                }
            },
            onDismiss = {
                showMediaRationaleDialog = false
                pendingAttachmentType = null
            }
        )
    }

    if (showDiscardDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = {
                Text(
                    text = "Discard Changes?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "You have unsaved changes. Are you sure you want to discard them?",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    handleDiscardAndBack()
                }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private enum class AttachmentType {
    PHOTO,
    DOCUMENT
}

private fun resolveFileName(context: Context, uri: Uri): String {
    var name: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        name = cursor.getString(idx)
                    }
                }
            }
        } catch (_: Throwable) {}
    }
    return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Attachment_${System.currentTimeMillis()}"
}

private fun resolveMimeType(context: Context, uri: Uri, fileName: String): String {
    val fromResolver = try { context.contentResolver.getType(uri) } catch (_: Throwable) { null }
    if (!fromResolver.isNullOrBlank() && fromResolver != "application/octet-stream") {
        return fromResolver
    }
    val ext = android.webkit.MimeTypeMap.getFileExtensionFromUrl(fileName)
        ?.ifBlank { fileName.substringAfterLast('.', "") }
    if (!ext.isNullOrBlank()) {
        val mapped = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
        if (!mapped.isNullOrBlank()) return mapped
    }
    return fromResolver ?: "application/octet-stream"
}

private fun isValidLuhn(number: String): Boolean {
    val digits = number.filter { it.isDigit() }
    if (digits.length !in 12..19) return false
    var sum = 0
    var alternate = false
    for (i in digits.length - 1 downTo 0) {
        var d = digits[i] - '0'
        if (alternate) {
            d *= 2
            if (d > 9) d -= 9
        }
        sum += d
        alternate = !alternate
    }
    return sum % 10 == 0
}



