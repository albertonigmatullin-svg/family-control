package com.albert.familycontrol.parent

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.albert.familycontrol.parent.adapters.HistoryAdapter
import com.albert.familycontrol.parent.databinding.ActivityHistoryBinding
import com.albert.familycontrol.parent.firebase.ParentFirebaseManager
import com.google.firebase.database.ValueEventListener

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter
    private var historyListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = getString(R.string.history_title)
            setDisplayHomeAsUpEnabled(true)
        }

        adapter = HistoryAdapter()
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        historyListener = ParentFirebaseManager.listenHistory { sites ->
            runOnUiThread {
                adapter.submitList(sites)
                binding.tvEmpty.visibility =
                    if (sites.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        historyListener?.let { ParentFirebaseManager.removeHistoryListener(it) }
    }
}
