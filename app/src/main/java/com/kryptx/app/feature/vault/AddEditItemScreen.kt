package com.kryptx.app.feature.vault

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.core.crypto.EntropyCalculator
import com.kryptx.app.core.designsystem.components.KryptxCard
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.components.KryptxTextField
import com.kryptx.app.core.designsystem.components.KryptxTopBar
import com.kryptx.app.core.designsystem.components.QrCodeScannerDialog
import com.kryptx.app.core.designsystem.components.StrengthBadge
import com.kryptx.app.core.designsystem.components.atmosphericTopGlow
import com.kryptx.app.core.designsystem.components.bounceClick
import com.kryptx.app.core.designsystem.theme.KryptxBlue
import com.kryptx.app.core.designsystem.theme.KryptxIceBlue
import com.kryptx.app.core.designsystem.theme.OledBackground
import com.kryptx.app.core.generator.GeneratorEngine
import com.kryptx.app.core.model.CustomField
import com.kryptx.app.core.model.GeneratorConfig
import com.kryptx.app.core.model.ItemType
import com.kryptx.app.core.model.VaultItem
import com.kryptx.app.core.totp.UriParser
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

    // Login fields
    var username by remember { mutableStateOf(existingItem?.username ?: "") }
    var password by remember { mutableStateOf(existingItem?.password ?: "") }
    var website by remember { mutableStateOf(existingItem?.website ?: "") }
    var totpSecret by remember { mutableStateOf(existingItem?.totpSecret ?: "") }

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

    // Bank Account fields
    var bankName by remember { mutableStateOf(existingItem?.bankName ?: "") }
    var bankAccountNumber by remember { mutableStateOf(existingItem?.bankAccountNumber ?: "") }
    var bankRoutingNumber by remember { mutableStateOf(existingItem?.bankRoutingNumber ?: "") }

    // Crypto fields
    var cryptoWalletAddress by remember { mutableStateOf(existingItem?.cryptoWalletAddress ?: "") }
    var cryptoSeedPhrase by remember { mutableStateOf(existingItem?.cryptoSeedPhrase ?: "") }
    var cryptoNetwork by remember { mutableStateOf(existingItem?.cryptoNetwork ?: "") }

    // SSH fields
    var sshPublicKey by remember { mutableStateOf(existingItem?.sshPublicKey ?: "") }
    var sshPrivateKey by remember { mutableStateOf(existingItem?.sshPrivateKey ?: "") }
    var sshHost by remember { mutableStateOf(existingItem?.sshHost ?: "") }

    // Medical fields
    var medicalBloodType by remember { mutableStateOf(existingItem?.medicalBloodType ?: "") }
    var medicalAllergies by remember { mutableStateOf(existingItem?.medicalAllergies ?: "") }
    var medicalEmergencyContact by remember { mutableStateOf(existingItem?.medicalEmergencyContact ?: "") }

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
        mutableStateListOf<com.kryptx.app.core.model.VaultAttachment>().apply {
            if (existingItem != null) {
                addAll(existingItem.attachments)
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            scope.launch {
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Attachment_${System.currentTimeMillis()}"
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val saved = viewModel.saveAttachment(context, uri, fileName, mimeType)
                if (saved != null) {
                    attachments.add(saved)
                }
            }
        }
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val passwordAnalysis = remember(password) {
        if (password.isNotBlank()) EntropyCalculator.analyze(password) else null
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .atmosphericTopGlow(),
        containerColor = OledBackground,
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
                    color = Color.White.copy(alpha = 0.6f),
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
                                    if (isSelected) KryptxBlue else Color.White.copy(alpha = 0.08f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) KryptxBlue else Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(14.dp)
                                )
                                .bounceClick(scaleDown = 0.94f) { selectedType = type }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = type.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
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
                ItemType.LOGIN -> {
                    KryptxTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = "Username or Email"
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    KryptxTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        isPassword = true,
                        isMonospace = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                val generated = GeneratorEngine.generate(GeneratorConfig())
                                password = generated.value
                            }) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Generate Password",
                                    tint = KryptxBlue
                                )
                            }
                        }
                    )

                    if (passwordAnalysis != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StrengthBadge(strength = passwordAnalysis.strength)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${passwordAnalysis.entropyBits} bits entropy",
                                fontSize = 11.sp,
                                color = KryptxIceBlue.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    KryptxTextField(
                        value = website,
                        onValueChange = { website = it },
                        label = "Website URL (e.g. https://github.com)"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    KryptxTextField(
                        value = totpSecret,
                        onValueChange = { totpSecret = it },
                        label = "2FA / TOTP Secret Key (optional)",
                        trailingIcon = {
                            IconButton(onClick = { showQrScanner = true }) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan 2FA QR Code",
                                    tint = KryptxBlue
                                )
                            }
                        }
                    )
                }

                ItemType.CREDIT_CARD -> {
                    KryptxTextField(
                        value = cardholderName,
                        onValueChange = { cardholderName = it },
                        label = "Cardholder Name"
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    KryptxTextField(
                        value = cardNumber,
                        onValueChange = { cardNumber = it },
                        label = "Card Number",
                        isMonospace = true
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            KryptxTextField(
                                value = cardExpiry,
                                onValueChange = { cardExpiry = it },
                                label = "Expiry (MM/YY)"
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            KryptxTextField(
                                value = cardCvv,
                                onValueChange = { cardCvv = it },
                                label = "CVV",
                                isPassword = true
                            )
                        }
                    }
                }

                ItemType.IDENTITY -> {
                    KryptxTextField(value = identityName, onValueChange = { identityName = it }, label = "Full Name")
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxTextField(value = identityEmail, onValueChange = { identityEmail = it }, label = "Email Address")
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxTextField(value = identityPhone, onValueChange = { identityPhone = it }, label = "Phone Number")
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxTextField(value = identityAddress, onValueChange = { identityAddress = it }, label = "Physical Address")
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxTextField(value = identityIdNum, onValueChange = { identityIdNum = it }, label = "ID / Passport Number", isPassword = true)
                }

                ItemType.WIFI -> {
                    KryptxTextField(value = wifiSsid, onValueChange = { wifiSsid = it }, label = "Wi-Fi SSID")
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxTextField(value = wifiPassword, onValueChange = { wifiPassword = it }, label = "Wi-Fi Password", isPassword = true)
                }

                ItemType.API_KEY -> {
                    KryptxTextField(value = apiKey, onValueChange = { apiKey = it }, label = "API Key / Token", isPassword = true)
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxTextField(value = apiSecret, onValueChange = { apiSecret = it }, label = "API Secret (optional)", isPassword = true)
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxTextField(value = apiEndpoint, onValueChange = { apiEndpoint = it }, label = "Endpoint URL")
                }

                ItemType.BANK_ACCOUNT -> {
                    KryptxTextField(value = cardholderName, onValueChange = { cardholderName = it }, label = "Bank Name")
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxTextField(value = cardNumber, onValueChange = { cardNumber = it }, label = "Account Number", isPassword = true, isMonospace = true)
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxTextField(value = cardPin, onValueChange = { cardPin = it }, label = "Routing Number / Sort Code", isMonospace = true)
                }

                ItemType.CRYPTO_WALLET -> {
                    KryptxTextField(value = website, onValueChange = { website = it }, label = "Network / Blockchain (e.g. Ethereum, Solana)")
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxTextField(value = apiKey, onValueChange = { apiKey = it }, label = "Public Wallet Address", isMonospace = true)
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxTextField(value = password, onValueChange = { password = it }, label = "Recovery Seed Phrase (12/24 words)", isPassword = true, singleLine = false)
                }

                ItemType.SSH_KEY -> {
                    KryptxTextField(value = website, onValueChange = { website = it }, label = "Host / Server (e.g. server.domain.com)")
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxTextField(value = apiKey, onValueChange = { apiKey = it }, label = "Public Key", isMonospace = true, singleLine = false)
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxTextField(value = password, onValueChange = { password = it }, label = "Private Key", isPassword = true, isMonospace = true, singleLine = false)
                }

                ItemType.MEDICAL -> {
                    KryptxTextField(value = identityName, onValueChange = { identityName = it }, label = "Full Name")
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxTextField(value = identityDob, onValueChange = { identityDob = it }, label = "Blood Type (e.g. O+, A-)")
                    Spacer(modifier = Modifier.height(14.dp))
                    KryptxTextField(value = notes, onValueChange = { notes = it }, label = "Known Allergies & Conditions", singleLine = false)
                }

                ItemType.SECURE_NOTE, ItemType.CUSTOM -> {}
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dynamic Custom Fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CUSTOM FIELDS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.6f)
                )
                TextButton(onClick = {
                    customFields.add(CustomField(UUID.randomUUID().toString(), "Field ${customFields.size + 1}", "", false))
                }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = KryptxBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Field", color = KryptxBlue, fontSize = 12.sp)
                }
            }

            customFields.forEachIndexed { index, field ->
                KryptxCard(modifier = Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            KryptxTextField(
                                value = field.label,
                                onValueChange = { customFields[index] = field.copy(label = it) },
                                label = "Field Label"
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            KryptxTextField(
                                value = field.value,
                                onValueChange = { customFields[index] = field.copy(value = it) },
                                label = "Field Value",
                                isPassword = field.isSecured
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text("Secure / Masked", fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f))
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = field.isSecured,
                                    onCheckedChange = { customFields[index] = field.copy(isSecured = it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = KryptxBlue
                                    )
                                )
                            }
                        }
                        IconButton(onClick = { customFields.removeAt(index) }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove Field", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Password Expiration & Rotation Policy
            Text(
                text = "PASSWORD ROTATION & EXPIRATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.6f),
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
                            .background(if (isSelected) KryptxBlue else Color.White.copy(alpha = 0.08f))
                            .clickable { rotationIntervalDays = days }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
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
                    color = Color.White.copy(alpha = 0.6f)
                )
                TextButton(onClick = { filePickerLauncher.launch("*/*") }) {
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
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
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
                        website = website,
                        totpSecret = totpSecret,
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
                        bankName = bankName,
                        bankAccountNumber = bankAccountNumber,
                        bankRoutingNumber = bankRoutingNumber,
                        cryptoWalletAddress = cryptoWalletAddress,
                        cryptoSeedPhrase = cryptoSeedPhrase,
                        cryptoNetwork = cryptoNetwork,
                        sshPublicKey = sshPublicKey,
                        sshPrivateKey = sshPrivateKey,
                        sshHost = sshHost,
                        medicalBloodType = medicalBloodType,
                        medicalAllergies = medicalAllergies,
                        medicalEmergencyContact = medicalEmergencyContact,
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
}
