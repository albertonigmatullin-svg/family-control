package com.albert.familycontrol.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class GuardianAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {}
    override fun onDisabled(context: Context, intent: Intent) {
        // Если кто-то снял Device Admin — перезапрашиваем
        val reactivate = Intent(context, com.albert.familycontrol.ui.MainActivity::class.java)
        reactivate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(reactivate)
    }
}
