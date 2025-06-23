package com.uzaif.locationservice.network

import com.uzaif.locationservice.data.SupabaseConfig
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object NetworkModule {
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    // Custom DNS to handle private DNS issues
    private val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                // Try default DNS first
                Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                // If default fails, try manual resolution
                // This helps with private DNS issues
                try {
                    listOf(InetAddress.getByName(hostname))
                } catch (e2: Exception) {
                    // If all fails, throw the original exception
                    throw e
                }
            }
        }
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .dns(customDns)  // Use custom DNS resolver
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl("${SupabaseConfig.SUPABASE_URL}/rest/v1/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val supabaseApiService: SupabaseApiService by lazy {
        retrofit.create(SupabaseApiService::class.java)
    }
} 