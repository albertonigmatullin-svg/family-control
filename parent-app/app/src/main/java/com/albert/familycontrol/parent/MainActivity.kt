package com.albert.familycontrol.parent

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.albert.familycontrol.parent.databinding.ActivityMainBinding
import com.albert.familycontrol.parent.firebase.ParentFirebaseManager
import com.albert.familycontrol.parent.models.ChildStatus
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var statusListener: ValueEventListener? = null
    private var screenshotListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.dashboard_title)

        binding.btnLockScreen.setOnClickListener {
            ParentFirebaseManager.sendLockScreen()
            Toast.makeText(this, getString(R.string.command_sent_lock), Toast.LENGTH_SHORT).show()
        }

        binding.btnScreenshot.setOnClickListener {
            ParentFirebaseManager.sendScreenshot()
            Toast.makeText(this, getString(R.string.command_sent_screenshot), Toast.LENGTH_SHORT).show()
            listenForScreenshot()
        }

        binding.btnApps.setOnClickListener {
            startActivity(Intent(this, AppsActivity::class.java))
        }

        binding.btnInternet.setOnClickListener {
            startActivity(Intent(this, InternetActivity::class.java))
        }

        binding.btnAlerts.setOnClickListener {
            startActivity(Intent(this, AlertsActivity::class.java))
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        startListening()
    }

    private fun startListening() {
        statusListener = ParentFirebaseManager.listenStatus { status ->
            runOnUiThread { updateStatus(status) }
        }
    }

    private fun updateStatus(status: ChildStatus) {
        // Online dot
        binding.viewOnlineDot.setBackgroundResource(
            if (status.online) R.drawable.dot_green else R.drawable.dot_gray
        )
        binding.tvOnlineStatus.text = if (status.online) getString(R.string.online) else getString(R.string.offline)

        // Battery
        binding.tvBattery.text = getString(R.string.battery_format, status.battery)

        // Screen
        binding.tvScreenOn.text = getString(
            R.string.screen_format,
            if (status.screenOn) getString(R.string.on) else getString(R.string.off)
        )

        // Current app
        binding.tvCurrentApp.text = getString(
            R.string.current_app_format,
            status.currentApp.ifEmpty { getString(R.string.unknown) }
        )

        // Last seen
        if (status.lastSeen > 0) {
            val sdf = SimpleDateFormat("HH:mm:ss dd.MM", Locale.getDefault())
            binding.tvLastSeen.text = getString(R.string.last_seen_format, sdf.format(Date(status.lastSeen)))
        } else {
            binding.tvLastSeen.text = getString(R.string.last_seen_format, getString(R.string.never))
        }

        // Location
        if (status.lat != 0.0 || status.lng != 0.0) {
            binding.tvLocation.text = getString(
                R.string.location_format,
                String.format(Locale.US, "%.5f", status.lat),
                String.format(Locale.US, "%.5f", status.lng),
                status.locationAccuracy.toInt()
            )
            binding.tvLocation.visibility = View.VISIBLE
        } else {
            binding.tvLocation.visibility = View.GONE
        }
    }

    private fun listenForScreenshot() {
        screenshotListener?.let {
            ParentFirebaseManager.removeAlertsListener(it)
        }
        screenshotListener = ParentFirebaseManager.listenForScreenshot { imageBase64 ->
            runOnUiThread { showScreenshot(imageBase64) }
        }
    }

    private fun showScreenshot(base64: String) {
        try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                val imageView = android.widget.ImageView(this)
                imageView.setImageBitmap(bitmap)
                imageView.adjustViewBounds = true
                AlertDialog.Builder(this)
                    .setTitle(R.string.screenshot_title)
                    .setView(imageView)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.screenshot_error), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, R.string.disconnect)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            getSharedPreferences(LoginActivity.PREF_NAME, MODE_PRIVATE)
                .edit()
                .remove(LoginActivity.PREF_DEVICE_ID)
                .apply()
            ParentFirebaseManager.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        statusListener?.let { ParentFirebaseManager.removeStatusListener(it) }
        screenshotListener?.let { ParentFirebaseManager.removeAlertsListener(it) }
    }
}
