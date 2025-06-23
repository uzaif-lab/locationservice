package com.uzaif.locationservice.network

import com.uzaif.locationservice.data.LocationData
import com.uzaif.locationservice.data.SupabaseResponse
import retrofit2.Response
import retrofit2.http.*

interface SupabaseApiService {
    @POST("locations")
    @Headers(
        "Content-Type: application/json",
        "Prefer: return=representation"
    )
    suspend fun insertLocation(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body locationData: LocationData
    ): Response<List<LocationData>>
    
    @GET("locations")
    suspend fun getLocations(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("child_id") childId: String,
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 1
    ): Response<List<LocationData>>
    
    // Test connection endpoint
    @GET("locations")
    suspend fun testConnection(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int = 1
    ): Response<List<LocationData>>
} 