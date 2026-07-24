package com.dms.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * SettingsScreen Jetpack Compose layout presenter for LastMessage.
 * Provides controls for configuring timer intervals, emergency contacts, dispatch strategy,
 * encrypted SMTP credentials, image attachments, fail-safe redundancy settings (Boot Recovery,
 * Low Battery Guardian, Self-Hosted Raspberry Pi / Home-Server Watchdog Web-Ping), and live testing.
 */
class SettingsScreen(
    private val viewModel: SettingsViewModel
) {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content(onBackToMain: () -> Unit) {
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            viewModel.refreshAllState()
        }

        val config by viewModel.configState.collectAsState()
        val contacts by viewModel.contactsState.collectAsState()
        val smtpState by viewModel.smtpState.collectAsState()
        val emergencyMessage by viewModel.emergencyMessageState.collectAsState()
        val statusMessage by viewModel.statusMessage.collectAsState()
        val isTesting by viewModel.isTesting.collectAsState()
        val testResult by viewModel.testResult.collectAsState()

        // Image Picker Launcher
        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { viewModel.addImageAttachmentFromUri(context, it) }
        }

        // Initialize local state
        var selectedIntervalHours by remember {
            mutableStateOf((config.timerIntervalMinutes / 60).toString())
        }
        var selectedDispatchMethod by remember {
            mutableStateOf(config.primaryDispatchMethod)
        }

        var enableBootRecovery by remember { mutableStateOf(config.enableBootRecovery) }
        var enableBatteryWarnings by remember { mutableStateOf(config.enableBatteryWarnings) }
        var enableCloudWatchdog by remember { mutableStateOf(config.enableCloudWatchdog) }
        var watchdogPingUrl by remember { mutableStateOf(config.watchdogPingUrl) }

        var recipientName by remember {
            mutableStateOf(contacts.firstOrNull()?.recipientName ?: "Notfall-Kontakt")
        }
        var recipientPhone by remember {
            mutableStateOf(contacts.firstOrNull()?.phoneNumber ?: "")
        }
        var recipientEmail by remember {
            mutableStateOf(contacts.firstOrNull()?.emailAddress ?: "")
        }

        var smtpHost by remember {
            mutableStateOf(smtpState?.host ?: "smtp.gmail.com")
        }
        var smtpPort by remember {
            mutableStateOf((smtpState?.port ?: 587).toString())
        }
        var smtpUsername by remember {
            mutableStateOf(smtpState?.username ?: "")
        }
        var smtpPassword by remember {
            mutableStateOf(smtpState?.passwordEncrypted ?: "")
        }

        var passwordVisible by remember { mutableStateOf(false) }

        var messageBody by remember {
            mutableStateOf(emergencyMessage.bodyTemplate)
        }

        // Sync state from database
        LaunchedEffect(config) {
            enableBootRecovery = config.enableBootRecovery
            enableBatteryWarnings = config.enableBatteryWarnings
            enableCloudWatchdog = config.enableCloudWatchdog
            if (watchdogPingUrl.isBlank()) watchdogPingUrl = config.watchdogPingUrl
        }
        LaunchedEffect(smtpState) {
            smtpState?.let {
                if (smtpHost.isBlank()) smtpHost = it.host
                if (smtpUsername.isBlank()) smtpUsername = it.username
                if (smtpPassword.isBlank()) smtpPassword = it.passwordEncrypted
            }
        }
        LaunchedEffect(contacts) {
            contacts.firstOrNull()?.let {
                if (recipientName.isBlank() || recipientName == "Notfall-Kontakt") recipientName = it.recipientName
                if (recipientPhone.isBlank()) recipientPhone = it.phoneNumber
                if (recipientEmail.isBlank()) recipientEmail = it.emailAddress
            }
        }
        LaunchedEffect(emergencyMessage) {
            if (messageBody.isBlank()) messageBody = emergencyMessage.bodyTemplate
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackToMain) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LastMessage Einstellungen",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // General Status Message
            statusMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20))
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(16.dp),
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }

            // Live Connection Test Result Card
            testResult?.let { res ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (res.success) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (res.success) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (res.success) "TEST ERFOLGREICH" else "HINWEIS ZUR LÖSUNG",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = res.message,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section 1: Timer Intervall
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "COUNTDOWN INTERVALL",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF81D4FA)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("12", "24", "48", "72", "168").forEach { hours ->
                            FilterChip(
                                selected = selectedIntervalHours == hours,
                                onClick = {
                                    selectedIntervalHours = hours
                                    val mins = hours.toLong() * 60
                                    viewModel.updateConfig(
                                        mins, selectedDispatchMethod, 3, true,
                                        enableBootRecovery, enableBatteryWarnings, enableCloudWatchdog, watchdogPingUrl
                                    )
                                },
                                label = { Text("${hours}h", color = Color.White) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: REDUNDANZ & AUSFALLSCHUTZ (Leerer Akku, Boot Recovery & Eigener Server)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = Color(0xFFFFD54F))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AUSFALLSCHUTZ & EIGENER SERVER",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD54F)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle 1: Boot-Recovery
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("⚡ Sofort-Versand nach Boot/Aufladen", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Text("Falls das Handy aus war und verstrichen ist, wird beim Einschalten/Laden sofort gesendet.", color = Color.Gray, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        Switch(
                            checked = enableBootRecovery,
                            onCheckedChange = {
                                enableBootRecovery = it
                                val mins = selectedIntervalHours.toLong() * 60
                                viewModel.updateConfig(mins, selectedDispatchMethod, 3, true, it, enableBatteryWarnings, enableCloudWatchdog, watchdogPingUrl)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle 2: Low Battery Warning
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🔋 Akku-Warnung (Unter 15%)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Text("Schlägt Alarm, wenn der Akku leer läuft, um rechtzeitig einzuchecken.", color = Color.Gray, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        Switch(
                            checked = enableBatteryWarnings,
                            onCheckedChange = {
                                enableBatteryWarnings = it
                                val mins = selectedIntervalHours.toLong() * 60
                                viewModel.updateConfig(mins, selectedDispatchMethod, 3, true, enableBootRecovery, it, enableCloudWatchdog, watchdogPingUrl)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF333333))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Toggle 3: Self-Hosted Server / Raspberry Pi Watchdog Ping
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🏠 Eigenen Server / Raspberry Pi nutzen", fontWeight = FontWeight.Bold, color = Color(0xFF80DEEA), fontSize = 13.sp)
                            Text("Verbindet die App mit Ihrem eigenen Heimserver. Wenn Ihr Handy leer/aus bleibt, übernimmt Ihr Server automatisch den Notfall-Versand!", color = Color.Gray, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        Switch(
                            checked = enableCloudWatchdog,
                            onCheckedChange = {
                                enableCloudWatchdog = it
                                val mins = selectedIntervalHours.toLong() * 60
                                viewModel.updateConfig(mins, selectedDispatchMethod, 3, true, enableBootRecovery, enableBatteryWarnings, it, watchdogPingUrl)
                            }
                        )
                    }

                    if (enableCloudWatchdog) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Server Info Box
                        Surface(
                            color = Color(0xFF263238),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("ℹ️ EIGENER WATCHDOG SERVER (PYTHON / DOCKER):", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF80DEEA))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Im Projektordner 'server/' befindet sich der kostenlose Python/Docker-Server für Ihren Raspberry Pi oder Linux-Server. Er empfängt die Pings und versendet E-Mails, falls Ihr Handy aus bleibt.", fontSize = 11.sp, color = Color.White, lineHeight = 15.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = watchdogPingUrl,
                            onValueChange = { watchdogPingUrl = it.trim() },
                            label = { Text("Server Ping URL (z.B. http://192.168.1.100:8080/ping)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { viewModel.testWatchdogPing(watchdogPingUrl) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isTesting
                        ) {
                            Icon(imageVector = Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF80DEEA))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("🌐 SERVER-PING JETZT TESTEN", fontSize = 12.sp, color = Color(0xFF80DEEA), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: Dispatch Strategy (SMS vs E-Mail)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "NOTFALL-VERSANDMETHODE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF81D4FA)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val methods = listOf(
                            "SMS" to "Nur SMS",
                            "EMAIL" to "Nur E-Mail",
                            "SMS_THEN_EMAIL" to "SMS (Fallback: E-Mail)",
                            "BOTH" to "SMS + E-Mail gleichzeitig"
                        )
                        methods.forEach { (key, label) ->
                            FilterChip(
                                selected = selectedDispatchMethod == key,
                                onClick = {
                                    selectedDispatchMethod = key
                                    val mins = selectedIntervalHours.toLong() * 60
                                    viewModel.updateConfig(mins, key, 3, true, enableBootRecovery, enableBatteryWarnings, enableCloudWatchdog, watchdogPingUrl)
                                },
                                label = { Text(label, color = Color.White) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 4: Emergency Contact (SMS & E-Mail)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "NOTFALL-EMPFÄNGER (SMS & E-MAIL)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF81D4FA)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = recipientName,
                        onValueChange = { recipientName = it.replace("\n", "").replace("\r", "") },
                        label = { Text("Name des Empfängers") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = recipientPhone,
                        onValueChange = { recipientPhone = it.replace("\n", "").replace("\r", "") },
                        label = { Text("Handynummer für SMS (+49...)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = recipientEmail,
                        onValueChange = { recipientEmail = it.replace("\n", "").replace("\r", "") },
                        label = { Text("E-Mail-Adresse des Empfängers") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 5: Emergency Message & Image Attachments with Small Thumbnail Previews
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "NOTFALL-NACHRICHT & BILDER-ANHÄNGE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF81D4FA)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = messageBody,
                        onValueChange = { messageBody = it },
                        label = { Text("Nachrichtentext") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "📷 NOTFALL-BILDER ANHÄNGEN (MIT VORSCHAU):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (emergencyMessage.attachmentPaths.isEmpty()) {
                        Text(
                            text = "Noch keine Bilder angehängt.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            emergencyMessage.attachmentPaths.forEach { path ->
                                val file = File(path)
                                val bitmap = remember(path) {
                                    try {
                                        if (file.exists()) {
                                            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                                        } else null
                                    } catch (e: Exception) {
                                        null
                                    }
                                }

                                Surface(
                                    color = Color(0xFF263238),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (bitmap != null) {
                                                Image(
                                                    bitmap = bitmap,
                                                    contentDescription = "Thumbnail",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Image,
                                                    contentDescription = null,
                                                    tint = Color(0xFF80DEEA),
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column {
                                                Text(
                                                    text = file.name,
                                                    fontSize = 13.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "${file.length() / 1024} KB",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }

                                        IconButton(onClick = { viewModel.removeAttachment(path) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove",
                                                tint = Color(0xFFFF8A80)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color(0xFF80DEEA))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("📷 BILD AUS GALERIE HINZUFÜGEN", fontSize = 12.sp, color = Color(0xFF80DEEA), fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.addEmergencyContact(recipientName, recipientPhone, recipientEmail, 1)
                            viewModel.saveEmergencyMessage(messageBody)
                            viewModel.testSmsDispatch(recipientPhone)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isTesting
                    ) {
                        Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = Color(0xFFA5D6A7))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("💬 TEST-SMS JETZT SENDEN", fontSize = 12.sp, color = Color(0xFFA5D6A7), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 6: SMTP Provider Presets & Legend Guide
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Color(0xFF81D4FA))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SMTP SCHNELL-AUSWAHL & LEGENDE",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF81D4FA)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Tippen Sie auf Ihre E-Mail-Anbieter Vorlage:",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Gmail" to Pair("smtp.gmail.com", "587"),
                            "GMX" to Pair("mail.gmx.net", "587"),
                            "WEB.DE" to Pair("smtp.web.de", "587"),
                            "Outlook" to Pair("smtp.office365.com", "587")
                        ).forEach { (name, preset) ->
                            Button(
                                onClick = {
                                    smtpHost = preset.first
                                    smtpPort = preset.second
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                            ) {
                                Text(text = name, fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = Color(0xFF263238),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("ℹ️ WICHTIGE EINSTELLUNGEN PRO ANBIETER:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF80DEEA))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("🔴 Gmail: Normales Passwort funktioniert NICHT. Sie MÜSSEN auf myaccount.google.com/apppasswords ein 16-stelliges 'App-Passwort' erstellen.", fontSize = 11.sp, color = Color.White, lineHeight = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("🟡 GMX / WEB.DE: Loggen Sie sich im Browser ein -> Einstellungen -> 'POP3/SMTP-Übertragung erlauben' aktivieren.", fontSize = 11.sp, color = Color.White, lineHeight = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("🔵 Ports: Port 587 (Standard für STARTTLS) oder Port 465 (SSL).", fontSize = 11.sp, color = Color.White, lineHeight = 16.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 7: SMTP Server Credentials
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = Color(0xFF81D4FA))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SMTP E-MAIL-SERVER EINSTELLUNGEN",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF81D4FA)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = smtpHost,
                            onValueChange = { smtpHost = it.replace("\n", "").replace("\r", "").trim() },
                            label = { Text("SMTP Server") },
                            modifier = Modifier.weight(2f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        OutlinedTextField(
                            value = smtpPort,
                            onValueChange = { smtpPort = it.replace("\n", "").replace("\r", "").trim() },
                            label = { Text("Port") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = smtpUsername,
                        onValueChange = { smtpUsername = it.replace("\n", "").replace("\r", "").trim() },
                        label = { Text("Absender-Email / Benutzername") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = smtpPassword,
                        onValueChange = { smtpPassword = it.replace("\n", "").replace("\r", "").trim() },
                        label = { Text("Passwort / App-Passwort") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password",
                                    tint = Color.LightGray
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // LIVE TEST BUTTON FOR EMAIL
                    OutlinedButton(
                        onClick = {
                            val port = smtpPort.toIntOrNull() ?: 587
                            val cleanHost = smtpHost.trim()
                            val cleanUser = smtpUsername.trim()
                            val cleanPassword = smtpPassword.trim()
                            val cleanRecipient = recipientEmail.trim()

                            viewModel.addEmergencyContact(recipientName, recipientPhone, cleanRecipient, 1)
                            viewModel.saveSmtpCredentials(cleanHost, port, cleanUser, cleanPassword, true)
                            viewModel.saveEmergencyMessage(messageBody)
                            viewModel.testSmtpConnection(
                                host = cleanHost,
                                port = port,
                                username = cleanUser,
                                passwordPlain = cleanPassword,
                                recipientEmail = if (cleanRecipient.isNotBlank()) cleanRecipient else cleanUser
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isTesting
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("VERBINDUNG WIRD GEPRÜFT...", fontSize = 12.sp, color = Color.White)
                        } else {
                            Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = Color(0xFF81D4FA))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("📧 E-MAIL VERBINDUNG JETZT TESTEN", fontSize = 12.sp, color = Color(0xFF81D4FA), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Save Button
            Button(
                onClick = {
                    val port = smtpPort.toIntOrNull() ?: 587
                    val cleanHost = smtpHost.trim()
                    val cleanUser = smtpUsername.trim()
                    val cleanPassword = smtpPassword.trim()
                    val cleanRecipient = recipientEmail.trim()

                    viewModel.updateConfig(
                        selectedIntervalHours.toLong() * 60, selectedDispatchMethod, 3, true,
                        enableBootRecovery, enableBatteryWarnings, enableCloudWatchdog, watchdogPingUrl
                    )
                    viewModel.addEmergencyContact(recipientName, recipientPhone, cleanRecipient, 1)
                    viewModel.saveSmtpCredentials(cleanHost, port, cleanUser, cleanPassword, true)
                    viewModel.saveEmergencyMessage(messageBody)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "ALLE EINSTELLUNGEN SPEICHERN", fontWeight = FontWeight.Bold)
            }
        }
    }
}
