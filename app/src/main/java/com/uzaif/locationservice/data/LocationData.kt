package com.uzaif.locationservice.data

import com.google.gson.annotations.SerializedName

data class LocationData(
    @SerializedName("id")
    val id: String? = null,
    
    @SerializedName("child_id")
    val childId: String,
    
    @SerializedName("latitude")
    val latitude: Double,
    
    @SerializedName("longitude")
    val longitude: Double,
    
    @SerializedName("location_timestamp")
    val locationTimestamp: String,
    
    @SerializedName("accuracy")
    val accuracy: Float? = null,
    
    @SerializedName("speed")
    val speed: Float? = null,
    
    @SerializedName("bearing")
    val bearing: Float? = null,
    
    @SerializedName("created_at")
    val createdAt: String? = null
)

data class SupabaseResponse<T>(
    @SerializedName("data")
    val data: T? = null,
    
    @SerializedName("error")
    val error: SupabaseError? = null
)

data class SupabaseError(
    @SerializedName("message")
    val message: String,
    
    @SerializedName("code")
    val code: String? = null
) 