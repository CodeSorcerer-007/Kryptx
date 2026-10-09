package com.kryptx.app.feature.auth

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryptx.app.MainActivity
import com.kryptx.app.core.designsystem.components.KryptxOutlinedButton
import com.kryptx.app.core.designsystem.components.KryptxPrimaryButton
import com.kryptx.app.core.designsystem.theme.KryptxAmber
import com.kryptx.app.core.designsystem.theme.KryptxRed
import com.kryptx.app.core.designsystem.theme.KryptxTheme
import com.kryptx.app.core.designsystem.theme.OledBackground
import com.kryptx.app.core.designsystem.theme.OledCard
import com.kryptx.app.core.designsystem.theme.OledCardBorder
import com.kryptx.app.core.designsystem.theme.OledTextPrimary
import com.kryptx.app.core.designsystem.theme.OledTextSecondary
import com.kryptx.app.core.security.SecurityBootstrapper

class SecurityCompromisedActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DETAILS = "extra_security_compromise_details"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hard stop hardware window screenshot & screen recording protection
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()

        // Block system back navigation completely — this is a non-dismissable gate
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Deliberate no-op: user must explicitly choose Exit or Proceed
            }
        })

        val details = intent.getStringArrayListExtra(EXTRA_DETAILS) ?: arrayListOf()

        setContent {
            KryptxTheme {
                SecurityCompromisedScreen(
                    details = details,
                    onExit = {
                        finishAffinity()
                        kotlin.system.exitProcess(0)
                    },
                    onProceedAnyway = {
                        SecurityBootstrapper.setCompromiseAcknowledged(this@SecurityCompromisedActivity, true)
                        val intent = Intent(this@SecurityCompromisedActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun SecurityCompromisedScreen(
    details: List<String>,
    onExit: () -> Unit,
    onProceedAnyway: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(OledBackground),
        color = OledBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Warning Shield Icon with glowing red ring
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(KryptxRed.copy(alpha = 0.12f))
                    .border(2.dp, KryptxRed.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Security Alert",
                    tint = KryptxRed,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Security Alert",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = OledTextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Untrusted Environment Detected",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = KryptxRed
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Kryptx detected root access, custom ROM test-keys, or active hooking frameworks. Running a zero-knowledge password vault in an untrusted environment compromises memory safety and hardware keystore guarantees.",
                fontSize = 13.sp,
                color = OledTextSecondary,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Detected indicators list card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OledCard)
                    .border(1.dp, OledCardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "DETECTED THREAT INDICATORS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = KryptxAmber,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (details.isEmpty()) {
                        Text(
                            text = "Root binaries or hooking frameworks present in system.",
                            fontSize = 13.sp,
                            color = OledTextSecondary
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(details) { detail ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 5.dp)
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(KryptxRed)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = detail,
                                        fontSize = 12.sp,
                                        color = OledTextPrimary,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            KryptxPrimaryButton(
                text = "Exit App (Recommended)",
                onClick = onExit,
                containerColor = KryptxRed,
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            KryptxOutlinedButton(
                text = "I understand the risks, proceed anyway",
                onClick = onProceedAnyway,
                borderColor = KryptxAmber.copy(alpha = 0.5f),
                textColor = KryptxAmber,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
