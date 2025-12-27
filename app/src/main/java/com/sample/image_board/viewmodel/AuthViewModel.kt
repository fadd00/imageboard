package com.sample.image_board.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sample.image_board.data.model.Result
import com.sample.image_board.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 1. Definisikan State UI (Kondisi Layar)
sealed interface AuthState {
    data object Idle : AuthState // Diam (awal)
    data object Loading : AuthState // Loading (Spinner muter)
    data object Success : AuthState // Berhasil (Pindah ke Home)
    data class Error(val message: String) : AuthState // Gagal (Munculin Toast)
}

// State untuk forgot password
sealed interface ForgotPasswordState {
    data object Idle : ForgotPasswordState
    data object Loading : ForgotPasswordState
    data object Success : ForgotPasswordState
    data class Error(val message: String) : ForgotPasswordState
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository()

    // 2. StateFlow buat dipantau sama UI (Compose)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    // State untuk menampilkan dialog konfirmasi logout
    private val _showLogoutDialog = MutableStateFlow(false)
    val showLogoutDialog = _showLogoutDialog.asStateFlow()

    // State untuk forgot password
    private val _forgotPasswordState =
            MutableStateFlow<ForgotPasswordState>(ForgotPasswordState.Idle)
    val forgotPasswordState = _forgotPasswordState.asStateFlow()

    // Flag untuk prevent auto-check session setelah logout
    private var skipAutoCheck = false

    // Username saat ini
    private val _currentUsername = MutableStateFlow<String?>(null)
    val currentUsername = _currentUsername.asStateFlow()

    // Stay logged in preference
    private val _stayLoggedIn = MutableStateFlow(true)
    val stayLoggedIn = _stayLoggedIn.asStateFlow()

    // Settings dialog state
    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog = _showSettingsDialog.asStateFlow()

    // Cek status login saat aplikasi dibuka (Auto Login)
    init {
        // Load preference SEBELUM checkSession
        val pref = com.sample.image_board.utils.PreferenceManager.getStayLoggedIn(application)
        _stayLoggedIn.value = pref

        // Baru check session
        checkSession()
    }

    private fun checkSession() {
        // Skip auto-check jika user baru logout
        if (skipAutoCheck) {
            skipAutoCheck = false
            return
        }

        // Hanya auto-login jika stay logged in enabled
        if (_stayLoggedIn.value) {
            val session = repository.getCurrentSession()
            if (session != null) {
                _authState.value = AuthState.Success
                loadUsername() // Load username saat auto-login
            }
        } else {
            // Jika stay logged in OFF, clear session
            viewModelScope.launch { repository.signOut() }
        }
    }

    /** Load current user's username */
    fun loadUsername() {
        viewModelScope.launch {
            val username = repository.getCurrentUsername()
            if (username != null) {
                _currentUsername.value = username
            } else {
                val email = repository.getCurrentUserEmail()
                _currentUsername.value = email?.substringBefore("@") ?: "User"
            }
        }
    }

    /** Get current access token untuk keperluan API calls */
    fun getAccessToken(): String? {
        return repository.getAccessToken()
    }

    /** Refresh session token secara manual */
    fun refreshSession() {
        viewModelScope.launch {
            when (val result = repository.refreshSession()) {
                is Result.Success -> {
                    // Do nothing, session is refreshed
                }
                is Result.Error -> {
                    _authState.value = AuthState.Error("Sesi berakhir, silakan login kembali")
                }
            }
        }
    }

