package com.albert.familycontrol.firebase

import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FirebaseManager {

    private val db = Firebase.database.reference
    private val auth = Firebase.auth

    var deviceId: String = ""
        private set

    // ───── Schemas ─────

    data class ChildConfig(
        val blockInternet: Boolean = false,
        val blockedApps: List<String> = emptyList(),
        val blockedDomains: List<String> = defaultBlockedDomains,
        val blockedGameApps: List<String> = defaultGameApps,
        val screenStreaming: Boolean = false,
        val preset: String = "custom",           // school | evening | weekend | custom
        val appLimits: Map<String, Int> = emptyMap(),  // pkg → limitMinutes
        val appSchedules: Map<String, AppSchedule> = emptyMap(),
        val bedtime: BedtimeConfig = BedtimeConfig(),
        val appExtraMinutes: Map<String, Int> = emptyMap()  // pkg → extra granted today
    )

    data class AppSchedule(
        val enabled: Boolean = false,
        val startHour: Int = 15,
        val startMin: Int = 0,
        val endHour: Int = 21,
        val endMin: Int = 0,
        val days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7)
    )

    data class BedtimeConfig(
        val enabled: Boolean = false,
        val hour: Int = 22,
        val minute: Int = 0,
        val allowedApps: List<String> = listOf("com.android.phone", "com.samsung.android.dialer")
    )

    data class TimeRequest(
        val key: String = "",
        val packageName: String = "",
        val appName: String = "",
        val requestedMinutes: Int = 30,
        val status: String = "pending",   // pending | approved | denied
        val ts: Long = 0L
    )

    // ───── Static data ─────

    val defaultBlockedDomains = listOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "onlyfans.com",
        "redtube.com", "youporn.com", "brazzers.com",
        "games.yandex.ru", "miniclip.com", "poki.com", "gamesgames.com",
        "friv.com", "y8.com", "coolmathgames.com", "agame.com",
        "rutracker.org"
    )

    val defaultGameApps = listOf(
        "com.yandex.browser",
        "com.onay.app"
    )

    val adultKeywords = listOf(
        "порно", "секс", "эротика", "голая", "голый", "18+", "xxx",
        "porn", "sex", "nude", "naked", "adult", "erotic"
    )

    val matKeywords = listOf(
        "блядь", "пизда", "хуй", "ёбаный", "залупа",
        "мудак", "пидор", "нахуй"
    )

    // ───── Preset definitions ─────

    val presets = mapOf(
        "school" to ChildConfig(
            preset = "school",
            blockedApps = listOf(
                "com.roblox.client", "com.mojang.minecraftpe",
                "com.zhiliaoapp.musically", "com.snapchat.android",
                "com.vkontakte.android", "com.king.candycrushsaga",
                "com.supercell.clashofclans"
            ),
            appLimits = mapOf("com.google.android.youtube" to 30),
            bedtime = BedtimeConfig(enabled = false)
        ),
        "evening" to ChildConfig(
            preset = "evening",
            blockedApps = emptyList(),
            appLimits = mapOf(
                "com.roblox.client" to 60,
                "com.mojang.minecraftpe" to 60,
                "com.google.android.youtube" to 90
            ),
            bedtime = BedtimeConfig(enabled = true, hour = 22, minute = 0)
        ),
        "weekend" to ChildConfig(
            preset = "weekend",
            blockedApps = emptyList(),
            appLimits = mapOf(
                "com.roblox.client" to 120,
                "com.mojang.minecraftpe" to 120,
                "com.google.android.youtube" to 180
            ),
            bedtime = BedtimeConfig(enabled = true, hour = 23, minute = 0)
        )
    )

    // ───── Auth ─────

    suspend fun init() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
        deviceId = auth.currentUser!!.uid
        db.child("devices").child(deviceId).child("status/online").setValue(true)
        db.child("devices").child(deviceId).child("status/online")
            .onDisconnect().setValue(false)
    }

    // ───── Config listener ─────

    fun listenForConfig(onConfig: (ChildConfig) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val preset = snapshot.child("preset").getValue(String::class.java) ?: "custom"

                // If preset is active, return the preset definition
                if (preset != "custom" && presets.containsKey(preset)) {
                    val base = presets[preset]!!
                    // Merge extra minutes from Firebase into preset
                    val extras = snapshot.child("appExtraMinutes").children.associate {
                        (it.key ?: "") to (it.getValue(Int::class.java) ?: 0)
                    }
                    onConfig(base.copy(appExtraMinutes = extras))
                    return
                }

                // Custom config
                val blockedApps = snapshot.child("blockedApps").children
                    .mapNotNull { it.getValue(String::class.java) }
                val blockedDomains = snapshot.child("blockedDomains").children
                    .mapNotNull { it.getValue(String::class.java) }
                    .ifEmpty { defaultBlockedDomains }
                val blockedGameApps = snapshot.child("blockedGameApps").children
                    .mapNotNull { it.getValue(String::class.java) }
                    .ifEmpty { defaultGameApps }

                val appLimits = snapshot.child("appLimits").children.associate {
                    (it.key ?: "") to (it.getValue(Int::class.java) ?: 0)
                }

                val appExtraMinutes = snapshot.child("appExtraMinutes").children.associate {
                    (it.key ?: "") to (it.getValue(Int::class.java) ?: 0)
                }

                val appSchedules = snapshot.child("appSchedules").children.associate { child ->
                    val key = child.key ?: ""
                    val schedule = AppSchedule(
                        enabled = child.child("enabled").getValue(Boolean::class.java) ?: false,
                        startHour = child.child("startHour").getValue(Int::class.java) ?: 15,
                        startMin = child.child("startMin").getValue(Int::class.java) ?: 0,
                        endHour = child.child("endHour").getValue(Int::class.java) ?: 21,
                        endMin = child.child("endMin").getValue(Int::class.java) ?: 0,
                        days = child.child("days").children
                            .mapNotNull { it.getValue(Int::class.java) }
                            .ifEmpty { listOf(1,2,3,4,5,6,7) }
                    )
                    key to schedule
                }

                val bedtime = snapshot.child("bedtime").let { b ->
                    BedtimeConfig(
                        enabled = b.child("enabled").getValue(Boolean::class.java) ?: false,
                        hour = b.child("hour").getValue(Int::class.java) ?: 22,
                        minute = b.child("minute").getValue(Int::class.java) ?: 0,
                        allowedApps = b.child("allowedApps").children
                            .mapNotNull { it.getValue(String::class.java) }
                            .ifEmpty { listOf("com.android.phone", "com.samsung.android.dialer") }
                    )
                }

                onConfig(ChildConfig(
                    blockInternet = snapshot.child("blockInternet").getValue(Boolean::class.java) ?: false,
                    blockedApps = blockedApps,
                    blockedDomains = blockedDomains,
                    blockedGameApps = blockedGameApps,
                    screenStreaming = snapshot.child("screenStreaming").getValue(Boolean::class.java) ?: false,
                    preset = preset,
                    appLimits = appLimits,
                    appSchedules = appSchedules,
                    bedtime = bedtime,
                    appExtraMinutes = appExtraMinutes
                ))
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("devices").child(deviceId).child("config").addValueEventListener(listener)
        return listener
    }

    fun removeConfigListener(listener: ValueEventListener) {
        db.child("devices").child(deviceId).child("config").removeEventListener(listener)
    }

    // ───── Today's usage ─────

    fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun listenForTodayUsage(onUsage: (Map<String, Long>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val usageMap = snapshot.children.associate { child ->
                    val pkg = child.key ?: ""
                    val minutes = child.getValue(Long::class.java) ?: 0L
                    pkg to minutes
                }
                onUsage(usageMap)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("devices").child(deviceId).child("usage").child(todayKey())
            .addValueEventListener(listener)
        return listener
    }

    fun recordUsage(packageName: String, minutes: Long) {
        if (deviceId.isEmpty() || minutes <= 0) return
        db.child("devices").child(deviceId).child("usage")
            .child(todayKey()).child(packageName)
            .setValue(ServerValue.increment(minutes))
    }

    // ───── Status ─────

    fun updateStatus(battery: Int, screenOn: Boolean, currentApp: String, location: Map<String, Any>? = null) {
        if (deviceId.isEmpty()) return
        val updates = mutableMapOf<String, Any>(
            "status/battery" to battery,
            "status/screenOn" to screenOn,
            "status/currentApp" to currentApp,
            "status/lastSeen" to System.currentTimeMillis()
        )
        location?.let { updates["status/location"] = it }
        db.child("devices").child(deviceId).updateChildren(updates)
    }

    // ───── Alerts ─────

    fun sendAlert(type: String, title: String, body: String, imageBase64: String? = null) {
        if (deviceId.isEmpty()) return
        val alert = mutableMapOf<String, Any>(
            "type" to type, "title" to title, "body" to body,
            "ts" to System.currentTimeMillis()
        )
        imageBase64?.let { alert["image"] = it }
        db.child("devices").child(deviceId).child("alerts").push().setValue(alert)
    }

    fun sendVisitedDomain(domain: String, source: String) {
        if (deviceId.isEmpty()) return
        db.child("devices").child(deviceId).child("history/sites").push()
            .setValue(mapOf("domain" to domain, "source" to source, "ts" to System.currentTimeMillis()))
    }

    fun sendSms(from: String, body: String) {
        if (deviceId.isEmpty()) return
        db.child("devices").child(deviceId).child("sms").push()
            .setValue(mapOf("from" to from, "body" to body, "ts" to System.currentTimeMillis()))
    }

    fun sendPhoto(base64: String, name: String) {
        if (deviceId.isEmpty()) return
        db.child("devices").child(deviceId).child("gallery").push()
            .setValue(mapOf("data" to base64, "name" to name, "ts" to System.currentTimeMillis()))
    }

    // ───── Time requests (child → parent) ─────

    fun sendTimeRequest(packageName: String, appName: String, requestedMinutes: Int) {
        if (deviceId.isEmpty()) return
        val request = mapOf(
            "packageName" to packageName,
            "appName" to appName,
            "requestedMinutes" to requestedMinutes,
            "status" to "pending",
            "ts" to System.currentTimeMillis()
        )
        db.child("devices").child(deviceId).child("requests").push().setValue(request)
    }

    // ───── Commands ─────

    fun listenForCommands(onCommand: (String, Map<String, Any>) -> Unit): ValueEventListener {
        if (deviceId.isEmpty()) return object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (cmd in snapshot.children) {
                    val type = cmd.child("type").getValue(String::class.java) ?: continue
                    val params = cmd.children.associate { it.key!! to (it.value ?: "") }
                    onCommand(type, params)
                    cmd.ref.removeValue()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("devices").child(deviceId).child("commands").addValueEventListener(listener)
        return listener
    }
}
