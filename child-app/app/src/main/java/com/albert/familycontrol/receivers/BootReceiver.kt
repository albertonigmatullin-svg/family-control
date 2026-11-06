package com.albert.familycontrol.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.albert.familycontrol.services.GuardianVpnService
import com.albert.familycontrol.services.LocationService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startService(
                Intent(context, GuardianVpnService::class.java).apply {
                    action = GuardianVpnService.ACTION_START
                }
            )
            context.startForegroundService(Intent(context, LocationService::class.java))
        }
    }
}
