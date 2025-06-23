package com.uzaif.locationservice.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.uzaif.locationservice.service.LocationTrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.d("BootReceiver", "Boot/restart event detected: ${intent.action}")
                
                // Use coroutine to handle delayed start (some devices need time)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Wait a bit for system to stabilize
                        delay(5000)
                        
                        Log.d("BootReceiver", "Starting location service after boot")
                        LocationTrackingService.startService(context)
                        
                        // Double-check after another delay
                        delay(10000)
                        if (!LocationTrackingService.isRunning()) {
                            Log.w("BootReceiver", "Service not running, attempting restart")
                            LocationTrackingService.startService(context)
                        }
                    } catch (e: Exception) {
                        Log.e("BootReceiver", "Error starting service after boot", e)
                    }
                }
            }
        }
    }
} 