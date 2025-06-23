package com.uzaif.locationservice.debug

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.uzaif.locationservice.data.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.URL

object ConnectionDebugger {
    private const val TAG = "ConnectionDebugger"
    
    suspend fun performFullDiagnostic(context: Context): String = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        
        // 1. Check internet connectivity
        results.add("🌐 INTERNET CONNECTIVITY:")
        val isConnected = isInternetAvailable(context)
        results.add(if (isConnected) "✅ Internet is available" else "❌ No internet connection")
        
        // 2. Check DNS resolution
        results.add("\n🔍 DNS RESOLUTION:")
        val dnsResult = testDnsResolution()
        results.add(dnsResult)
        
        // 3. Check Supabase URL format
        results.add("\n🔗 URL VALIDATION:")
        val urlResult = validateSupabaseUrl()
        results.add(urlResult)
        
        // 4. Check if Supabase host is reachable
        results.add("\n📡 HOST REACHABILITY:")
        val hostResult = testHostReachability()
        results.add(hostResult)
        
        results.joinToString("\n")
    }
    
    private fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
    
    private suspend fun testDnsResolution(): String = withContext(Dispatchers.IO) {
        try {
            val url = URL(SupabaseConfig.SUPABASE_URL)
            val host = url.host
            
            // Try multiple DNS resolution methods
            val results = mutableListOf<String>()
            
            // Method 1: Default DNS
            try {
                val address = InetAddress.getByName(host)
                results.add("✅ Default DNS: $host -> ${address.hostAddress}")
            } catch (e: Exception) {
                results.add("❌ Default DNS failed: ${e.message}")
                
                // Method 2: Try with Google DNS (8.8.8.8)
                try {
                    // Force using Google DNS by trying to resolve a known domain first
                    InetAddress.getByName("google.com")
                    val address = InetAddress.getByName(host)
                    results.add("✅ Fallback DNS: $host -> ${address.hostAddress}")
                } catch (e2: Exception) {
                    results.add("❌ Fallback DNS failed: ${e2.message}")
                    
                    // Check if it's a private DNS issue
                    if (e.message?.contains("no address associated with hostname") == true) {
                        results.add("⚠️ This looks like a Private DNS issue!")
                        results.add("💡 Try: Settings > Network > Private DNS > Off")
                        results.add("💡 Or use 'Automatic' instead of custom DNS")
                    }
                }
            }
            
            results.joinToString("\n")
        } catch (e: Exception) {
            "❌ DNS resolution test failed: ${e.message}"
        }
    }
    
    private fun validateSupabaseUrl(): String {
        return try {
            val url = URL(SupabaseConfig.SUPABASE_URL)
            when {
                !url.protocol.equals("https", ignoreCase = true) -> 
                    "❌ URL should use HTTPS protocol"
                !url.host.contains("supabase.co") -> 
                    "❌ Invalid Supabase hostname"
                url.host.length < 10 -> 
                    "❌ Hostname too short, check project ID"
                else -> 
                    "✅ URL format is valid: ${url.host}"
            }
        } catch (e: Exception) {
            "❌ Invalid URL format: ${e.message}"
        }
    }
    
    private suspend fun testHostReachability(): String = withContext(Dispatchers.IO) {
        try {
            val url = URL(SupabaseConfig.SUPABASE_URL)
            val host = url.host
            val address = InetAddress.getByName(host)
            
            // Try to reach the host
            val reachable = address.isReachable(5000)
            if (reachable) {
                "✅ Host is reachable: $host"
            } else {
                "⚠️ Host not reachable (may be firewall/network issue): $host"
            }
        } catch (e: Exception) {
            "❌ Host reachability test failed: ${e.message}"
        }
    }
} 