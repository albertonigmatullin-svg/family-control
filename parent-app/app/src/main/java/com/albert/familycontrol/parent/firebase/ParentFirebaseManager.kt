package com.albert.familycontrol.parent.firebase

import com.albert.familycontrol.parent.models.Alert
import com.albert.familycontrol.parent.models.ChildStatus
import com.albert.familycontrol.parent.models.TimeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ParentFirebaseManager {

    private val db = Firebase.database.reference
    private val auth = Firebase.auth

    var deviceId: String = ""

    // ───── Auth ─────

    suspend fun signIn(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            try {
                auth.createUserWithEmailAndPassword(email, password).await()
                Result.success(Unit)
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }

    fun isSignedIn(): Boolean = auth.currentUser != null
    fun signOut() = auth.signOut()

    // ───── Status ─────

    fun listenStatus(onStatus: (ChildStatus) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val location = snapshot.child("location")
                onStatus(ChildStatus(
                    online = snapshot.child("online").getValue(Boolean::class.java) ?: false,
                    battery = snapshot.child("battery").getValue(Int::class.java) ?: 0,
                    screenOn = snapshot.child("screenOn").getValue(Boolean::class.java) ?: false,
                    currentApp = snapshot.child("currentApp").getValue(String::class.java) ?: "",
                    lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L,
                    lat = location.child("lat").getValue(Double::class.java) ?: 0.0,
                    lng = location.child("lng").getValue(Double::class.java) ?: 0.0,
                    locationAccuracy = (location.child("accuracy").getValue(Double::class.java) ?: 0.0).toFloat()
                ))
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        deviceRef().child("status").addValueEventListener(listener)
        return listener
    }

    fun removeStatusListener(l: ValueEventListener) = deviceRef().child("status").removeEventListener(l)

    // ───── Commands ─────

    fun sendCommand(type: String, params: Map<String, Any> = emptyMap()) {
        val cmd = mutableMapOf<String, Any>("type" to type)
        cmd.putAll(params)
        deviceRef().child("commands").push().setValue(cmd)
    }

    fun sendLockScreen() = sendCommand("lock_screen")
    fun sendScreenshot() = sendCommand("screenshot")

    // ───── Config — blocked apps & domains ─────

    fun listenConfig(onConfig: (Boolean, List<String>, List<String>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val blockInternet = snapshot.child("blockInternet").getValue(Boolean::class.java) ?: false
                val blockedApps = snapshot.child("blockedApps").children
                    .mapNotNull { it.getValue(String::class.java) }
                val blockedDomains = snapshot.child("blockedDomains").children
                    .mapNotNull { it.getValue(String::class.java) }
                onConfig(blockInternet, blockedApps, blockedDomains)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        deviceRef().child("config").addValueEventListener(listener)
        return listener
    }

    fun removeConfigListener(l: ValueEventListener) = deviceRef().child("config").removeEventListener(l)

    fun setBlockInternet(block: Boolean) = deviceRef().child("config/blockInternet").setValue(block)
    fun setBlockedApps(apps: List<String>) = deviceRef().child("config/blockedApps").setValue(apps)
    fun setBlockedDomains(domains: List<String>) = deviceRef().child("config/blockedDomains").setValue(domains)

    // ───── Presets ─────

    fun setPreset(preset: String) {
        deviceRef().child("config/preset").setValue(preset)
    }

    fun listenPreset(onPreset: (String) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onPreset(snapshot.getValue(String::class.java) ?: "custom")
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        deviceRef().child("config/preset").addValueEventListener(listener)
        return listener
    }

    fun removePresetListener(l: ValueEventListener) =
        deviceRef().child("config/preset").removeEventListener(l)

    // ───── App limits ─────

    fun setAppLimit(packageName: String, limitMinutes: Int) {
        if (limitMinutes <= 0) {
            deviceRef().child("config/appLimits/$packageName").removeValue()
        } else {
            deviceRef().child("config/appLimits/$packageName").setValue(limitMinutes)
        }
    }

    fun listenAppLimits(onLimits: (Map<String, Int>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val limits = snapshot.children.associate {
                    (it.key ?: "") to (it.getValue(Int::class.java) ?: 0)
                }
                onLimits(limits)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        deviceRef().child("config/appLimits").addValueEventListener(listener)
        return listener
    }

    fun removeAppLimitsListener(l: ValueEventListener) =
        deviceRef().child("config/appLimits").removeEventListener(l)

    // ───── Today's usage ─────

    fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun listenTodayUsage(onUsage: (Map<String, Long>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val usageMap = snapshot.children.associate {
                    (it.key ?: "") to (it.getValue(Long::class.java) ?: 0L)
                }
                onUsage(usageMap)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        deviceRef().child("usage").child(todayKey()).addValueEventListener(listener)
        return listener
    }

    fun removeTodayUsageListener(l: ValueEventListener) =
        deviceRef().child("usage").child(todayKey()).removeEventListener(l)

    // ───── Bedtime ─────

    fun setBedtime(enabled: Boolean, hour: Int, minute: Int) {
        deviceRef().child("config/bedtime").updateChildren(mapOf(
            "enabled" to enabled,
            "hour" to hour,
            "minute" to minute
        ))
    }

    // ───── Time requests ─────

    fun listenRequests(onRequests: (List<TimeRequest>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val requests = snapshot.children.mapNotNull { child ->
                    TimeRequest(
                        key = child.key ?: return@mapNotNull null,
                        packageName = child.child("packageName").getValue(String::class.java) ?: "",
                        appName = child.child("appName").getValue(String::class.java) ?: "",
                        requestedMinutes = child.child("requestedMinutes").getValue(Int::class.java) ?: 30,
                        status = child.child("status").getValue(String::class.java) ?: "pending",
                        ts = child.child("ts").getValue(Long::class.java) ?: 0L
                    )
                }.sortedByDescending { it.ts }
                onRequests(requests)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        deviceRef().child("requests").addValueEventListener(listener)
        return listener
    }

    fun removeRequestsListener(l: ValueEventListener) =
        deviceRef().child("requests").removeEventListener(l)

    fun approveRequest(request: TimeRequest) {
        // Update request status
        deviceRef().child("requests/${request.key}/status").setValue("approved")
        // Grant extra minutes
        val pkgKey = request.packageName.replace(".", "_")
        deviceRef().child("config/appExtraMinutes/${pkgKey}").let { ref ->
            // Read current and add
            ref.get().addOnSuccessListener { snap ->
                val current = snap.getValue(Int::class.java) ?: 0
                ref.setValue(current + request.requestedMinutes)
            }
        }
        // Store with real key (dots allowed in Firebase path segments but not recommended)
        deviceRef().child("config/appExtraMinutes").child(request.packageName)
            .get().addOnSuccessListener { snap ->
                val current = snap.getValue(Int::class.java) ?: 0
                deviceRef().child("config/appExtraMinutes").child(request.packageName)
                    .setValue(current + request.requestedMinutes)
            }
    }

    fun denyRequest(requestKey: String) {
        deviceRef().child("requests/$requestKey/status").setValue("denied")
    }

    // ───── Alerts ─────

    fun listenAlerts(onAlerts: (List<Alert>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alerts = snapshot.children.mapNotNull { child ->
                    Alert(
                        key = child.key ?: return@mapNotNull null,
                        type = child.child("type").getValue(String::class.java) ?: "",
                        title = child.child("title").getValue(String::class.java) ?: "",
                        body = child.child("body").getValue(String::class.java) ?: "",
                        ts = child.child("ts").getValue(Long::class.java) ?: 0L,
                        image = child.child("image").getValue(String::class.java)
                    )
                }.sortedByDescending { it.ts }
                onAlerts(alerts)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        deviceRef().child("alerts").addValueEventListener(listener)
        return listener
    }

    fun removeAlertsListener(l: ValueEventListener) = deviceRef().child("alerts").removeEventListener(l)

    // ───── History ─────

    fun listenHistory(onHistory: (List<Pair<String, Long>>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sites = snapshot.children.mapNotNull { child ->
                    val domain = child.child("domain").getValue(String::class.java) ?: return@mapNotNull null
                    val ts = child.child("ts").getValue(Long::class.java) ?: 0L
                    domain to ts
                }.sortedByDescending { it.second }
                onHistory(sites)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        deviceRef().child("history/sites").addValueEventListener(listener)
        return listener
    }

    fun removeHistoryListener(l: ValueEventListener) =
        deviceRef().child("history/sites").removeEventListener(l)

    // ───── Screenshot ─────

    fun listenForScreenshot(onImage: (String) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val type = child.child("type").getValue(String::class.java)
                    if (type == "screenshot_result") {
                        val imageBase64 = child.child("image").getValue(String::class.java) ?: continue
                        onImage(imageBase64)
                        child.ref.removeValue()
                        return
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        deviceRef().child("alerts").addValueEventListener(listener)
        return listener
    }

    // ───── Helper ─────

    private fun deviceRef() = db.child("devices").child(deviceId)
}
