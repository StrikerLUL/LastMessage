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
 * Provides controls for language selection (DE / EN), countdown timer intervals, configurable Grace Period (Gnadenfrist),
 * Biometric & PIN Lock, Panic PIN, Auto-Delete sensitive data, GPS location & Last Known Location history,
 * Voice Audio Notes, emergency contacts, dispatch strategy, encrypted SMTP credentials, image attachments, fail-safe redundancy, and live testing.
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

        // Audio Note Picker Launcher
        val audioPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { viewModel.addAudioNoteFromUri(context, it) }
        }

        // Initialize local state
        var selectedLanguage by remember { mutableStateOf(config.language) }
        val isEn = selectedLanguage == "EN"

        var selectedIntervalHours by remember {
            mutableStateOf((config.timerIntervalMinutes / 60).toString())
        }
        var selectedGraceHours by remember {
            mutableStateOf((config.gracePeriodMinutes / 60).toString())
        }
        var selectedDispatchMethod by remember {
            mutableStateOf(config.primaryDispatchMethod)
        }

        var enableBootRecovery by remember { mutableStateOf(config.enableBootRecovery) }
        var enableBatteryWarnings by remember { mutableStateOf(config.enableBatteryWarnings) }
        var enableCloudWatchdog by remember { mutableStateOf(config.enableCloudWatchdog) }
        var watchdogPingUrl by remember { mutableStateOf(config.watchdogPingUrl) }

        var enableBiometricLock by remember { mutableStateOf(config.enableBiometricLock) }
        var appPin by remember { mutableStateOf(config.appPin) }
        var panicPin by remember { mutableStateOf(config.panicPin) }
        var autoDeleteAfterDispatch by remember { mutableStateOf(config.autoDeleteAfterDispatch) }

        var enableGpsLocation by remember { mutableStateOf(config.enableGpsLocation) }
        var lastKnownLocationUrl by remember { mutableStateOf(config.lastKnownLocationUrl) }

        var recipientName by remember {
            mutableStateOf(contacts.firstOrNull()?.recipientName ?: (if (isEn) "Emergency Contact" else "Notfall-Kontakt"))
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
            selectedLanguage = config.language
            enableBootRecovery = config.enableBootRecovery
            enableBatteryWarnings = config.enableBatteryWarnings
            enableCloudWatchdog = config.enableCloudWatchdog
            selectedGraceHours = (config.gracePeriodMinutes / 60).toString()
            enableBiometricLock = config.enableBiometricLock
            if (appPin.isBlank()) appPin = config.appPin
            if (panicPin.isBlank()) panicPin = config.panicPin
            autoDeleteAfterDispatch = config.autoDeleteAfterDispatch
            enableGpsLocation = config.enableGpsLocation
            if (lastKnownLocationUrl.isBlank()) lastKnownLocationUrl = config.lastKnownLocationUrl
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
                if (recipientName.isBlank() || recipientName == "Notfall-Kontakt" || recipientName == "Emergency Contact") recipientName = it.recipientName
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
                    text = if (isEn) "LastMessage Settings" else "LastMessage Einstellungen",
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
                                text = if (res.success) (if (isEn) "TEST SUCCESSFUL" else "TEST ERFOLGREICH") else (if (isEn) "DIAGNOSTIC ADVICE" else "HINWEIS ZUR LÖSUNG"),
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

            // Section 0: SPRACHE / LANGUAGE SWITCHER
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEn) "APP LANGUAGE" else "SPRACHE / LANGUAGE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF81D4FA)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterChip(
                            selected = selectedLanguage == "DE",
                            onClick = {
                                selectedLanguage = "DE"
                                val mins = selectedIntervalHours.toLong() * 60
                                val graceMins = selectedGraceHours.toLong() * 60
                                viewModel.updateConfig(
                                    mins, selectedDispatchMethod, 3, true,
                                    "DE", enableBootRecovery, enableBatteryWarnings, enableCloudWatchdog, watchdogPingUrl, graceMins,
                                    enableBiometricLock, appPin, panicPin, autoDeleteAfterDispatch, enableGpsLocation, lastKnownLocationUrl
                                )
                            },
                            label = { Text("Deutsch 🇩🇪", color = Color.White) },
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = selectedLanguage == "EN",
                            onClick = {
                                selectedLanguage = "EN"
                                val mins = selectedIntervalHours.toLong() * 60
                                val graceMins = selectedGraceHours.toLong() * 60
                                viewModel.updateConfig(
                                    mins, selectedDispatchMethod, 3, true,
                                    "EN", enableBootRecovery, enableBatteryWarnings, enableCloudWatchdog, watchdogPingUrl, graceMins,
                                    enableBiometricLock, appPin, panicPin, autoDeleteAfterDispatch, enableGpsLocation, lastKnownLocationUrl
                                )
                            },
                            label = { Text("English 🇬🇧", color = Color.White) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 0B: STANDORT, GPS & LETZTER BEKANNTER STANDORT (LOCATION HISTORY)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFFF4081))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEn) "GPS LOCATION & MAPS LINK (OPTIONAL)" else "GPS-STANDORT & GOOGLE MAPS LINK (OPTIONAL)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF4081)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (isEn) "📍 Append Google Maps Location Link" else "📍 Google Maps Standort-Link mitsenden", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Text(if (isEn) "Appends a direct Google Maps GPS link to emergency SMS & Email." else "Fügt der Notfall-SMS und E-Mail automatisch einen direkten Google Maps Link hinzu.", color = Color.Gray, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        Switch(
                            checked = enableGpsLocation,
                            onCheckedChange = {
                                enableGpsLocation = it
                                val mins = selectedIntervalHours.toLong() * 60
                                val graceMins = selectedGraceHours.toLong() * 60
                                viewModel.updateConfig(mins, selectedDispatchMethod, 3, true, selectedLanguage, enableBootRecovery, enableBatteryWarnings, enableCloudWatchdog, watchdogPingUrl, graceMins, enableBiometricLock, appPin, panicPin, autoDeleteAfterDispatch, it, lastKnownLocationUrl)
                            }
                        )
                    }

                    if (enableGpsLocation) {
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = lastKnownLocationUrl,
                            onValueChange = { lastKnownLocationUrl = it.trim() },
                            label = { Text(if (isEn) "Last Known Location Link (e.g. https://maps.google.com/?q=lat,lng)" else "Zuletzt bekannter Standort Link (z.B. https://maps.google.com/?q=52.52,13.40)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 0C: SICHERHEIT, BIOMETRIE, PANIC-PIN & AUTO-DELETE
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color(0xFFEA80FC))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEn) "SECURITY, BIOMETRICS & PANIC PIN" else "SICHERHEIT, BIOMETRIE & NÖTIGUNGS-PIN",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEA80FC)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Biometric / PIN Lock Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (isEn) "🔒 App PIN & Biometric Protection" else "🔒 App PIN & Biometrischer Schutz", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Text(if (isEn) "Protects app settings and check-in with fingerprint / PIN." else "Schützt App-Einstellungen und Check-in vor fremdem Zugriff.", color = Color.Gray, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        Switch(
                            checked = enableBiometricLock,
                            onCheckedChange = {
                                enableBiometricLock = it
                                val mins = selectedIntervalHours.toLong() * 60
                                val graceMins = selectedGraceHours.toLong() * 60
                                viewModel.updateConfig(mins, selectedDispatchMethod, 3, true, selectedLanguage, enableBootRecovery, enableBatteryWarnings, enableCloudWatchdog, watchdogPingUrl, graceMins, it, appPin, panicPin, autoDeleteAfterDispatch, enableGpsLocation, lastKnownLocationUrl)
                            }
                        )
                    }

                    if (enableBiometricLock) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = appPin,
                            onValueChange = { appPin = it.trim() },
                            label = { Text(if (isEn) "Main App PIN (e.g. 1234)" else "Haupt App-PIN (z.B. 1234)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF333333))
                    Spacer(modifier = Modifier.height(14.dp))

                    // PANIC PIN / Nötigungs-PIN
                    Column {
                        Text(if (isEn) "🚨 Panic PIN (Duress Trigger)" else "🚨 Nötigungs-PIN (Panic PIN)", fontWeight = FontWeight.Bold, color = Color(0xFFFF8A80), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isEn)
                                "If forced to cancel the timer, enter this Panic PIN. The app feigns success, but secretly triggers emergency dispatch immediately in the background!"
                            else
                                "Falls Sie gezwungen werden, den Timer abzubrechen, geben Sie diesen PIN ein. Die App tut so als ob es klappt, löst aber heimlich im Hintergrund sofort den Notfall-Ruf aus!",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = panicPin,
                            onValueChange = { panicPin = it.trim() },
                            label = { Text(if (isEn) "Panic PIN (e.g. 9999)" else "Nötigungs-PIN (z.B. 9999)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF333333))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Auto-Delete After Dispatch Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (isEn) "🔥 Auto-Delete Data After Dispatch" else "🔥 Notfall-Daten nach Versand löschen", fontWeight = FontWeight.Bold, color = Color(0xFFFFAB40), fontSize = 13.sp)
                            Text(if (isEn) "Automatically purges emergency text & photo attachments from device after emergency dispatch." else "Löscht Notfall-Texte & Bilder nach erfolgreichem Notfall-Versand automatisch vom Gerät.", color = Color.Gray, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        Switch(
                            checked = autoDeleteAfterDispatch,
                            onCheckedChange = {
                                autoDeleteAfterDispatch = it
                                val mins = selectedIntervalHours.toLong() * 60
                                val graceMins = selectedGraceHours.toLong() * 60
                                viewModel.updateConfig(mins, selectedDispatchMethod, 3, true, selectedLanguage, enableBootRecovery, enableBatteryWarnings, enableCloudWatchdog, watchdogPingUrl, graceMins, enableBiometricLock, appPin, panicPin, it, enableGpsLocation, lastKnownLocationUrl)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Timer Intervall
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEn) "COUNTDOWN INTERVAL" else "COUNTDOWN INTERVALL",
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
                                    val graceMins = selectedGraceHours.toLong() * 60
                                    viewModel.updateConfig(
                                        mins, selectedDispatchMethod, 3, true,
                                        selectedLanguage, enableBootRecovery, enableBatteryWarnings, enableCloudWatchdog, watchdogPingUrl, graceMins,
                                        enableBiometricLock, appPin, panicPin, autoDeleteAfterDispatch, enableGpsLocation, lastKnownLocationUrl
                                    )
                                },
                                label = { Text("${hours}h", color = Color.White) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1B: GNADENFRIST / GRACE PERIOD (SAFETY BUFFER)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = Color(0xFFFF7043))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEn) "SAFETY GRACE PERIOD (AFTER TIMER EXPIRES)" else "GNADENFRIST (NACH COUNTDOWN-ABLAUF)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF7043)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isEn)
                            "After the main timer expires, emergency alerts are NOT sent immediately. You get an additional grace buffer period with hourly push warnings to check in or cancel!"
                        else
                            "Nach Ablauf des Haupt-Timers wird der Notfall-Ruf NOTSCHUTZ-MÄSSIG verzögert. Sie erhalten stündliche Warn-Pushs zum Abbrechen!",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("0" to "0h", "1" to "1h", "3" to "3h", "6" to "6h", "12" to "12h", "24" to "24h").forEach { (hours, label) ->
                            FilterChip(
                                selected = selectedGraceHours == hours,
                                onClick = {
                                    selectedGraceHours = hours
                                    val mins = selectedIntervalHours.toLong() * 60
                                    val graceMins = hours.toLong() * 60
                                    viewModel.updateConfig(
                                        mins, selectedDispatchMethod, 3, true,
                                        selectedLanguage, enableBootRecovery, enableBatteryWarnings, enableCloudWatchdog, watchdogPingUrl, graceMins,
                                        enableBiometricLock, appPin, panicPin, autoDeleteAfterDispatch, enableGpsLocation, lastKnownLocationUrl
                                    )
                                },
                                label = { Text(if (hours == "0") (if (isEn) "Instant" else "Sofort") else label, color = Color.White, fontSize = 11.sp) }
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
                            text = if (isEn) "FAIL-SAFE & REDUNDANCY" else "AUSFALLSCHUTZ & EIGENER SERVER",
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
                            Text(if (isEn) "⚡ Instant Dispatch After Boot/Recharge" else "⚡ Sofort-Versand nach Boot/Aufladen", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Text(if (isEn) "If phone was dead and timer expired, dispatches emergency alerts immediately upon boot." else "Falls das Handy aus war und verstrichen ist, wird beim Einschalten/Laden sofort gesendet.", color = Color.Gray, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        Switch(
                            checked = enableBootRecovery,
                            onCheckedChange = {
                                enableBootRecovery = it
                                val mins = selectedIntervalHours.toLong() * 60
                                val graceMins = selectedGraceHours.toLong() * 60
                                viewModel.updateConfig(mins, selectedDispatchMethod, 3, true, selectedLanguage, it, enableBatteryWarnings, enableCloudWatchdog, watchdogPingUrl, graceMins, enableBiometricLock, appPin, panicPin, autoDeleteAfterDispatch, enableGpsLocation, lastKnownLocationUrl)
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
                            Text(if (isEn) "🔋 Low Battery Alert (Below 15%)" else "🔋 Akku-Warnung (Unter 15%)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Text(if (isEn) "Warns when battery is running low so you can plug in or check in." else "Schlägt Alarm, wenn der Akku leer läuft, um rechtzeitig einzuchecken.", color = Color.Gray, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        Switch(
                            checked = enableBatteryWarnings,
                            onCheckedChange = {
                                enableBatteryWarnings = it
                                val mins = selectedIntervalHours.toLong() * 60
                                val graceMins = selectedGraceHours.toLong() * 60
                                viewModel.updateConfig(mins, selectedDispatchMethod, 3, true, selectedLanguage, enableBootRecovery, it, enableCloudWatchdog, watchdogPingUrl, graceMins, enableBiometricLock, appPin, panicPin, autoDeleteAfterDispatch, enableGpsLocation, lastKnownLocationUrl)
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
                            Text(if (isEn) "🏠 Self-Hosted Server / Raspberry Pi Watchdog" else "🏠 Eigenen Server / Raspberry Pi nutzen", fontWeight = FontWeight.Bold, color = Color(0xFF80DEEA), fontSize = 13.sp)
                            Text(if (isEn) "Sends a heartbeat ping on check-in to your server. If your phone stays dead, your home server sends emergency emails!" else "Verbindet die App mit Ihrem eigenen Heimserver. Wenn Ihr Handy leer/aus bleibt, übernimmt Ihr Server automatisch den Notfall-Versand!", color = Color.Gray, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        Switch(
                            checked = enableCloudWatchdog,
                            onCheckedChange = {
                                enableCloudWatchdog = it
                                val mins = selectedIntervalHours.toLong() * 60
                                val graceMins = selectedGraceHours.toLong() * 60
                                viewModel.updateConfig(mins, selectedDispatchMethod, 3, true, selectedLanguage, enableBootRecovery, enableBatteryWarnings, it, watchdogPingUrl, graceMins, enableBiometricLock, appPin, panicPin, autoDeleteAfterDispatch, enableGpsLocation, lastKnownLocationUrl)
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
                                Text(if (isEn) "ℹ️ SELF-HOSTED WATCHDOG SERVER (PYTHON / DOCKER):" else "ℹ️ EIGENER WATCHDOG SERVER (PYTHON / DOCKER):", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF80DEEA))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(if (isEn) "The 'server/' directory contains the free Python/Docker server for your Raspberry Pi or Linux server." else "Im Projektordner 'server/' befindet sich der kostenlose Python/Docker-Server für Ihren Raspberry Pi oder Linux-Server.", fontSize = 11.sp, color = Color.White, lineHeight = 15.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = watchdogPingUrl,
                            onValueChange = { watchdogPingUrl = it.trim() },
                            label = { Text(if (isEn) "Server Ping URL (e.g. http://192.168.1.100:8080/ping)" else "Server Ping URL (z.B. http://192.168.1.100:8080/ping)") },
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
                            Text(if (isEn) "🌐 TEST SERVER PING NOW" else "🌐 SERVER-PING JETZT TESTEN", fontSize = 12.sp, color = Color(0xFF80DEEA), fontWeight = FontWeight.Bold)
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
                        text = if (isEn) "EMERGENCY DISPATCH METHOD" else "NOTFALL-VERSANDMETHODE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF81D4FA)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val methods = if (isEn) listOf(
                            "SMS" to "SMS Only",
                            "EMAIL" to "Email Only",
                            "SMS_THEN_EMAIL" to "SMS (Fallback: Email)",
                            "BOTH" to "SMS + Email Simultaneously"
                        ) else listOf(
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
                                    val graceMins = selectedGraceHours.toLong() * 60
                                    viewModel.updateConfig(mins, key, 3, true, selectedLanguage, enableBootRecovery, enableBatteryWarnings, enableCloudWatchdog, watchdogPingUrl, graceMins, enableBiometricLock, appPin, panicPin, autoDeleteAfterDispatch, enableGpsLocation, lastKnownLocationUrl)
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
                        text = if (isEn) "EMERGENCY RECIPIENT (SMS & EMAIL)" else "NOTFALL-EMPFÄNGER (SMS & E-MAIL)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF81D4FA)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = recipientName,
                        onValueChange = { recipientName = it.replace("\n", "").replace("\r", "") },
                        label = { Text(if (isEn) "Recipient Name" else "Name des Empfängers") },
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
                        label = { Text(if (isEn) "Phone Number (+49...)" else "Handynummer für SMS (+49...)") },
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
                        label = { Text(if (isEn) "Recipient Email Address" else "E-Mail-Adresse des Empfängers") },
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

            // Section 5: Emergency Message, Voice Audio Note & Image Attachments
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isEn) "EMERGENCY MESSAGE, VOICE NOTE & MEDIA" else "NOTFALL-NACHRICHT, SPRACHBOX & MEDIEN",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF81D4FA)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = messageBody,
                        onValueChange = { messageBody = it },
                        label = { Text(if (isEn) "Message Text" else "Nachrichtentext") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // VOICE AUDIO NOTE SECTION
                    Text(
                        text = if (isEn) "🎙️ EMERGENCY VOICE AUDIO NOTE (EMAIL ATTACHMENT):" else "🎙️ SPRACHNACHRICHT / AUDIO-NOTIZ (E-MAIL ANHANG):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD54F)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (emergencyMessage.audioNotePath.isBlank()) {
                        OutlinedButton(
                            onClick = { audioPickerLauncher.launch("audio/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = Color(0xFFFFD54F))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isEn) "🎙️ ADD AUDIO VOICE NOTE (.m4a / .mp3)" else "🎙️ SPRACHNACHRICHT DATEI HINZUFÜGEN (.m4a / .mp3)", fontSize = 12.sp, color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        val audioFile = File(emergencyMessage.audioNotePath)
                        Surface(
                            color = Color(0xFF37474F),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(audioFile.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${audioFile.length() / 1024} KB", color = Color.Gray, fontSize = 11.sp)
                                    }
                                }
                                IconButton(onClick = { viewModel.removeAudioNote() }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete audio", tint = Color(0xFFFF8A80))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isEn) "📷 ATTACHED EMERGENCY PHOTOS:" else "📷 NOTFALL-BILDER ANHÄNGEN (MIT VORSCHAU):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (emergencyMessage.attachmentPaths.isEmpty()) {
                        Text(
                            text = if (isEn) "No photos attached yet." else "Noch keine Bilder angehängt.",
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
                        Text(if (isEn) "📷 ADD PHOTO FROM GALLERY" else "📷 BILD AUS GALERIE HINZUFÜGEN", fontSize = 12.sp, color = Color(0xFF80DEEA), fontWeight = FontWeight.Bold)
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
                        Text(if (isEn) "💬 SEND TEST SMS NOW" else "💬 TEST-SMS JETZT SENDEN", fontSize = 12.sp, color = Color(0xFFA5D6A7), fontWeight = FontWeight.Bold)
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
                            text = if (isEn) "SMTP PROVIDER PRESETS & GUIDELINES" else "SMTP SCHNELL-AUSWAHL & LEGENDE",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF81D4FA)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isEn) "Tap an email provider preset to fill server host & port:" else "Tippen Sie auf Ihre E-Mail-Anbieter Vorlage:",
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
                            Text(if (isEn) "ℹ️ PROVIDER GUIDELINES:" else "ℹ️ WICHTIGE EINSTELLUNGEN PRO ANBIETER:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF80DEEA))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(if (isEn) "🔴 Gmail: Regular passwords do NOT work. You MUST create a 16-character 'App Password' at myaccount.google.com/apppasswords." else "🔴 Gmail: Normales Passwort funktioniert NICHT. Sie MÜSSEN auf myaccount.google.com/apppasswords ein 16-stelliges 'App-Passwort' erstellen.", fontSize = 11.sp, color = Color.White, lineHeight = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(if (isEn) "🟡 GMX / WEB.DE: Log in via browser -> Settings -> Enable 'Allow POP3/SMTP access'." else "🟡 GMX / WEB.DE: Loggen Sie sich im Browser ein -> Einstellungen -> 'POP3/SMTP-Übertragung erlauben' aktivieren.", fontSize = 11.sp, color = Color.White, lineHeight = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("🔵 Ports: Port 587 (STARTTLS) or Port 465 (SSL).", fontSize = 11.sp, color = Color.White, lineHeight = 16.sp)
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
                            text = if (isEn) "SMTP EMAIL SERVER SETTINGS" else "SMTP E-MAIL-SERVER EINSTELLUNGEN",
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
                        label = { Text(if (isEn) "Sender Email / Username" else "Absender-Email / Benutzername") },
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
                        label = { Text(if (isEn) "Password / App Password" else "Passwort / App-Passwort") },
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
                            Text(if (isEn) "VERIFYING CONNECTION..." else "VERBINDUNG WIRD GEPRÜFT...", fontSize = 12.sp, color = Color.White)
                        } else {
                            Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = Color(0xFF81D4FA))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isEn) "📧 TEST EMAIL CONNECTION NOW" else "📧 E-MAIL VERBINDUNG JETZT TESTEN", fontSize = 12.sp, color = Color(0xFF81D4FA), fontWeight = FontWeight.Bold)
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

                    val graceMins = selectedGraceHours.toLong() * 60

                    viewModel.updateConfig(
                        selectedIntervalHours.toLong() * 60, selectedDispatchMethod, 3, true,
                        selectedLanguage, enableBootRecovery, enableBatteryWarnings, enableCloudWatchdog, watchdogPingUrl, graceMins,
                        enableBiometricLock, appPin, panicPin, autoDeleteAfterDispatch, enableGpsLocation, lastKnownLocationUrl
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
                Text(text = if (isEn) "SAVE ALL SETTINGS" else "ALLE EINSTELLUNGEN SPEICHERN", fontWeight = FontWeight.Bold)
            }
        }
    }
}
