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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

    var selectedType by remember { mutableStateOf(existingItem?.type ?: ItemType.LOGIN) }
    var title by remember { mutableStateOf(existingItem?.title ?: "") }
    var isFavorite by remember { mutableStateOf(existingItem?.isFavorite ?: false) }
    var notes by remember { mutableStateOf(existingItem?.notes ?: "") }
    var showQrScanner by remember { mutableStateOf(false) }

    var hasPopulated by remember { mutableStateOf(existingItem != null) }

    // Login fields
    var username by remember { mutableStateOf(existingItem?.username ?: "") }
    var password by remember { mutableStateOf(existingItem?.password ?: "") }
    var website by remember { mutableStateOf(existingItem?.website ?: "") }
    var totpSecret by remember { mutableStateOf(existingItem?.totpSecret ?: "") }

    // Passkey fields
    var passkeyRpId by remember { mutableStateOf(existingItem?.passkeyRpId ?: "") }
    var passkeyUserHandle by remember { mutableStateOf(existingItem?.passkeyUserHandle ?: "") }
    var passkeyCredentialId by remember { mutableStateOf(existingItem?.passkeyCredentialId ?: "") }
    var passkeyAlgorithm by remember { mutableStateOf(existingItem?.passkeyAlgorithm ?: "ES256 (ECDSA P-256)") }

    // Credit card fields
    var cardholderName by remember { mutableStateOf(existingItem?.cardholderName ?: "") }
    var cardNumber by remember { mutableStateOf(existingItem?.cardNumber ?: "") }
    var cardExpiry by remember { mutableStateOf(existingItem?.cardExpiry ?: "") }
    var cardCvv by remember { mutableStateOf(existingItem?.cardCvv ?: "") }
    var cardPin by remember { mutableStateOf(existingItem?.cardPin ?: "") }

    // Identity fields
    var identityName by remember { mutableStateOf(existingItem?.identityFullName ?: "") }
    var identityEmail by remember { mutableStateOf(existingItem?.identityEmail ?: "") }
    var identityPhone by remember { mutableStateOf(existingItem?.identityPhone ?: "") }
    var identityAddress by remember { mutableStateOf(existingItem?.identityAddress ?: "") }
    var identityDob by remember { mutableStateOf(existingItem?.identityDob ?: "") }
    var identityIdNum by remember { mutableStateOf(existingItem?.identityIdNumber ?: "") }

    // Wi-Fi fields
    var wifiSsid by remember { mutableStateOf(existingItem?.wifiSsid ?: "") }
    var wifiPassword by remember { mutableStateOf(existingItem?.wifiPassword ?: "") }

    // API Key fields
    var apiKey by remember { mutableStateOf(existingItem?.apiKey ?: "") }
    var apiSecret by remember { mutableStateOf(existingItem?.apiSecret ?: "") }
    var apiEndpoint by remember { mutableStateOf(existingItem?.apiEndpoint ?: "") }

    // Custom fields list
    val customFields = remember {
        mutableStateListOf<CustomField>().apply {
            if (existingItem != null) {
                addAll(existingItem.customFields)
            }
        }
    }

    var rotationIntervalDays by remember { mutableStateOf(existingItem?.rotationIntervalDays) }
    val attachments = remember {
        mutableStateListOf<VaultAttachment>().apply {
            if (existingItem != null) {
                addAll(existingItem.attachments)
            }
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
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                context.startActivity(Intent.createChooser(intent, "Select Photo"))
            } catch (e2: Throwable) {
                app?.sessionManager?.setPickerActive(false)
                errorMessage = "Unable to open photo gallery: ${e2.message}"
            }
        }
    }

    val launchDocumentPicker: () -> Unit = {
        app?.sessionManager?.setPickerActive(true)
        try {
            filePickerLauncher.launch("*/*")
        } catch (e: Throwable) {
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                context.startActivity(Intent.createChooser(intent, "Select Document or File"))
            } catch (e2: Throwable) {
                app?.sessionManager?.setPickerActive(false)
                errorMessage = "Unable to open file selector: ${e2.message}"
            }
        }
    }




    LaunchedEffect(existingItem) {
        val item = existingItem ?: return@LaunchedEffect
        if (hasPopulated) return@LaunchedEffect
        hasPopulated = true
        selectedType = item.type
        title = item.title
        isFavorite = item.isFavorite
        notes = item.notes
        username = item.username
        password = item.password
        website = item.website
        totpSecret = item.totpSecret
        passkeyRpId = item.passkeyRpId
        passkeyUserHandle = item.passkeyUserHandle
        passkeyCredentialId = item.passkeyCredentialId
        passkeyAlgorithm = item.passkeyAlgorithm
        cardholderName = item.cardholderName
        cardNumber = item.cardNumber
        cardExpiry = item.cardExpiry
        cardCvv = item.cardCvv
        cardPin = item.cardPin
        identityName = item.identityFullName
        identityEmail = item.identityEmail
        identityPhone = item.identityPhone
        identityAddress = item.identityAddress
        identityDob = item.identityDob
        identityIdNum = item.identityIdNumber
        wifiSsid = item.wifiSsid
        wifiPassword = item.wifiPassword
        apiKey = item.apiKey
        apiSecret = item.apiSecret
        rotationIntervalDays = item.rotationIntervalDays
        customFields.clear()
        customFields.addAll(item.customFields)
        attachments.clear()
        attachments.addAll(item.attachments)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .atmosphericTopGlow(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            KryptxTopBar(
                title = if (existingItem != null) "Edit Item" else "New Vault Item",
                showBackButton = true,
                onBackClick = onNavigateBack
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
                placeholder = "Required"
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
                    }
                }
                ItemType.CUSTOM -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Custom Fields
            CustomFieldsEditor(customFields = customFields)

            Spacer(modifier = Modifier.height(14.dp))

            // Password Rotation & Expiration
            Text(
                text = "PASSWORD ROTATION & EXPIRATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    null to "Never",
                    30 to "30 Days",
                    60 to "60 Days",
                    90 to "90 Days",
                    180 to "180 Days",
                    365 to "1 Year"
                ).forEach { (days, label) ->
                    val isSelected = rotationIntervalDays == days
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) KryptxBlue else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, if (isSelected) KryptxBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .clickable { rotationIntervalDays = days }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Encrypted Document & Photo Attachments
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ENCRYPTED ATTACHMENTS (${attachments.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { showAttachmentTypeDialog = true }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = KryptxBlue)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add File / Photo", color = KryptxBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (attachments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    attachments.forEachIndexed { index, att ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = att.fileName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${att.formattedSize} • AES-256-GCM Encrypted",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            viewModel.deleteAttachment(context, att)
                                            attachments.removeAt(index)
                                        }
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Notes field
            KryptxTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "Secure Notes",
                singleLine = false,
                maxLines = 5
            )

            AnimatedVisibility(visible = errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            KryptxPrimaryButton(
                text = if (existingItem != null) "Save Changes" else "Save to Vault",
                containerColor = KryptxBlue,
                contentColor = Color.White,
                onClick = {
                    if (title.isBlank()) {
                        errorMessage = "Title cannot be empty"
                        return@KryptxPrimaryButton
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
                        customFields = customFields.toList(),
                        attachments = attachments.toList(),
                        expiresAt = computedExpiry,
                        rotationIntervalDays = rotationIntervalDays,
                        updatedAt = System.currentTimeMillis()
                    )

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

