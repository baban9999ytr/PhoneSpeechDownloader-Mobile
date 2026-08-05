package com.example.callhistory

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.runBlocking

object SupabaseManager {

    private val client = createSupabaseClient(
        supabaseUrl = "https://waultfsxvpomjcdwuoem.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6IndhdWx0ZnN4dnBvbWpjZHd1b2VtIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4MzMwODQ1MiwiZXhwIjoyMDk4ODg0NDUyfQ.3AwnBCcljZ1BkKgJn2Mq520-bhW5lbgYahAK7DIWzus"
    ) {
        httpEngine = Android.create()
        install(Storage)
    }

    @JvmStatic
    fun getClient(): SupabaseClient {
        return client   
    }

    @JvmStatic
    fun uploadFile(bucket: String, path: String, data: ByteArray): String = runBlocking {
        val storageBucket = client.storage.from(bucket)

        storageBucket.upload(path, data, upsert = true)

        return@runBlocking storageBucket.publicUrl(path)
    }
}