package com.albert.familycontrol.parent

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.albert.familycontrol.parent.databinding.ActivityConnectChildBinding
import com.albert.familycontrol.parent.firebase.ParentFirebaseManager

class ConnectChildActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConnectChildBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectChildBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = getString(R.string.connect_child_title)

        binding.btnConnect.setOnClickListener {
            val deviceId = binding.etDeviceId.text.toString().trim()
            if (deviceId.isEmpty()) {
                Toast.makeText(this, getString(R.string.enter_device_id), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Save to SharedPreferences
            getSharedPreferences(LoginActivity.PREF_NAME, MODE_PRIVATE)
                .edit()
                .putString(LoginActivity.PREF_DEVICE_ID, deviceId)
                .apply()

            ParentFirebaseManager.deviceId = deviceId

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
