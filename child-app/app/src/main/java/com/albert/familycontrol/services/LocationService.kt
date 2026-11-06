package com.albert.familycontrol.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import com.albert.familycontrol.firebase.FirebaseManager
import com.google.android.gms.location.*

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    companion object {
        const val CHANNEL_ID = "fc_location"
        const val NOTIF_ID = 1002
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val locationMap = mapOf(
                    "lat" to loc.latitude,
                    "lng" to loc.longitude,
                    "accuracy" to loc.accuracy,
                    "ts" to System.currentTimeMillis()
                )
                FirebaseManager.updateStatus(
                    battery = 0,
                    screenOn = true,
                    currentApp = "",
                    location = locationMap
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, 300_000 // каждые 5 минут
        ).setMinUpdateDistanceMeters(50f).build()

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) { /* разрешение не выдано */ }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "FamilyControl Location", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Местоположение")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true).build()
}
