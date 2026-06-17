package com.cj.tapblok

import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.cj.tapblok.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private lateinit var db: AppDatabase
    private lateinit var prefs: android.content.SharedPreferences
    @Volatile private var blockedApps: Set<String> = emptySet()
    @Volatile private var hasBlockedWebsites = false
    private var isMonitoring = false

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "app_monitoring_channel"
        @Volatile var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AppMonitoringService", "Service has started.")

        prefs.edit {
            putInt("blocked_app_attempts", 0)
            putBoolean("monitoring_active", true)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HomeBlok is Active")
            .setContentText("App monitoring and blocking is running.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        LocationRuleManager.refreshCurrentHomeState(this, startWhenAway = false) { homeDistance ->
            if (homeDistance.insideHome == true && LocationRuleManager.shouldAllowBlockedAppsAtHome(this)) {
                Log.d("AppMonitoringService", "Already inside home radius; monitoring will stay active but allow blocked apps.")
                WebsiteBlockVpnService.stop(this)
            } else if (hasBlockedWebsites) {
                WebsiteBlockVpnService.startIfAllowed(this)
            }
        }

        if (isMonitoring) return START_STICKY
        isMonitoring = true

        serviceScope.launch {
            db.blockedAppDao().getAllBlockedApps().collect { list ->
                blockedApps = list.map { it.packageName }.toSet()
                HardModeManager.setBlockedPackagesSuspended(
                    this@AppMonitoringService,
                    blockedApps,
                    suspended = LocationRuleManager.shouldSuspendBlockedPackages(this@AppMonitoringService)
                )
                if (BuildConfig.DEBUG) Log.d("AppMonitoringService", "Blocked apps updated from DB: $blockedApps")
            }
        }

        serviceScope.launch {
            db.blockedWebsiteDao().getAllBlockedWebsites().collect { list ->
                hasBlockedWebsites = list.isNotEmpty()
                if (!hasBlockedWebsites || LocationRuleManager.shouldAllowBlockedAppsAtHome(this@AppMonitoringService)) {
                    WebsiteBlockVpnService.stop(this@AppMonitoringService)
                } else if (!WebsiteBlockVpnService.isRunning) {
                    WebsiteBlockVpnService.startIfAllowed(this@AppMonitoringService)
                }
            }
        }

        serviceScope.launch {
            val localContext = this@AppMonitoringService

            while (isActive) {
                if (!hasUsageStatsPermission(localContext) || !Settings.canDrawOverlays(localContext)) {
                    Log.e("AppMonitoringService", "Permissions revoked. Stopping service.")
                    stopSelf()
                    break
                }

                if (LocationRuleManager.shouldAllowBlockedAppsAtHome(localContext)) {
                    WebsiteBlockVpnService.stop(localContext)
                    if (BuildConfig.DEBUG) {
                        Log.d("AppMonitoringService", "Inside home radius; monitoring remains active without blocking.")
                    }
                    delay(1000)
                    continue
                }

                if (hasBlockedWebsites && !WebsiteBlockVpnService.isRunning) {
                    WebsiteBlockVpnService.startIfAllowed(localContext)
                }

                val foregroundApp = getForegroundApp()
                if (BuildConfig.DEBUG) Log.d("AppMonitoringService", "Current App: $foregroundApp")

                if (foregroundApp != null &&
                    LocationRuleManager.isTemporarilyAllowedAtHome(localContext, foregroundApp)
                ) {
                    if (BuildConfig.DEBUG) {
                        Log.d("AppMonitoringService", "Skipping block during home-confirmed allow window: $foregroundApp")
                    }
                    delay(1000)
                    continue
                }

                if (foregroundApp != null && foregroundApp in blockedApps && foregroundApp != packageName) {
                    val blockIntent = Intent(localContext, BlockingActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("BLOCKED_APP_PACKAGE_NAME", foregroundApp)
                    }
                    startActivity(blockIntent)
                    if (BuildConfig.DEBUG) Log.d("AppMonitoringService", "Blocked app detected: $foregroundApp")

                    val attempts = prefs.getInt("blocked_app_attempts", 0)
                    prefs.edit {
                        putInt("blocked_app_attempts", attempts + 1)
                    }
                }
                delay(1000)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        WebsiteBlockVpnService.stop(this)
        HardModeManager.setBlockedPackagesSuspended(this, blockedApps, suspended = false)
        serviceScope.cancel()
        Log.d("AppMonitoringService", "Service has been destroyed.")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        ensureMonitoringDefaultEnabled(this)
        Log.d("AppMonitoringService", "Task removed; requested monitoring recovery.")
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun getForegroundApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val appList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 10,
            time
        )
        return appList?.maxByOrNull { it.lastTimeUsed }?.packageName
    }
}
