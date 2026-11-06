package com.albert.familycontrol.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.albert.familycontrol.R
import com.albert.familycontrol.firebase.FirebaseManager
import com.albert.familycontrol.firebase.FirebaseManager.AppSchedule
import com.albert.familycontrol.firebase.FirebaseManager.BedtimeConfig
import com.albert.familycontrol.receivers.GuardianAdminReceiver
import com.albert.familycontrol.ui.BlockedAppActivity
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.Calendar

class ScreenMonitorService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    // Current state
    private var currentApp = ""
    private var appForegroundSince = 0L

    // Config
    private var blockedApps = mutableSetOf<String>()
    private var blockedGameApps = mutableSetOf<String>()
    private var appLimits = mutableMapOf<String, Int>()          // pkg → limitMinutes
    private var appSchedules = mutableMapOf<String, AppSchedule>()
    private var appExtraMinutes = mutableMapOf<String, Int>()    // pkg → extra granted
    private var bedtime = BedtimeConfig()

    // Usage cache (from Firebase, minutes)
    private var usedTodayMinutes = mutableMapOf<String, Long>()
    private var todayKey = ""

    // Keywords
    private val adultWords = FirebaseManager.adultKeywords
    private val matWords = FirebaseManager.matKeywords

    private val knownGamePackages = mutableSetOf(
        "com.yandex.browser", "com.onay.app", "com.gameloft.android",
        "com.king.candycrushsaga", "com.supercell.clashofclans",
        "com.mojang.minecraftpe", "com.roblox.client",
        "com.activision.callofduty.shooter"
    )

    // Countdown notification
    private val NOTIF_CHANNEL = "guardian_timer"
    private val NOTIF_ID = 9001
    private var countdownJob: Job? = null

    // ───── Lifecycle ─────

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }

        createNotifChannel()
        todayKey = FirebaseManager.todayKey()

        // Load today's usage
        FirebaseManager.listenForTodayUsage { usage ->
            if (FirebaseManager.todayKey() != todayKey) {
                usedTodayMinutes.clear()
                todayKey = FirebaseManager.todayKey()
            }
            usedTodayMinutes.clear()
            usedTodayMinutes.putAll(usage)
        }

        FirebaseManager.listenForConfig { config ->
            blockedApps.clear()
            blockedApps.addAll(config.blockedApps)
            blockedGameApps.clear()
            blockedGameApps.addAll(config.blockedGameApps)
            knownGamePackages.addAll(config.blockedGameApps)
            appLimits.clear()
            appLimits.putAll(config.appLimits)
            appSchedules.clear()
            appSchedules.putAll(config.appSchedules)
            appExtraMinutes.clear()
            appExtraMinutes.putAll(config.appExtraMinutes)
            bedtime = config.bedtime
        }

        FirebaseManager.listenForCommands { type, params ->
            when (type) {
                "lock_screen" -> lockScreen()
                "screenshot"  -> takeScreenshot()
                "block_app"   -> params["package"]?.let { blockedApps.add(it.toString()) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg == packageName) return  // ignore our own UI

            if (pkg != currentApp) {
                // Record time for previous app
                if (currentApp.isNotEmpty() && appForegroundSince > 0L) {
                    val elapsed = (System.currentTimeMillis() - appForegroundSince) / 60_000L
                    if (elapsed > 0) {
                        scope.launch {
                            FirebaseManager.recordUsage(currentApp, elapsed)
                        }
                        usedTodayMinutes[currentApp] = (usedTodayMinutes[currentApp] ?: 0L) + elapsed
                    }
                    countdownJob?.cancel()
                    clearCountdownNotif()
                }

                currentApp = pkg
                appForegroundSince = System.currentTimeMillis()

                FirebaseManager.updateStatus(getBatteryLevel(), true, pkg)

                // Check bedtime
                if (isBedtime() && !bedtime.allowedApps.contains(pkg)) {
                    scope.launch(Dispatchers.Main) { performGlobalAction(GLOBAL_ACTION_HOME) }
                    return
                }

                // Check schedule
                val schedule = appSchedules[pkg]
                if (schedule != null && schedule.enabled && !isInSchedule(schedule)) {
                    showBlockedScreen(pkg, getAppLabel(pkg), "schedule")
                    return
                }

                // Check blocked list / game list
                if (blockedApps.contains(pkg) || isGameApp(pkg)) {
                    showBlockedScreen(pkg, getAppLabel(pkg), "blocked")
                    FirebaseManager.sendAlert("blocked_app", "Приложение закрыто", "$pkg — заблокировано")
                    return
                }

                // Check time limit
                val limitMin = appLimits[pkg]
                if (limitMin != null && limitMin > 0) {
                    val usedMin = usedTodayMinutes[pkg] ?: 0L
                    val extraMin = (appExtraMinutes[pkg] ?: 0).toLong()
                    val effectiveLimit = limitMin.toLong() + extraMin
                    if (usedMin >= effectiveLimit) {
                        showBlockedScreen(pkg, getAppLabel(pkg), "limit")
                        return
                    }
                    val remaining = effectiveLimit - usedMin
                    if (remaining <= 5) {
                        startCountdown(pkg, remaining)
                    }
                }
            }
        }

        // Monitor text on screen
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val text = event.text.joinToString(" ").lowercase()
            when {
                adultWords.any { text.contains(it) } ->
                    FirebaseManager.sendAlert("adult_content", "Взрослый контент",
                        "Приложение: $currentApp")
                matWords.any { text.contains(it) } ->
                    FirebaseManager.sendAlert("mat_content", "Нецензурная лексика",
                        "Приложение: $currentApp")
            }
        }
    }

    // ───── Blocking ─────

    private fun showBlockedScreen(pkg: String, appName: String, reason: String) {
        scope.launch(Dispatchers.Main) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            val intent = Intent(this@ScreenMonitorService, BlockedAppActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(BlockedAppActivity.EXTRA_PKG, pkg)
                putExtra(BlockedAppActivity.EXTRA_APP_NAME, appName)
                putExtra(BlockedAppActivity.EXTRA_REASON, reason)
                val usedMin = usedTodayMinutes[pkg] ?: 0L
                val limitMin = (appLimits[pkg] ?: 0).toLong()
                val extraMin = (appExtraMinutes[pkg] ?: 0).toLong()
                putExtra(BlockedAppActivity.EXTRA_USED_MIN, usedMin)
                putExtra(BlockedAppActivity.EXTRA_LIMIT_MIN, limitMin + extraMin)
            }
            startActivity(intent)
        }
    }

    // ───── Countdown ─────

    private fun startCountdown(pkg: String, remainingMin: Long) {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            var remaining = remainingMin
            while (remaining > 0 && currentApp == pkg) {
                showCountdownNotif(getAppLabel(pkg), remaining)
                delay(60_000)
                remaining--
                usedTodayMinutes[pkg] = (usedTodayMinutes[pkg] ?: 0L) + 1
            }
            if (currentApp == pkg && remaining <= 0) {
                withContext(Dispatchers.Main) {
                    showBlockedScreen(pkg, getAppLabel(pkg), "limit")
                }
            }
            clearCountdownNotif()
        }
    }

    private fun showCountdownNotif(appName: String, remaining: Long) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("⏱ $appName — осталось $remaining мин")
            .setContentText("Скоро время выйдет")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(NOTIF_ID, notif)
    }

    private fun clearCountdownNotif() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
    }

    private fun createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL, "Таймер", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    // ───── Bedtime & Schedule ─────

    private fun isBedtime(): Boolean {
        if (!bedtime.enabled) return false
        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val bedMin = bedtime.hour * 60 + bedtime.minute
        val wakeMin = 7 * 60  // wake up at 7:00
        return if (bedMin > wakeMin) nowMin >= bedMin || nowMin < wakeMin
        else nowMin >= bedMin && nowMin < wakeMin
    }

    private fun isInSchedule(s: AppSchedule): Boolean {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_WEEK)  // 1=Sun, 2=Mon...
        val androidDay = if (day == 1) 7 else day - 1  // convert to Mon=1..Sun=7
        if (!s.days.contains(androidDay)) return false
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMin = s.startHour * 60 + s.startMin
        val endMin = s.endHour * 60 + s.endMin
        return nowMin in startMin until endMin
    }

    // ───── Helpers ─────

    private fun isGameApp(pkg: String): Boolean {
        if (blockedGameApps.contains(pkg)) return true
        return knownGamePackages.any { pkg.startsWith(it) || pkg.contains("game") }
    }

    private fun getAppLabel(pkg: String): String {
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg.substringAfterLast(".")
        }
    }

    // ───── Screenshot ─────

    fun takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, null)!!
                                screenshot.hardwareBuffer.close()
                                val stream = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 30, stream)
                                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                                FirebaseManager.sendAlert("screenshot", "Скриншот", currentApp, base64)
                            } catch (e: Exception) { /* ignore */ }
                        }
                    }
                    override fun onFailure(errorCode: Int) {}
                }
            )
        }
    }

    private fun lockScreen() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, GuardianAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) dpm.lockNow()
    }

    private fun getBatteryLevel(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        return bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
