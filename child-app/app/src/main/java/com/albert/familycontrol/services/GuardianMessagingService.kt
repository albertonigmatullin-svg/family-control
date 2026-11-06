package com.albert.familycontrol.services

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class GuardianMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        // Команды от родителей через FCM (для надёжности, дублируют Realtime DB)
        when (message.data["cmd"]) {
            "lock" -> sendBroadcast(android.content.Intent("com.albert.familycontrol.LOCK"))
            "screenshot" -> sendBroadcast(android.content.Intent("com.albert.familycontrol.SCREENSHOT"))
        }
    }
}
