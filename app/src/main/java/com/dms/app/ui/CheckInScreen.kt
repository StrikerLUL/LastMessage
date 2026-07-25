package com.dms.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dms.app.domain.models.TimerStatus

/**
 * CheckInScreen UI representation and Jetpack Compose layout presenter.
 * Enforces App PIN / Biometric Lock Screen on startup, Panic PIN duress triggers,
 * countdown timer state, status badges, and primary "I Am Alive" check-in button.
 */
class CheckInScreen(
    private val viewModel: CheckInViewModel
) {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content(onNavigateToSettings: () -> Unit) {
        val timerEval by viewModel.timerState.collectAsState()
        val userMessage by viewModel.userMessage.collectAsState()
        val config by viewModel.configState.collectAsState()
        val isAppLocked by viewModel.isAppLocked.collectAsState()

        val isEn = config.language == "EN"

        var startupPinInput by remember { mutableStateOf("") }
        var startupPinError by remember { mutableStateOf<String?>(null) }

        // APP LOCK OVERLAY SCREEN (FULL-SCREEN LOCK)
        if (isAppLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = Color(0xFFEA80FC).copy(alpha = 0.15f),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFEA80FC),
                                modifier = Modifier
                                    .padding(16.dp)
                                    .size(42.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isEn) "LastMessage Locked" else "LastMessage Gesperrt",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (isEn) "Enter your App PIN or Panic PIN to continue:" else "Geben Sie Ihren App-PIN oder Nötigungs-PIN ein:",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        OutlinedTextField(
                            value = startupPinInput,
                            onValueChange = { startupPinInput = it.trim() },
                            label = { Text(if (isEn) "Enter PIN" else "PIN eingeben") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        startupPinError?.let { err ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = err,
                                color = Color(0xFFFF8A80),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (startupPinInput.isBlank()) {
                                    startupPinError = if (isEn) "Please enter PIN!" else "Bitte PIN eingeben!"
                                } else {
                                    val success = viewModel.verifyStartupPin(startupPinInput)
                                    if (success) {
                                        startupPinInput = ""
                                        startupPinError = null
                                    } else {
                                        startupPinError = if (isEn) "❌ Invalid PIN!" else "❌ Falscher PIN!"
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
                        ) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isEn) "UNLOCK APP" else "APP ENTSPERREN",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
            return
        }

        // MAIN APP SCREEN CONTENT (WHEN UNLOCKED)
        val isGracePeriod = timerEval.status == TimerStatus.GRACE_PERIOD

        val displayMinutes = if (isGracePeriod) timerEval.remainingGraceMinutes else timerEval.remainingMinutes
        val hoursRemaining = displayMinutes / 60
        val minsRemaining = displayMinutes % 60
        val formattedCountdown = String.format("%02dh %02dm", hoursRemaining, minsRemaining)

        val statusColor = when (timerEval.status) {
            TimerStatus.ACTIVE -> Color(0xFF4CAF50)
            TimerStatus.WARNING -> Color(0xFFFF9800)
            TimerStatus.GRACE_PERIOD -> Color(0xFFFF5722)
            TimerStatus.EXPIRED -> Color(0xFFF44336)
        }

        val animatedColor by animateColorAsState(targetValue = statusColor, label = "statusColor")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF81D4FA),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LastMessage",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            }

            // Main Status & Countdown Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isGracePeriod) Color(0xFF3E2723) else Color(0xFF1E1E1E)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Status Badge
                    Surface(
                        color = animatedColor.copy(alpha = 0.15f),
                        shape = CircleShape,
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(animatedColor, animatedColor)))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (timerEval.status == TimerStatus.ACTIVE) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = animatedColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isGracePeriod) "🚨 GNADENFRIST / GRACE PERIOD" else timerEval.status.name,
                                color = animatedColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = if (isGracePeriod) "VERBLEIBENDE GNADENFRIST / GRACE TIME" else "VERBLEIBENDE ZEIT / REMAINING TIME",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isGracePeriod) Color(0xFFFFAB91) else Color.Gray,
                        letterSpacing = 1.2.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = formattedCountdown,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isGracePeriod)
                            "🚨 Notfall-Countdown abgelaufen! Jetzt einchecken, um Notfall-Versand abzubrechen!"
                        else
                            "Vollständig lokal & verschlüsselt auf dem Gerät\n100% Local & AES-256 Encrypted",
                        fontSize = 11.sp,
                        color = if (isGracePeriod) Color(0xFFFFCCBC) else Color(0xFFB0BEC5),
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }

            // Main Check-in Action Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                userMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = Color(0xFF81C784),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                Button(
                    onClick = { viewModel.performCheckIn("MANUAL_APP") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isGracePeriod) Color(0xFFD84315) else Color(0xFF2E7D32)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isGracePeriod) "⚡ NOTFALL-ABBRUCH & CHECK-IN" else "ICH BIN NOCH DA / I AM ALIVE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
