package com.uzaif.locationservice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.uzaif.locationservice.repository.LocationRepository
import com.uzaif.locationservice.service.LocationTrackingService
import com.uzaif.locationservice.ui.theme.LocationserviceTheme
import com.uzaif.locationservice.data.SupabaseConfig
import com.uzaif.locationservice.debug.ConnectionDebugger
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var isServiceRunning by mutableStateOf(false)
    private var hasLocationPermissions by mutableStateOf(false)
    private var hasBackgroundLocationPermission by mutableStateOf(false)
    private var connectionStatus by mutableStateOf<String?>(null)
    private var isTestingConnection by mutableStateOf(false)
    private var isBatteryOptimized by mutableStateOf(true)
    private var realtimeStatus by mutableStateOf<String?>(null)
    
    private val locationRepository = LocationRepository()
    
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermissions = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (hasLocationPermissions) {
            checkBackgroundLocationPermission()
        }
    }
    
    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasBackgroundLocationPermission = isGranted
        if (isGranted) {
            checkBatteryOptimization()
        }
    }
    
    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkBatteryOptimization()
    }
    
    // Modern back button handling for stealth mode
    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            // Move app to background instead of closing (stealth feature)
            moveTaskToBack(true)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Register the back button callback
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
        
        checkPermissions()
        checkBatteryOptimization()
        checkServiceStatus()
        
        setContent {
            LocationserviceTheme {
                LocationTrackingApp()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        checkServiceStatus()
        checkBatteryOptimization()
    }
    
    private fun checkPermissions() {
        hasLocationPermissions = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasLocationPermissions) {
            checkBackgroundLocationPermission()
        }
    }
    
    private fun checkBackgroundLocationPermission() {
        hasBackgroundLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Background location permission not needed for Android < 10
        }
    }
    
    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            isBatteryOptimized = !powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            isBatteryOptimized = false
        }
    }
    
    private fun checkServiceStatus() {
        isServiceRunning = LocationTrackingService.isRunning()
    }
    
    private fun requestLocationPermissions() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    
    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            checkBatteryOptimization()
        }
    }
    
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                batteryOptimizationLauncher.launch(intent)
            } catch (e: Exception) {
                // Fallback to settings page
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                batteryOptimizationLauncher.launch(intent)
            }
        }
    }
    
    private fun startLocationService() {
        LocationTrackingService.startService(this)
        isServiceRunning = true
    }
    
    private fun stopLocationService() {
        LocationTrackingService.stopService(this)
        isServiceRunning = false
    }
    
    private fun testSupabaseConnection() {
        isTestingConnection = true
        connectionStatus = "🔄 Testing connection..."
        
        lifecycleScope.launch {
            locationRepository.testConnection().fold(
                onSuccess = { isConnected ->
                    connectionStatus = if (isConnected) {
                        "✅ Database connected successfully!\n🌐 Real-time updates enabled"
                    } else {
                        "❌ Connection failed - Invalid response from server"
                    }
                },
                onFailure = { exception ->
                    val errorMessage = when {
                        exception.message?.contains("Unable to resolve hostname") == true -> 
                            "❌ DNS Error: Cannot resolve Supabase hostname\n📡 Check your internet connection\n🔧 Verify Supabase URL in config"
                        exception.message?.contains("Connection failed") == true -> 
                            "❌ Network Error: Cannot connect to server\n📡 Check your internet connection\n🔒 Check if WiFi/mobile data is working"
                        exception.message?.contains("timeout") == true -> 
                            "❌ Timeout Error: Server not responding\n⏱️ Connection is too slow\n📡 Try again with better internet"
                        else -> 
                            "❌ Connection Error: ${exception.message ?: "Unknown error"}\n🔧 Check logs for details"
                    }
                    connectionStatus = errorMessage
                }
            )
            isTestingConnection = false
        }
    }
    
    private fun runDetailedDiagnostic() {
        isTestingConnection = true
        connectionStatus = "🔄 Running detailed diagnostic..."
        
        lifecycleScope.launch {
            try {
                val diagnosticResult = ConnectionDebugger.performFullDiagnostic(this@MainActivity)
                
                // Add specific private DNS guidance
                val finalResult = if (diagnosticResult.contains("no address associated with hostname")) {
                    diagnosticResult + "\n\n" +
                    "🔧 PRIVATE DNS DETECTED!\n" +
                    "This is likely a Private DNS issue.\n\n" +
                    "QUICK FIX:\n" +
                    "1. Go to Settings > Network > Private DNS\n" +
                    "2. Select 'Off' or 'Automatic'\n" +
                    "3. Test connection again\n\n" +
                    "OR try mobile data instead of WiFi"
                } else {
                    diagnosticResult
                }
                
                connectionStatus = finalResult
            } catch (e: Exception) {
                connectionStatus = "❌ Diagnostic failed: ${e.message}"
            }
            isTestingConnection = false
        }
    }
    
    @Composable
    fun LocationTrackingApp() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Child Location Tracker",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Connection Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        connectionStatus?.startsWith("✅") == true -> MaterialTheme.colorScheme.primaryContainer
                        connectionStatus?.startsWith("❌") == true -> MaterialTheme.colorScheme.errorContainer
                        connectionStatus?.startsWith("🔄") == true -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Database Connection",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { testSupabaseConnection() },
                                enabled = !isTestingConnection,
                                modifier = Modifier.size(width = 80.dp, height = 36.dp)
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Test", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            
                            Button(
                                onClick = { runDetailedDiagnostic() },
                                enabled = !isTestingConnection,
                                modifier = Modifier.size(width = 100.dp, height = 36.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("Diagnose", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    
                    connectionStatus?.let { status ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                status.startsWith("✅") -> MaterialTheme.colorScheme.onPrimaryContainer
                                status.startsWith("❌") -> MaterialTheme.colorScheme.onErrorContainer
                                status.startsWith("🔄") -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    
                    // Show Supabase URL for debugging
                    if (connectionStatus?.startsWith("❌") == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "🔗 Target: ${SupabaseConfig.SUPABASE_URL}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            when {
                !hasLocationPermissions -> {
                    LocationPermissionContent()
                }
                !hasBackgroundLocationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    BackgroundLocationPermissionContent()
                }
                isBatteryOptimized -> {
                    BatteryOptimizationContent()
                }
                else -> {
                    LocationServiceContent()
                }
            }
        }
    }
    
    @Composable
    fun LocationPermissionContent() {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Location permissions are required for tracking",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "This app needs location access to track your child's location for safety purposes.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { requestLocationPermissions() }
            ) {
                Text("Grant Location Permissions")
            }
        }
    }
    
    @Composable
    fun BackgroundLocationPermissionContent() {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Background location access needed",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "To track location when the app is closed or screen is off, please allow 'Allow all the time' when prompted.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { requestBackgroundLocationPermission() }
            ) {
                Text("Grant Background Location")
            }
        }
    }
    
    @Composable
    fun BatteryOptimizationContent() {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Battery optimization exemption needed",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "To ensure continuous location tracking, please exempt this app from battery optimization. This prevents the system from stopping the service.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { requestBatteryOptimizationExemption() }
            ) {
                Text("Disable Battery Optimization")
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = { 
                    isBatteryOptimized = false
                    if (hasLocationPermissions && hasBackgroundLocationPermission) {
                        startLocationService()
                    }
                }
            ) {
                Text("Skip (Not Recommended)")
            }
        }
    }
    
    @Composable
    fun LocationServiceContent() {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isServiceRunning) 
                    "Location tracking is active" 
                else 
                    "Location tracking is stopped",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isServiceRunning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "✓ Stealth Mode Active",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Running invisibly in background • Location sent every 30-60 seconds • Survives app removal from recent apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (isServiceRunning) {
                Button(
                    onClick = { stopLocationService() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Stop Tracking")
                }
            } else {
                Button(onClick = { startLocationService() }) {
                    Text("Start Stealth Tracking")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "⚠️ Once started, the service will run continuously and restart automatically if killed. Only force-stop or uninstall can stop it.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}