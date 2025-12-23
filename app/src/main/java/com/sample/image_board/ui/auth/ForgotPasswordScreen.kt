package com.sample.image_board.ui.auth

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sample.image_board.viewmodel.AuthViewModel
import com.sample.image_board.viewmodel.ForgotPasswordState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val state by viewModel.forgotPasswordState.collectAsState()
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf(false) }

    // Helper function untuk validasi email
    fun isValidEmail(email: String): Boolean {
        return email.contains("@") && email.contains(".")
    }

    // Handle state changes
    LaunchedEffect(state) {
        when (val currentState = state) {
            is ForgotPasswordState.Success -> {
                Toast.makeText(
                    context,
                    "Email reset password telah dikirim! Silakan cek inbox Anda.",
                    Toast.LENGTH_LONG
                ).show()
                viewModel.resetForgotPasswordState()
                onNavigateBack()
            }
            is ForgotPasswordState.Error -> {
                Toast.makeText(context, currentState.message, Toast.LENGTH_LONG).show()
                viewModel.resetForgotPasswordState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lupa Password") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon atau ilustrasi (opsional)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Reset Password",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Masukkan email Anda untuk menerima link reset password",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Input Email
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    emailError = false
                },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = emailError,
                supportingText = {
                    if (emailError) {
                        Text("Email tidak valid", color = MaterialTheme.colorScheme.error)
                    }
                },
                enabled = state !is ForgotPasswordState.Loading
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Tombol Kirim
            Button(
                onClick = {
                    emailError = !isValidEmail(email)

                    if (!emailError) {
                        viewModel.sendPasswordResetEmail(email.trim())
                    } else {
                        Toast.makeText(context, "Email tidak valid", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state !is ForgotPasswordState.Loading
            ) {
                if (state is ForgotPasswordState.Loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text("Kirim Email Reset")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Info tambahan
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "ℹ️ Informasi",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Link reset password akan dikirim ke email Anda\n" +
                                "• Link berlaku selama 1 jam\n" +
                                "• Cek folder spam jika tidak menerima email",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

