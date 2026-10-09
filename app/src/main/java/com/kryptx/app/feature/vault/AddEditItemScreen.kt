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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
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
import com.kryptx.app.core.model.VaultAttachment
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

@Composable
fun AddEditItemScreen(
    itemId: String?,
    viewModel: VaultViewModel,
    customAddEditViewModel: com.kryptx.app.feature.vault.editor.AddEditViewModel? = null,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val factory = remember(context) {
        com.kryptx.app.core.di.KryptxViewModelFactory(
            context.applicationContext as? KryptxApplication
                ?: error("applicationContext is not KryptxApplication")
        )
    }
    val addEditViewModel: com.kryptx.app.feature.vault.editor.AddEditViewModel = customAddEditViewModel
        ?: androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)

    val items by viewModel.rawItems.collectAsState()
    val existingItem = remember(itemId, items) {
        items.firstOrNull { it.id == itemId }
    }

    // Initialise the AddEditViewModel once when we know which item (or new type) we're editing.
    val isInitialised = remember { mutableStateOf(false) }
    LaunchedEffect(itemId, existingItem) {
        if (!isInitialised.value) {
            if (itemId != null && existingItem != null) {
                addEditViewModel.loadItem(existingItem)
            } else if (itemId == null) {
                addEditViewModel.initNewItem(com.kryptx.app.core.model.ItemType.LOGIN)
            }
            isInitialised.value = true
        }
    }

    // Observe save results
    val saveResult by addEditViewModel.saveResult.collectAsState()
    var localError by remember { mutableStateOf<String?>(null) }
    var isSaved by remember { mutableStateOf(false) }

    LaunchedEffect(saveResult) {
        when (saveResult) {
            is com.kryptx.app.feature.vault.editor.AddEditViewModel.SaveResult.Saved -> {
                isSaved = true
                addEditViewModel.resetSaveResult()
                onNavigateBack()
            }
            else -> {}
        }
    }

    val editorStateRaw by addEditViewModel.editorState.collectAsState()

    if (itemId != null && existingItem == null) {
        BackHandler(onBack = onNavigateBack)
        if (items.isNotEmpty()) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.Text(
                        text = "Item not found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.OutlinedButton(onClick = onNavigateBack) {
                        Text("Go Back")
                    }
                }
            }
        } else {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        }
        return
    }

    val editorState = editorStateRaw ?: return

    // For new items: allow type switching via the ViewModel
    val selectedType = editorState.itemType

    // Bind flat field state from the typed sealed editor state
    val title = editorState.title
    val isFavorite = editorState.isFavorite
    val notes = editorState.notes
    val customFields = editorState.customFields
    val attachments = editorState.attachments

    // Type-specific fields
    val username = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.Login)?.username
        ?: (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.Passkey)?.username
    val password = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.Login)?.password
    val website = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.Login)?.website
    val totpSecret = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.Login)?.totpSecret

    val passkeyRpId = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.Passkey)?.rpId
    val passkeyUserHandle = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.Passkey)?.userHandle
    val passkeyCredentialId = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.Passkey)?.credentialId
    val passkeyAlgorithm = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.Passkey)?.algorithm

    val cardholderName = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.CreditCard)?.cardholderName
    val cardNumber = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.CreditCard)?.cardNumber
    val cardExpiry = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.CreditCard)?.cardExpiry
    val cardCvv = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.CreditCard)?.cardCvv
    val cardPin = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.CreditCard)?.cardPin

    val identityName = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.Identity)?.fullName
    val identityEmail = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.Identity)?.email
    val identityPhone = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.Identity)?.phone
    val identityAddress = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.Identity)?.address
    val identityDob = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.Identity)?.dob
    val identityIdNum = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.Identity)?.idNumber

    val wifiSsid = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.Wifi)?.ssid
    val wifiPassword = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.Wifi)?.password

    val apiKey = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.ApiKey)?.key
    val apiSecret = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.ApiKey)?.secret
    val apiEndpoint = (editorState as? com.kryptx.app.feature.vault.editor.ItemEditorState.ApiKey)?.endpoint

    var showQrScanner by rememberSaveable(itemId) { mutableStateOf(false) }

    val rotationIntervalDays = editorState.rotationIntervalDays

    val newAttachments = remember(itemId) { mutableStateListOf<VaultAttachment>() }
    val deletedAttachments = remember(itemId) { mutableStateListOf<VaultAttachment>() }

    val isDirty by remember(editorState) {
        derivedStateOf { addEditViewModel.isDirty }
    }

    val scope = rememberCoroutineScope()
    val app = remember(context) { context.applicationContext as? KryptxApplication }

    DisposableEffect(Unit) {
        onDispose {
            app?.sessionManager?.setPickerActive(false)
        }
    }

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
                    val saved = addEditViewModel.saveAttachment(context, uri, fileName, mimeType)
                    if (saved != null) {
                        newAttachments.add(saved)
                    } else {
                        localError = "Failed to encrypt and attach file"
                    }
                } catch (t: Throwable) {
                    localError = "Attachment error: ${t.localizedMessage ?: "Unable to read file"}"
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
                    val saved = addEditViewModel.saveAttachment(context, uri, fileName, mimeType)
                    if (saved != null) {
                        newAttachments.add(saved)
                    } else {
                        localError = "Failed to encrypt and attach file"
                    }
                } catch (t: Throwable) {
                    localError = "Attachment error: ${t.localizedMessage ?: "Unable to read file"}"
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
            localError = "Unable to open photo gallery: ${e.message}"
        }
    }

    val launchDocumentPicker: () -> Unit = {
        app?.sessionManager?.setPickerActive(true)
        try {
            filePickerLauncher.launch("*/*")
        } catch (e: Throwable) {
            app?.sessionManager?.setPickerActive(false)
            localError = "Unable to open file selector: ${e.message}"
        }
    }






    var showDiscardDialog by remember { mutableStateOf(false) }
    val handleDiscardAndBack: () -> Unit = {
        addEditViewModel.discardChanges(context)
        onNavigateBack()
    }
    val safeNavigateBack: () -> Unit = {
        if (isDirty && !isSaved) {
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
            // Sensitive field references live in AddEditViewModel.editorState.
            // The ViewModel's onCleared() is responsible for clearing them.
            // Here we simply notify the ViewModel that the screen is going away
            // without a confirmed save, so it can clean up any uncommitted new attachments.
            if (!isSaved) {
                addEditViewModel.discardChanges(context)
            }
            app?.sessionManager?.setPickerActive(false)
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
                        val pillBg by animateColorAsState(
                            targetValue = if (isSelected) KryptxBlue else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            animationSpec = tween(200),
                            label = "pill_bg_${type.name}"
                        )
                        val pillBorder by animateColorAsState(
                            targetValue = if (isSelected) KryptxBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            animationSpec = tween(200),
                            label = "pill_border_${type.name}"
                        )
                        Box(
                            modifier = Modifier
                                .testTag("item_type_pill_${type.name.lowercase()}")
                                .clip(RoundedCornerShape(14.dp))
                                .background(pillBg)
                                .border(
                                    1.dp,
                                    pillBorder,
                                    RoundedCornerShape(14.dp)
                                )
                                .bounceClick(scaleDown = 0.94f) { addEditViewModel.switchType(type) }
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
                value = title.value,
                onValueChange = { title.value = it },
                label = "Title (e.g. Google, Chase Bank, Home Wi-Fi)",
                placeholder = "Required",
                modifier = Modifier.testTag("item_title_field")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Type-specific Form Inputs
            when (selectedType) {
                ItemType.LOGIN -> LoginFormFields(
                    username = username?.value ?: "",
                    onUsernameChange = { username?.value = it },
                    password = password?.value ?: "",
                    onPasswordChange = { password?.value = it },
                    website = website?.value ?: "",
                    onWebsiteChange = { website?.value = it },
                    totpSecret = totpSecret?.value ?: "",
                    onTotpSecretChange = { totpSecret?.value = it },
                    onScanQrClick = requestCameraOrOpenScanner
                )
                ItemType.PASSKEY -> PasskeyFormFields(
                    passkeyRpId = passkeyRpId?.value ?: "",
                    onPasskeyRpIdChange = {
                        passkeyRpId?.value = it
                        if (title.value.isBlank()) title.value = it.removePrefix("www.").replaceFirstChar { char -> char.uppercase() }
                    },
                    username = username?.value ?: "",
                    onUsernameChange = { username?.value = it },
                    passkeyCredentialId = passkeyCredentialId?.value ?: "",
                    onPasskeyCredentialIdChange = { passkeyCredentialId?.value = it },
                    passkeyAlgorithm = passkeyAlgorithm?.value ?: "ES256 (ECDSA P-256)",
                    onPasskeyAlgorithmChange = { passkeyAlgorithm?.value = it }
                )
                ItemType.CREDIT_CARD -> CreditCardFormFields(
                    cardholderName = cardholderName?.value ?: "",
                    onCardholderNameChange = { cardholderName?.value = it },
                    cardNumber = cardNumber?.value ?: "",
                    onCardNumberChange = { cardNumber?.value = it },
                    cardExpiry = cardExpiry?.value ?: "",
                    onCardExpiryChange = { cardExpiry?.value = it },
                    cardCvv = cardCvv?.value ?: "",
                    onCardCvvChange = { cardCvv?.value = it },
                    cardPin = cardPin?.value ?: "",
                    onCardPinChange = { cardPin?.value = it }
                )
                ItemType.IDENTITY -> IdentityFormFields(
                    name = identityName?.value ?: "",
                    onNameChange = { identityName?.value = it },
                    email = identityEmail?.value ?: "",
                    onEmailChange = { identityEmail?.value = it },
                    phone = identityPhone?.value ?: "",
                    onPhoneChange = { identityPhone?.value = it },
                    address = identityAddress?.value ?: "",
                    onAddressChange = { identityAddress?.value = it },
                    dob = identityDob?.value ?: "",
                    onDobChange = { identityDob?.value = it },
                    idNum = identityIdNum?.value ?: "",
                    onIdNumChange = { identityIdNum?.value = it }
                )
                ItemType.WIFI -> WifiFormFields(
                    ssid = wifiSsid?.value ?: "",
                    onSsidChange = { wifiSsid?.value = it },
                    password = wifiPassword?.value ?: "",
                    onPasswordChange = { wifiPassword?.value = it }
                )
                ItemType.API_KEY -> ApiKeyFormFields(
                    apiKey = apiKey?.value ?: "",
                    onApiKeyChange = { apiKey?.value = it },
                    apiSecret = apiSecret?.value ?: "",
                    onApiSecretChange = { apiSecret?.value = it },
                    apiEndpoint = apiEndpoint?.value ?: "",
                    onApiEndpointChange = { apiEndpoint?.value = it }
                )
                ItemType.SECURE_NOTE -> {
                    Column {
                        if (existingItem == null && notes.value.isBlank()) {
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
                                            .clickable { notes.value = templateText }
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
                            value = notes.value,
                            onValueChange = { notes.value = it },
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
                rotationIntervalDays = rotationIntervalDays.value,
                onIntervalSelected = { rotationIntervalDays.value = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Encrypted Document & Photo Attachments
            EncryptedAttachmentsSection(
                attachments = attachments,
                onAddClicked = { showAttachmentTypeDialog = true },
                onDeleteClicked = { index, att ->
                    addEditViewModel.scheduleAttachmentDeletion(context, att)
                }
            )

            // Notes field (for non-SECURE_NOTE items)
            if (selectedType != ItemType.SECURE_NOTE) {
                Spacer(modifier = Modifier.height(14.dp))
                KryptxTextField(
                    value = notes.value,
                    onValueChange = { notes.value = it },
                    label = "Secure Notes",
                    singleLine = false,
                    maxLines = 5
                )
            }

            val errorMessage = localError ?: (saveResult as? com.kryptx.app.feature.vault.editor.AddEditViewModel.SaveResult.Error)?.message
            val isSaving = saveResult is com.kryptx.app.feature.vault.editor.AddEditViewModel.SaveResult.Saving

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
                text = when {
                    isSaving -> "Saving…"
                    existingItem != null -> "Save Changes"
                    else -> "Save to Vault"
                },
                containerColor = KryptxBlue,
                contentColor = Color.White,
                enabled = !isSaving,
                modifier = Modifier.testTag("save_vault_item_button"),
                onClick = { addEditViewModel.confirmSave(context) }
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
                    totpSecret?.value = parsed.secret
                    if (title.value.isBlank()) {
                        title.value = parsed.issuer.ifBlank { parsed.accountName }
                    }
                    if (username?.value.isNullOrBlank() && parsed.accountName.isNotBlank()) {
                        username?.value = parsed.accountName
                    }
                } else {
                    totpSecret?.value = scannedContent.trim()
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



