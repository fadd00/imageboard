package com.sample.image_board.utils

import com.sample.image_board.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseClient {

    val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            // 1. Module Auth (Login/Register) dengan Secure Storage
            install(Auth) {

                // Auto save session ke persistent storage (encrypted)
                autoSaveToStorage = true
            }

            // 2. Module Database (Postgrest)
            install(Postgrest)

            // 3. Module Storage (Upload Gambar)
            install(Storage)
        }
    }
}