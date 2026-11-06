package com.albert.familycontrol.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.albert.familycontrol.R
import com.albert.familycontrol.firebase.FirebaseManager

class BlockedAppActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_REASON = "reason"    // blocked | limit | schedule
        const val EXTRA_USED_MIN = "used_min"
        const val EXTRA_LIMIT_MIN = "limit_min"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)

        val pkg = intent.getStringExtra(EXTRA_PKG) ?: ""
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: pkg
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "blocked"
        val usedMin = intent.getLongExtra(EXTRA_USED_MIN, 0L)
        val limitMin = intent.getLongExtra(EXTRA_LIMIT_MIN, 0L)

        val tvEmoji = findViewById<TextView>(R.id.tvBlockedEmoji)
        val tvTitle = findViewById<TextView>(R.id.tvBlockedTitle)
        val tvSubtitle = findViewById<TextView>(R.id.tvBlockedSubtitle)
        val layoutRequest = findViewById<LinearLayout>(R.id.layoutRequest)
        val btn15 = findViewById<Button>(R.id.btn15min)
        val btn30 = findViewById<Button>(R.id.btn30min)
        val btn60 = findViewById<Button>(R.id.btn60min)

        when (reason) {
            "limit" -> {
                tvEmoji.text = "⏱"
                tvTitle.text = "$appName — лимит исчерпан"
                tvSubtitle.text = "Использовано $usedMin мин из $limitMin мин сегодня"
                layoutRequest.visibility = View.VISIBLE
            }
            "schedule" -> {
                tvEmoji.text = "🕐"
                tvTitle.text = "$appName сейчас недоступен"
                tvSubtitle.text = "Не время для этого приложения"
                layoutRequest.visibility = View.VISIBLE
            }
            else -> {
                tvEmoji.text = "🚫"
                tvTitle.text = "$appName заблокирован"
                tvSubtitle.text = "Это приложение не разрешено"
                layoutRequest.visibility = View.GONE
            }
        }

        fun sendRequest(minutes: Int) {
            FirebaseManager.sendTimeRequest(pkg, appName, minutes)
            Toast.makeText(this, "Запрос отправлен родителям (+$minutes мин)", Toast.LENGTH_SHORT).show()
            btn15.isEnabled = false
            btn30.isEnabled = false
            btn60.isEnabled = false
        }

        btn15.setOnClickListener { sendRequest(15) }
        btn30.setOnClickListener { sendRequest(30) }
        btn60.setOnClickListener { sendRequest(60) }
    }

    override fun onBackPressed() {
        // Don't allow going back — child stays on this screen
    }
}
