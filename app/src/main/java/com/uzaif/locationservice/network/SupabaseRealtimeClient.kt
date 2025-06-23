package com.uzaif.locationservice.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.uzaif.locationservice.data.LocationData
import com.uzaif.locationservice.data.SupabaseConfig
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

class SupabaseRealtimeClient {
    private var webSocketClient: WebSocketClient? = null
    private val isConnected = AtomicBoolean(false)
    private val gson = Gson()
    
    companion object {
        private const val TAG = "SupabaseRealtime"
        private const val REALTIME_URL = "wss://agopuyuxyhgzjhvgseyx.supabase.co/realtime/v1/websocket"
    }
    
    interface LocationUpdateListener {
        fun onLocationUpdate(location: LocationData)
        fun onConnectionStatusChanged(connected: Boolean)
    }
    
    private var locationUpdateListener: LocationUpdateListener? = null
    
    fun setLocationUpdateListener(listener: LocationUpdateListener) {
        this.locationUpdateListener = listener
    }
    
    fun connect() {
        try {
            val uri = URI.create("$REALTIME_URL?apikey=${SupabaseConfig.SUPABASE_ANON_KEY}&vsn=1.0.0")
            
            webSocketClient = object : WebSocketClient(uri) {
                override fun onOpen(handshake: ServerHandshake?) {
                    Log.i(TAG, "WebSocket connected")
                    isConnected.set(true)
                    locationUpdateListener?.onConnectionStatusChanged(true)
                    
                    // Subscribe to location table changes
                    subscribeToLocationUpdates()
                }
                
                override fun onMessage(message: String?) {
                    Log.d(TAG, "Received message: $message")
                    handleMessage(message)
                }
                
                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    Log.w(TAG, "WebSocket closed: $code - $reason")
                    isConnected.set(false)
                    locationUpdateListener?.onConnectionStatusChanged(false)
                    
                    // Auto-reconnect after 5 seconds
                    if (remote) {
                        Log.i(TAG, "Attempting to reconnect in 5 seconds...")
                        Thread {
                            Thread.sleep(5000)
                            connect()
                        }.start()
                    }
                }
                
                override fun onError(ex: Exception?) {
                    Log.e(TAG, "WebSocket error", ex)
                    isConnected.set(false)
                    locationUpdateListener?.onConnectionStatusChanged(false)
                }
            }
            
            webSocketClient?.connect()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to WebSocket", e)
            isConnected.set(false)
            locationUpdateListener?.onConnectionStatusChanged(false)
        }
    }
    
    private fun subscribeToLocationUpdates() {
        val subscribeMessage = JsonObject().apply {
            addProperty("topic", "realtime:public:locations")
            addProperty("event", "phx_join")
            addProperty("payload", JsonObject().toString())
            addProperty("ref", "1")
        }
        
        webSocketClient?.send(subscribeMessage.toString())
        Log.d(TAG, "Subscribed to location updates")
    }
    
    private fun handleMessage(message: String?) {
        try {
            if (message == null) return
            
            val jsonMessage = gson.fromJson(message, JsonObject::class.java)
            val event = jsonMessage.get("event")?.asString
            val payload = jsonMessage.get("payload")?.asJsonObject
            
            when (event) {
                "postgres_changes" -> {
                    val eventType = payload?.get("eventType")?.asString
                    val record = payload?.get("new")?.asJsonObject
                    
                    if (eventType == "INSERT" && record != null) {
                        try {
                            val locationData = gson.fromJson(record, LocationData::class.java)
                            Log.i(TAG, "New location received: ${locationData.latitude}, ${locationData.longitude}")
                            locationUpdateListener?.onLocationUpdate(locationData)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse location data", e)
                        }
                    }
                }
                "phx_reply" -> {
                    val status = payload?.get("status")?.asString
                    if (status == "ok") {
                        Log.i(TAG, "Successfully subscribed to location updates")
                    } else {
                        Log.w(TAG, "Subscription failed: $payload")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message", e)
        }
    }
    
    fun disconnect() {
        webSocketClient?.close()
        isConnected.set(false)
        locationUpdateListener?.onConnectionStatusChanged(false)
        Log.i(TAG, "WebSocket disconnected")
    }
    
    fun isConnected(): Boolean = isConnected.get()
    
    // Send heartbeat to keep connection alive
    fun sendHeartbeat() {
        if (isConnected.get()) {
            val heartbeat = JsonObject().apply {
                addProperty("topic", "phoenix")
                addProperty("event", "heartbeat")
                addProperty("payload", JsonObject().toString())
                addProperty("ref", System.currentTimeMillis().toString())
            }
            webSocketClient?.send(heartbeat.toString())
        }
    }
} 