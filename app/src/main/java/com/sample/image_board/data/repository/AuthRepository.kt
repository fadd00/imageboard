package com.sample.image_board.data.repository

import com.sample.image_board.data.model.Profile
import com.sample.image_board.data.model.Result
import com.sample.image_board.utils.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserSession
import io.github.jan.supabase.postgrest.from
import kotlin.random.Random
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class AuthRepository {

    // Ambil modul Auth dari SupabaseClient yang udah kita bikin
    private val authClient = SupabaseClient.client.auth
    private val supabase = SupabaseClient.client

    /**
     * Generate username otomatis dengan format anon-XXXXXXXX X = karakter acak (huruf kecil +
     * angka)
     */
    private fun generateAnonUsername(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val randomString = (1..8).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "anon-$randomString"
    }

    /** Cek apakah user sudah login sebelumnya (Auto Login) Return: UserSession atau null */
    fun getCurrentSession(): UserSession? {
        return authClient.currentSessionOrNull()
    }

    /** Get Access Token (untuk keperluan API calls) */
    fun getAccessToken(): String? {
        return authClient.currentAccessTokenOrNull()
    }

    /**
     * Refresh session token secara manual Supabase SDK otomatis refresh, tapi ini bisa dipanggil
     * manual jika perlu
     */
    suspend fun refreshSession(): Result<UserSession> {
        return try {
            authClient.refreshCurrentSession()
            val session = authClient.currentSessionOrNull()
            if (session != null) {
                Result.Success(session)
            } else {
                Result.Error(Exception("Failed to refresh session."))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Register User Baru Username OTOMATIS dibuat sistem dengan format anon-XXXXXXXX Kalo sukses,
     * otomatis bikin data di tabel 'profiles' (karena trigger SQL kita)
     */
    suspend fun signUp(emailInput: String, passwordInput: String): Result<Unit> {
        return try {
            // Generate username otomatis
            val autoUsername = generateAnonUsername()

            authClient.signUpWith(Email) {
                email = emailInput
                password = passwordInput
                // Metadata ini buat ngisi tabel profiles via Trigger SQL
                data = buildJsonObject {
                    put("username", JsonPrimitive(autoUsername))
                    put("full_name", JsonPrimitive(autoUsername)) // Full name = username
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** Login User Lama */
    suspend fun signIn(emailInput: String, passwordInput: String): Result<Unit> {
        return try {
            authClient.signInWith(Email) {
                email = emailInput
                password = passwordInput
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** Logout - Hapus session dari server & lokal */
    suspend fun signOut(): Result<Unit> {
        return try {
            authClient.signOut()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** Get current user ID */
    fun getCurrentUserId(): String? {
        return authClient.currentSessionOrNull()?.user?.id
    }

    /**
     * Get user role dari profiles table Return: "member", "admin", "moderator", atau null jika
     * belum login
     */
    suspend fun getUserRole(): Result<String?> {
        val userId = getCurrentUserId() ?: return Result.Success(null)

        return try {
            val profile =
                    supabase.from("profiles")
                            .select { filter { eq("id", userId) } }
                            .decodeSingleOrNull<Profile>()

            Result.Success(profile?.role)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /** Check apakah user adalah admin */
    suspend fun isAdmin(): Boolean {
        return when (val result = getUserRole()) {
            is Result.Success -> result.data == "admin"
            is Result.Error -> false
        }
    }

    /** Get current user's username from profiles table */
    suspend fun getCurrentUsername(): String? {
        val userId = getCurrentUserId() ?: return null

        return try {
            val profile =
                    supabase.from("profiles")
                            .select { filter { eq("id", userId) } }
                            .decodeSingleOrNull<Profile>()

            profile?.username
        } catch (e: Exception) {
            null
        }
    }

    /** Send password reset email User akan menerima email dengan link untuk reset password */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            authClient.resetPasswordForEmail(email)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
