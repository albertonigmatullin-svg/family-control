package com.albert.familycontrol.parent

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.albert.familycontrol.parent.adapters.AlertAdapter
import com.albert.familycontrol.parent.databinding.ActivityAlertsBinding
import com.albert.familycontrol.parent.firebase.ParentFirebaseManager
import com.google.firebase.database.ValueEventListener

class AlertsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertsBinding
    private lateinit var adapter: AlertAdapter
    private var alertsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = getString(R.string.alerts_title)
            setDisplayHomeAsUpEnabled(true)
        }

        adapter = AlertAdapter()
        binding.rvAlerts.layoutManager = LinearLayoutManager(this)
        binding.rvAlerts.adapter = adapter

        alertsListener = ParentFirebaseManager.listenAlerts { alerts ->
            runOnUiThread {
                adapter.submitList(alerts)
                binding.tvEmpty.visibility =
                    if (alerts.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        alertsListener?.let { ParentFirebaseManager.removeAlertsListener(it) }
    }
}
