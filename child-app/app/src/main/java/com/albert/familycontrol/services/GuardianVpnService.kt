package com.albert.familycontrol.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.albert.familycontrol.firebase.FirebaseManager
import kotlinx.coroutines.*
import kotlinx.coroutines.currentCoroutineContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class GuardianVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var blockedDomains: Set<String> = FirebaseManager.defaultBlockedDomains.toSet()
    private var blockAllInternet = false

    companion object {
        const val ACTION_START = "START_VPN"
        const val ACTION_STOP = "STOP_VPN"
        const val CHANNEL_ID = "fc_vpn"
        const val NOTIF_ID = 1001
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        try {
            vpnInterface = Builder()
                .addAddress("10.0.0.2", 32)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .addRoute("0.0.0.0", 0)
                .setSession("FamilyControl")
                .setMtu(1500)
                .establish()
            running = true
            scope.launch { processPackets() }
            FirebaseManager.listenForConfig { config ->
                blockedDomains = config.blockedDomains.toSet()
                blockAllInternet = config.blockInternet
            }
        } catch (e: Exception) {
            stopVpn()
        }
    }

    private fun stopVpn() {
        running = false
        scope.cancel()
        vpnInterface?.close()
        vpnInterface = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private suspend fun processPackets() = withContext(Dispatchers.IO) {
        val fd = vpnInterface?.fileDescriptor ?: return@withContext
        val inputStream = FileInputStream(fd)
        val outputStream = FileOutputStream(fd)
        val buffer = ByteBuffer.allocate(32767)

        while (running && currentCoroutineContext().isActive) {
            try {
                buffer.clear()
                val length = inputStream.read(buffer.array())
                if (length <= 0) { delay(10); continue }
                buffer.limit(length)
                if (blockAllInternet) continue

                val domain = extractDnsQuery(buffer.array(), length)
                if (domain != null) {
                    val root = getRootDomain(domain)
                    if (isBlocked(root)) {
                        FirebaseManager.sendAlert("blocked_site", "Заблокирован сайт", "Попытка: $domain")
                        continue
                    }
                    if (domain.isNotEmpty()) FirebaseManager.sendVisitedDomain(domain, "vpn")
                }
                outputStream.write(buffer.array(), 0, length)
            } catch (e: Exception) {
                if (!running) break
                delay(10)
            }
        }
    }

    private fun isBlocked(domain: String): Boolean {
        if (domain.isEmpty()) return false
        return blockedDomains.any { domain == it || domain.endsWith(".$it") }
    }

    private fun getRootDomain(domain: String): String {
        val parts = domain.split(".")
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else domain
    }

    private fun extractDnsQuery(data: ByteArray, length: Int): String? {
        try {
            if (length < 28) return null
            val ipHeaderLen = (data[0].toInt() and 0x0F) * 4
            val protocol = data[9].toInt() and 0xFF
            if (protocol != 17) return null
            val udpOffset = ipHeaderLen
            val destPort = ((data[udpOffset + 2].toInt() and 0xFF) shl 8) or
                    (data[udpOffset + 3].toInt() and 0xFF)
            if (destPort != 53) return null
            val dnsOffset = udpOffset + 8
            if (length <= dnsOffset + 12) return null
            val sb = StringBuilder()
            var pos = dnsOffset + 12
            while (pos < length) {
                val labelLen = data[pos].toInt() and 0xFF
                if (labelLen == 0) break
                if (sb.isNotEmpty()) sb.append('.')
                pos++
                if (pos + labelLen > length) break
                sb.append(String(data, pos, labelLen))
                pos += labelLen
            }
            return sb.toString().lowercase()
        } catch (e: Exception) { return null }
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "FamilyControl", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Защита активна")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true).build()

    override fun onDestroy() { super.onDestroy(); stopVpn() }
}
