package com.albert.familycontrol.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.accessibility.AccessibilityEvent
import com.albert.familycontrol.firebase.FirebaseManager
import com.albert.familycontrol.receivers.GuardianAdminReceiver
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

class ScreenMonitorService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentApp = ""
    private var blockedApps = mutableSetOf<String>()
    private var blockedGameApps = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var screenshotInterval = 30_000L  // 30 сек

    // Слова-триггеры для алертов
    private val adultWords = FirebaseManager.adultKeywords
    private val matWords = FirebaseManager.matKeywords

    // Известные игровые пакеты (расширяется из Firebase)
    private val knownGamePackages = mutableSetOf(
        "com.yandex.browser",         // Яндекс браузер (игры)
        "com.onay.app",               // Onay
        "com.gameloft.android",
        "com.king.candycrushsaga",
        "com.supercell.clashofclans",
        "com.mojang.minecraftpe",
        "com.roblox.client",
        "com.activision.callofduty.shooter"
    )

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        FirebaseManager.listenForConfig { config ->
            blockedApps.clear()
            blockedApps.addAll(config.blockedApps)
            blockedGameApps.clear()
            blockedGameApps.addAll(config.blockedGameApps)
            knownGamePackages.addAll(config.blockedGameApps)
        }
        // Слушаем команды от родителей
        FirebaseManager.listenForCommands { type, params ->
            when (type) {
                "lock_screen" -> lockScreen()
                "screenshot" -> takeScreenshot()
                "block_app" -> params["package"]?.let { blockedApps.add(it.toString()) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg != currentApp) {
                currentApp = pkg
                FirebaseManager.updateStatus(
                    battery = getBatteryLevel(),
                    screenOn = true,
                    currentApp = pkg
                )
                // Проверяем заблокированные приложения
                if (blockedApps.contains(pkg) || isGameApp(pkg)) {
                    scope.launch(Dispatchers.Main) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                    val reason = if (blockedApps.contains(pkg)) "заблокировано" else "игра запрещена врачом"
                    FirebaseManager.sendAlert("blocked_app", "Приложение закрыто", "$pkg — $reason")
                }
            }
        }

        // Мониторинг текста на экране
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val text = event.text.joinToString(" ").lowercase()
            when {
                adultWords.any { text.contains(it) } ->
                    FirebaseManager.sendAlert("adult_content", "Обнаружен взрослый контент",
                        "Приложение: $currentApp")
                matWords.any { text.contains(it) } ->
                    FirebaseManager.sendAlert("mat_content", "Нецензурная лексика",
                        "Приложение: $currentApp")
            }
        }
    }

    private fun isGameApp(pkg: String): Boolean {
        if (blockedGameApps.contains(pkg)) return true
        return knownGamePackages.any { pkg.startsWith(it) || pkg.contains("game") }
    }

    fun takeScreenshot() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val bitmap = Bitmap.wrapHardwareBuffer(
                                    screenshot.hardwareBuffer, null
                                )!!
                                screenshot.hardwareBuffer.close()
                                // Сжимаем до 30% качества для экономии места
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
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
        }
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
