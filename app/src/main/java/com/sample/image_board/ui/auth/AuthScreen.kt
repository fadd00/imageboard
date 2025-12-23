package com.sample.image_board.ui.auth

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sample.image_board.viewmodel.AuthState
import com.sample.image_board.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    // Callback: Kalau login sukses, navigasi ke Home
    onLoginSuccess: () -> Unit,

    // Callback: Navigasi ke forgot password screen
    onForgotPasswordClick: () -> Unit = {},

    // Kita inject ViewModel di sini biar rapi
    viewModel: AuthViewModel = viewModel()
) {
    // Pantau State dari ViewModel (Idle, Loading, Success, Error)
    val state by viewModel.authState.collectAsState()

    val context = LocalContext.current

    // State lokal buat nyimpen text input user
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }

    // Toggle: True = Mode Login, False = Mode Daftar
    var isLoginMode by remember { mutableStateOf(true) }

    // Flag untuk handle auto-check vs manual login
    var isManualLogin by remember { mutableStateOf(false) }

    // Helper function untuk validasi
    fun isValidEmail(email: String): Boolean {
        return email.contains("@") && email.contains(".")
    }

    fun isValidPassword(password: String): Boolean {
        return password.length >= 6
    }

    // Efek Samping (Side Effect) buat nangkep hasil state
    LaunchedEffect(state) {
        when (val currentState = state) {
            is AuthState.Success -> {
                // Hanya navigate jika ini manual login (bukan auto-check)
                if (isManualLogin) {
                    Toast.makeText(context, "Berhasil Masuk!", Toast.LENGTH_SHORT).show()
                    onLoginSuccess() // Pindah ke Home
                    isManualLogin = false // Reset flag
                }
            }
            is AuthState.Error -> {
                Toast.makeText(context, currentState.message, Toast.LENGTH_LONG).show()
                viewModel.resetState() // Reset biar error gak muncul terus
                isManualLogin = false // Reset flag
            }
            else -> {} // Idle & Loading biarin aja
        }
    }

    // TAMPILAN UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isLoginMode) "Login ImageBoard" else "Daftar Akun Baru",
            style = MaterialTheme.typography.headlineMedium
        )

        // Info auto-generate username (hanya muncul di mode register)
        if (!isLoginMode) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Username akan dibuat otomatis dengan format anon-XXXXXXXX",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Input Email
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                emailError = false // Reset error saat user mengetik
            },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = emailError,
            supportingText = {
                if (emailError) {
                    Text("Email tidak valid", color = MaterialTheme.colorScheme.error)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Input Password
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                passwordError = false // Reset error saat user mengetik
            },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(), // Biar jadi bintang2
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = passwordError,
            supportingText = {
                if (passwordError) {
                    Text("Password minimal 6 karakter", color = MaterialTheme.colorScheme.error)
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Tombol Utama
        Button(
            onClick = {
                // Validasi input sebelum submit
                emailError = !isValidEmail(email)
                passwordError = !isValidPassword(password)

                // Kalau tidak ada error, proses login/signup
                if (!emailError && !passwordError) {
                    // Set flag bahwa ini manual login (bukan auto-check)
                    isManualLogin = true

                    if (isLoginMode) {
                        viewModel.signIn(email, password)
                    } else {
                        viewModel.signUp(email, password)
                    }
                } else {
                    // Tampilkan pesan error singkat
                    val errorMsg = when {
                        emailError && passwordError -> "Email dan password tidak valid"
                        emailError -> "Email tidak valid"
                        else -> "Password minimal 6 karakter"
                    }
                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is AuthState.Loading // Disable kalau lagi loading
        ) {
            if (state is AuthState.Loading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(if (isLoginMode) "Masuk" else "Daftar Sekarang")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tombol Lupa Password (hanya muncul di mode login)
        if (isLoginMode) {
            TextButton(onClick = onForgotPasswordClick) {
                Text("Lupa Password?")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Tombol Ganti Mode
        TextButton(onClick = { isLoginMode = !isLoginMode }) {
            Text(
                if (isLoginMode) "Belum punya akun? Daftar di sini"
                else "Sudah punya akun? Login"
            )
        }
    }
}
