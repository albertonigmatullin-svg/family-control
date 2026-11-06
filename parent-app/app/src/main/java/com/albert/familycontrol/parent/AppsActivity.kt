package com.albert.familycontrol.parent

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.albert.familycontrol.parent.adapters.AppListAdapter
import com.albert.familycontrol.parent.databinding.ActivityAppsBinding
import com.albert.familycontrol.parent.firebase.ParentFirebaseManager
import com.google.firebase.database.ValueEventListener

class AppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppsBinding
    private lateinit var adapter: AppListAdapter
    private var configListener: ValueEventListener? = null

    // Common apps that parents might want to block
    private val commonApps = listOf(
        "com.yandex.browser" to "Yandex Browser",
        "com.android.chrome" to "Chrome",
        "org.telegram.messenger" to "Telegram",
        "com.vkontakte.android" to "VKontakte",
        "ru.ok.android" to "Odnoklassniki",
        "com.instagram.android" to "Instagram",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.google.android.youtube" to "YouTube",
        "com.snapchat.android" to "Snapchat",
        "com.roblox.client" to "Roblox",
        "com.mojang.minecraftpe" to "Minecraft",
        "com.onay.app" to "Onay",
        "kz.onay.mobile" to "Onay Mobile"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = getString(R.string.apps_title)
            setDisplayHomeAsUpEnabled(true)
        }

        adapter = AppListAdapter(commonApps.map { it.first to it.second }, emptySet()) { packageName, blocked ->
            val current = adapter.blockedApps.toMutableSet()
            if (blocked) current.add(packageName) else current.remove(packageName)
            ParentFirebaseManager.setBlockedApps(current.toList())
            Toast.makeText(
                this,
                if (blocked) getString(R.string.app_blocked, packageName)
                else getString(R.string.app_unblocked, packageName),
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter

        // Listen for current blocked apps from Firebase
        configListener = ParentFirebaseManager.listenConfig { _, blockedApps, _ ->
            runOnUiThread {
                adapter.updateBlocked(blockedApps.toSet())
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        configListener?.let { ParentFirebaseManager.removeConfigListener(it) }
    }
}