    fun signUp(email: String, pass: String) {
        // --- Client-side validation ---
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _authState.value = AuthState.Error("Format email tidak valid")
            return
        }
        if (pass.length < 6) {
            _authState.value = AuthState.Error("Password minimal 6 karakter")
            return
        }
        // --- End of validation ---

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val result = repository.signUp(email, pass)) {
                is Result.Success -> {
                    // Setelah signup berhasil, cek session
                    val session = repository.getCurrentSession()
                    if (session != null) {
                        // Email confirmation DISABLED: langsung ada session
                        _authState.value = AuthState.Success
                    } else {
                        // Fallback: mungkin email confirmation masih aktif
                        _authState.value =
                                AuthState.Error(
                                        "Registrasi berhasil! Silakan cek email untuk konfirmasi, lalu login."
                                )
                    }
                }
                is Result.Error -> {
                    val errorMsg =
                            when {
                                result.exception.message?.contains(
                                        "already registered",
                                        ignoreCase = true
                                ) == true ||
                                        result.exception.message?.contains(
                                                "User already registered",
                                                ignoreCase = true
                                        ) == true -> "Email sudah terdaftar. Silakan login."
                                // Client-side validation should prevent this, but keep as a
                                // fallback
                                result.exception.message?.contains("password", ignoreCase = true) ==
                                        true -> "Password minimal 6 karakter"
                                result.exception.message?.contains(
                                        "Invalid email",
                                        ignoreCase = true
                                ) == true -> "Format email tidak valid"
                                else -> result.exception.message ?: "Register Gagal"
                            }
                    _authState.value = AuthState.Error(errorMsg)
                }
            }
        }
    }

    fun signIn(email: String, pass: String) {
        // --- Client-side validation ---
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _authState.value = AuthState.Error("Format email tidak valid")
            return
        }
        if (pass.isEmpty()) {
            _authState.value = AuthState.Error("Password tidak boleh kosong")
            return
        }
        // --- End of validation ---

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val result = repository.signIn(email, pass)) {
                is Result.Success -> {
                    _authState.value = AuthState.Success
                    loadUsername() // Load username setelah login
                }
                is Result.Error -> {
                    val errorMsg =
                            when {
                                result.exception.message?.contains(
                                        "Invalid login credentials",
                                        ignoreCase = true
                                ) == true -> "Email atau password salah"
                                result.exception.message?.contains(
                                        "Email not confirmed",
                                        ignoreCase = true
                                ) == true -> "Email belum dikonfirmasi. Cek inbox email Anda."
                                // Client-side validation should prevent this, but keep as a
                                // fallback
                                result.exception.message?.contains(
                                        "Invalid email",
                                        ignoreCase = true
                                ) == true -> "Format email tidak valid"
                                else -> result.exception.message ?: "Login Gagal"
                            }
                    _authState.value = AuthState.Error(errorMsg)
                }
            }
        }
    }

    /** Request untuk membuka settings - akan menampilkan settings dialog */
    fun requestSettings() {
        _showSettingsDialog.value = true
    }

    /** Close settings dialog */
    fun closeSettings() {
        _showSettingsDialog.value = false
    }

    /** Toggle stay logged in preference */
    fun toggleStayLoggedIn() {
        _stayLoggedIn.value = !_stayLoggedIn.value
    }

    /** Save stay logged in preference to storage */
    fun saveStayLoggedInPreference() {
        viewModelScope.launch {
            com.sample.image_board.utils.PreferenceManager.setStayLoggedIn(
                    getApplication(),
                    _stayLoggedIn.value
            )
        }
    }

    /** Request untuk logout - akan menampilkan dialog konfirmasi */
    fun requestLogout() {
        // Close settings dialog first
        _showSettingsDialog.value = false
        // Show logout confirmation
        _showLogoutDialog.value = true
    }

    /** Cancel logout request */
    fun cancelLogout() {
        _showLogoutDialog.value = false
    }

    /** Confirm logout - akan menghapus session dan redirect ke login */
    fun confirmLogout() {
        _showLogoutDialog.value = false
        viewModelScope.launch {
            // Set flag untuk prevent auto-check session
            skipAutoCheck = true

            // Logout dari repository (clear session)
            when (val result = repository.signOut()) {
                is Result.Success -> {
                    // Set state ke Idle (akan redirect ke login screen)
                    _authState.value = AuthState.Idle
                }
                is Result.Error -> {
                    _authState.value = AuthState.Error("Logout gagal: ${result.exception.message}")
                }
            }
        }
    }

    // Reset state biar kalau balik ke login screen gak langsung nge-trigger success lagi
    fun resetState() {
        _authState.value = AuthState.Idle
    }

    /** Send password reset email */
    fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            _forgotPasswordState.value = ForgotPasswordState.Loading
            when (val result = repository.sendPasswordResetEmail(email)) {
                is Result.Success -> {
                    _forgotPasswordState.value = ForgotPasswordState.Success
                }
                is Result.Error -> {
                    val errorMsg =
                            when {
                                result.exception.message?.contains(
                                        "Invalid email",
                                        ignoreCase = true
                                ) == true -> "Format email tidak valid"
                                result.exception.message?.contains(
                                        "not found",
                                        ignoreCase = true
                                ) == true -> "Email tidak terdaftar"
                                else -> result.exception.message
                                                ?: "Gagal mengirim email reset password"
                            }
                    _forgotPasswordState.value = ForgotPasswordState.Error(errorMsg)
                }
            }
        }
    }

    /** Reset forgot password state */
    fun resetForgotPasswordState() {
        _forgotPasswordState.value = ForgotPasswordState.Idle
    }
}
