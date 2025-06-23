package com.uzaif.locationservice.repository

import android.util.Log
import com.uzaif.locationservice.data.LocationData
import com.uzaif.locationservice.data.SupabaseConfig
import com.uzaif.locationservice.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class LocationRepository {
    private val apiService = NetworkModule.supabaseApiService
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    companion object {
        private const val TAG = "LocationRepository"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L
    }
    
    suspend fun uploadLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float? = null,
        speed: Float? = null,
        bearing: Float? = null
    ): Result<LocationData> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        
        // Retry logic for better reliability
        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                val timestamp = dateFormat.format(Date())
                val locationData = LocationData(
                    childId = SupabaseConfig.CHILD_ID,
                    latitude = latitude,
                    longitude = longitude,
                    locationTimestamp = timestamp,
                    accuracy = accuracy,
                    speed = speed,
                    bearing = bearing
                )
                
                Log.d(TAG, "Attempting to upload location (attempt ${attempt + 1}): lat=$latitude, lng=$longitude")
                
                val response = apiService.insertLocation(
                    apiKey = SupabaseConfig.SUPABASE_ANON_KEY,
                    authorization = "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}",
                    locationData = locationData
                )
                
                if (response.isSuccessful) {
                    val insertedData = response.body()?.firstOrNull()
                    if (insertedData != null) {
                        Log.i(TAG, "Location uploaded successfully: $insertedData")
                        return@withContext Result.success(insertedData)
                    } else {
                        Log.w(TAG, "Upload successful but no data returned from server")
                        return@withContext Result.failure(Exception("No data returned from server"))
                    }
                } else {
                    val errorMsg = "Upload failed: ${response.code()} - ${response.message()}"
                    Log.e(TAG, "$errorMsg (attempt ${attempt + 1})")
                    lastException = Exception(errorMsg)
                    
                    // Don't retry on certain error codes
                    if (response.code() in listOf(400, 401, 403, 404)) {
                        Log.e(TAG, "Non-retryable error code: ${response.code()}")
                        return@withContext Result.failure(lastException!!)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during upload (attempt ${attempt + 1})", e)
                lastException = e
            }
            
            // Wait before retry (except on last attempt)
            if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                Log.d(TAG, "Retrying in ${RETRY_DELAY_MS}ms...")
                delay(RETRY_DELAY_MS)
            }
        }
        
        Log.e(TAG, "All upload attempts failed")
        Result.failure(lastException ?: Exception("Upload failed after $MAX_RETRY_ATTEMPTS attempts"))
    }
    
    suspend fun getLatestLocation(): Result<LocationData?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching latest location for child: ${SupabaseConfig.CHILD_ID}")
            
            val response = apiService.getLocations(
                apiKey = SupabaseConfig.SUPABASE_ANON_KEY,
                authorization = "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}",
                childId = SupabaseConfig.CHILD_ID,
                limit = 1
            )
            
            if (response.isSuccessful) {
                val latestLocation = response.body()?.firstOrNull()
                if (latestLocation != null) {
                    Log.d(TAG, "Latest location retrieved: lat=${latestLocation.latitude}, lng=${latestLocation.longitude}")
                } else {
                    Log.i(TAG, "No location data found for child: ${SupabaseConfig.CHILD_ID}")
                }
                Result.success(latestLocation)
            } else {
                val errorMsg = "Failed to fetch location: ${response.code()} - ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while fetching latest location", e)
            Result.failure(e)
        }
    }
    
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Testing database connection...")
            Log.d(TAG, "Connecting to: ${SupabaseConfig.SUPABASE_URL}")
            
            val response = apiService.testConnection(
                apiKey = SupabaseConfig.SUPABASE_ANON_KEY,
                authorization = "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}",
                limit = 1
            )
            
            val isConnected = response.isSuccessful
            if (isConnected) {
                Log.i(TAG, "✅ Database connection successful!")
            } else {
                Log.e(TAG, "❌ Database connection failed: ${response.code()} - ${response.message()}")
                val errorBody = response.errorBody()?.string()
                if (errorBody != null) {
                    Log.e(TAG, "Error details: $errorBody")
                }
            }
            Result.success(isConnected)
        } catch (e: java.net.UnknownHostException) {
            val errorMsg = "Unable to resolve hostname. Check your internet connection and Supabase URL."
            Log.e(TAG, "❌ DNS Error: $errorMsg", e)
            Result.failure(Exception(errorMsg))
        } catch (e: java.net.ConnectException) {
            val errorMsg = "Connection failed. Check your internet connection."
            Log.e(TAG, "❌ Connection Error: $errorMsg", e)
            Result.failure(Exception(errorMsg))
        } catch (e: java.net.SocketTimeoutException) {
            val errorMsg = "Connection timeout. Check your internet connection."
            Log.e(TAG, "❌ Timeout Error: $errorMsg", e)
            Result.failure(Exception(errorMsg))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Connection test failed", e)
            Result.failure(e)
        }
    }
} 