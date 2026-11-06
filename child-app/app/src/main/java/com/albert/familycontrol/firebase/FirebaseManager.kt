package com.albert.familycontrol.firebase

import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

object FirebaseManager {

    private val db = Firebase.database.reference
    private val auth = Firebase.auth

    var deviceId: String = ""
        private set

    data class ChildConfig(
        val blockInternet: Boolean = false,
        val blockedApps: List<String> = emptyList(),
        val blockedDomains: List<String> = defaultBlockedDomains,
        val blockedGameApps: List<String> = defaultGameApps,
        val screenStreaming: Boolean = false
    )

    val defaultBlockedDomains = listOf(
        // Порно
        "pornhub.com", "xvideos.com", "xnxx.com", "onlyfans.com",
        "redtube.com", "youporn.com", "brazzers.com",
        // Яндекс игры
        "games.yandex.ru", "yandex.ru",
        // Игровые сайты
        "miniclip.com", "poki.com", "gamesgames.com", "friv.com",
        "y8.com", "coolmathgames.com", "agame.com",
        // Прочее
        "rutracker.org"
    )

    val defaultGameApps = listOf(
        // Яндекс
        "com.yandex.browser",
        // Игровые приложения (пополняется родителями)
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

    suspend fun init() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
        deviceId = auth.currentUser!!.uid
        db.child("devices").child(deviceId).child("status/online").setValue(true)
        db.child("devices").child(deviceId).child("status/online")
            .onDisconnect().setValue(false)
    }

    fun listenForConfig(onConfig: (ChildConfig) -> Unit) {
        if (deviceId.isEmpty()) return
        db.child("devices").child(deviceId).child("config")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val blockedApps = snapshot.child("blockedApps").children
                        .mapNotNull { it.getValue(String::class.java) }
                    val blockedDomains = snapshot.child("blockedDomains").children
                        .mapNotNull { it.getValue(String::class.java) }
                        .ifEmpty { defaultBlockedDomains }
                    val blockedGameApps = snapshot.child("blockedGameApps").children
                        .mapNotNull { it.getValue(String::class.java) }
                        .ifEmpty { defaultGameApps }
                    onConfig(ChildConfig(
                        blockInternet = snapshot.child("blockInternet").getValue(Boolean::class.java) ?: false,
                        blockedApps = blockedApps,
                        blockedDomains = blockedDomains,
                        blockedGameApps = blockedGameApps,
                        screenStreaming = snapshot.child("screenStreaming").getValue(Boolean::class.java) ?: false
                    ))
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

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

    fun sendAlert(type: String, title: String, body: String, imageBase64: String? = null) {
        if (deviceId.isEmpty()) return
        val alert = mutableMapOf<String, Any>(
            "type" to type,
            "title" to title,
            "body" to body,
            "ts" to System.currentTimeMillis()
        )
        imageBase64?.let { alert["image"] = it }
        db.child("devices").child(deviceId).child("alerts").push().setValue(alert)
    }

    fun sendVisitedDomain(domain: String, source: String) {
        if (deviceId.isEmpty()) return
        val entry = mapOf(
            "domain" to domain,
            "source" to source,
            "ts" to System.currentTimeMillis()
        )
        db.child("devices").child(deviceId).child("history/sites").push().setValue(entry)
    }

    fun sendSms(from: String, body: String) {
        if (deviceId.isEmpty()) return
        val sms = mapOf("from" to from, "body" to body, "ts" to System.currentTimeMillis())
        db.child("devices").child(deviceId).child("sms").push().setValue(sms)
    }

    fun sendPhoto(base64: String, name: String) {
        if (deviceId.isEmpty()) return
        val photo = mapOf("data" to base64, "name" to name, "ts" to System.currentTimeMillis())
        db.child("devices").child(deviceId).child("gallery").push().setValue(photo)
    }

    fun listenForCommands(onCommand: (String, Map<String, Any>) -> Unit) {
        if (deviceId.isEmpty()) return
        db.child("devices").child(deviceId).child("commands")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (cmd in snapshot.children) {
                        val type = cmd.child("type").getValue(String::class.java) ?: continue
                        val params = cmd.children.associate { it.key!! to (it.value ?: "") }
                        onCommand(type, params)
                        cmd.ref.removeValue()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}
