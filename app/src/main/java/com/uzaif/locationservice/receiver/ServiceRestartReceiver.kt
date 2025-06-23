package com.uzaif.locationservice.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.uzaif.locationservice.service.LocationTrackingService

class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ServiceRestartReceiver", "Service restart triggered")
        
        // Only restart if the service isn't already running
        if (!LocationTrackingService.isRunning()) {
            LocationTrackingService.startService(context)
        }
    }
} 