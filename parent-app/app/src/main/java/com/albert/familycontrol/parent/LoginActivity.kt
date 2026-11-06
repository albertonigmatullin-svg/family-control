package com.albert.familycontrol.parent

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.albert.familycontrol.parent.databinding.ActivityLoginBinding
import com.albert.familycontrol.parent.firebase.ParentFirebaseManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    companion object {
        const val PREF_NAME = "family_control_prefs"
        const val PREF_DEVICE_ID = "child_device_id"
        const val DEFAULT_EMAIL = "parent@familycontrol.local"
        const val DEFAULT_PASSWORD = "guardian2024"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pre-fill with default credentials
        binding.etEmail.setText(DEFAULT_EMAIL)
        binding.etPassword.setText(DEFAULT_PASSWORD)

        // If already signed in, go straight to main/connect
        if (ParentFirebaseManager.isSignedIn()) {
            goNext()
            return
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            setLoading(true)
            lifecycleScope.launch {
                val result = ParentFirebaseManager.signIn(email, password)
                setLoading(false)
                if (result.isSuccess) {
                    goNext()
                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        getString(R.string.login_error, result.exceptionOrNull()?.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun goNext() {
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val deviceId = prefs.getString(PREF_DEVICE_ID, "")
        if (deviceId.isNullOrEmpty()) {
            startActivity(Intent(this, ConnectChildActivity::class.java))
        } else {
            ParentFirebaseManager.deviceId = deviceId
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
