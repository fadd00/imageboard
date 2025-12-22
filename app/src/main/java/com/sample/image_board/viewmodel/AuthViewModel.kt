package com.sample.image_board.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sample.image_board.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 1. Definisikan State UI (Kondisi Layar)
sealed interface AuthState {
    data object Idle : AuthState         // Diam (awal)
    data object Loading : AuthState      // Loading (Spinner muter)
    data object Success : AuthState      // Berhasil (Pindah ke Home)
    data class Error(val message: String) : AuthState // Gagal (Munculin Toast)
}

class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    // 2. StateFlow buat dipantau sama UI (Compose)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    // State untuk menampilkan dialog konfirmasi logout
    private val _showLogoutDialog = MutableStateFlow(false)
    val showLogoutDialog = _showLogoutDialog.asStateFlow()

    // Flag untuk prevent auto-check session setelah logout
    private var skipAutoCheck = false

    // Cek status login saat aplikasi dibuka (Auto Login)
    init {
        checkSession()
    }

    private fun checkSession() {
        // Skip auto-check jika user baru logout
        if (skipAutoCheck) {
            skipAutoCheck = false
            return
        }

        val session = repository.getCurrentSession()
        if (session != null) {
            _authState.value = AuthState.Success
        }
    }

    /**
     * Get current access token untuk keperluan API calls
     */
    fun getAccessToken(): String? {
        return repository.getAccessToken()
    }

    /**
     * Refresh session token secara manual
     */
    fun refreshSession() {
        viewModelScope.launch {
            try {
                val session = repository.refreshSession()
                if (session == null) {
                    _authState.value = AuthState.Error("Sesi berakhir, silakan login kembali")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Gagal refresh session: ${e.message}")
            }
        }
    }

    fun signUp(email: String, pass: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                repository.signUp(email, pass)
                // Setelah signup berhasil, cek session
                val session = repository.getCurrentSession()
                if (session != null) {
                    // Email confirmation DISABLED: langsung ada session
                    _authState.value = AuthState.Success
                } else {
                    // Fallback: mungkin email confirmation masih aktif
                    _authState.value = AuthState.Error(
                        "Registrasi berhasil! Silakan cek email untuk konfirmasi, lalu login."
                    )
                }
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("already registered", ignoreCase = true) == true ||
                    e.message?.contains("User already registered", ignoreCase = true) == true ->
                        "Email sudah terdaftar. Silakan login."
                    e.message?.contains("password", ignoreCase = true) == true ->
                        "Password minimal 6 karakter"
                    e.message?.contains("Invalid email", ignoreCase = true) == true ->
                        "Format email tidak valid"
                    else -> e.message ?: "Register Gagal"
                }
                _authState.value = AuthState.Error(errorMsg)
            }
        }
    }

    fun signIn(email: String, pass: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                repository.signIn(email, pass)
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("Invalid login credentials", ignoreCase = true) == true ->
                        "Email atau password salah"
                    e.message?.contains("Email not confirmed", ignoreCase = true) == true ->
                        "Email belum dikonfirmasi. Cek inbox email Anda."
                    e.message?.contains("Invalid email", ignoreCase = true) == true ->
                        "Format email tidak valid"
                    else -> e.message ?: "Login Gagal"
                }
                _authState.value = AuthState.Error(errorMsg)
            }
        }
    }

    /**
     * Request untuk logout - akan menampilkan dialog konfirmasi
     */
    fun requestLogout() {
        _showLogoutDialog.value = true
    }

    /**
     * Cancel logout request
     */
    fun cancelLogout() {
        _showLogoutDialog.value = false
    }

    /**
     * Confirm logout - akan menghapus session dan redirect ke login
     */
    fun confirmLogout() {
        _showLogoutDialog.value = false
        viewModelScope.launch {
            try {
                // Set flag untuk prevent auto-check session
                skipAutoCheck = true

                // Logout dari repository (clear session)
                repository.signOut()

                // Set state ke Idle (akan redirect ke login screen)
                _authState.value = AuthState.Idle
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Logout gagal: ${e.message}")
            }
        }
    }

    // Reset state biar kalau balik ke login screen gak langsung nge-trigger success lagi
    fun resetState() {
        _authState.value = AuthState.Idle
    }
}
