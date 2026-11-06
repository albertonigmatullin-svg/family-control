package com.albert.familycontrol.parent

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.albert.familycontrol.parent.adapters.DomainAdapter
import com.albert.familycontrol.parent.databinding.ActivityInternetBinding
import com.albert.familycontrol.parent.firebase.ParentFirebaseManager
import com.google.firebase.database.ValueEventListener

class InternetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInternetBinding
    private lateinit var domainAdapter: DomainAdapter
    private var configListener: ValueEventListener? = null
    private val domains = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInternetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = getString(R.string.internet_title)
            setDisplayHomeAsUpEnabled(true)
        }

        domainAdapter = DomainAdapter(domains) { domain ->
            domains.remove(domain)
            domainAdapter.notifyDataSetChanged()
            ParentFirebaseManager.setBlockedDomains(domains.toList())
        }

        binding.rvDomains.layoutManager = LinearLayoutManager(this)
        binding.rvDomains.adapter = domainAdapter

        binding.switchBlockAll.setOnCheckedChangeListener { _, isChecked ->
            ParentFirebaseManager.setBlockInternet(isChecked)
            Toast.makeText(
                this,
                if (isChecked) getString(R.string.internet_blocked) else getString(R.string.internet_unblocked),
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnAddDomain.setOnClickListener {
            showAddDomainDialog()
        }

        configListener = ParentFirebaseManager.listenConfig { blockInternet, _, blockedDomains ->
            runOnUiThread {
                binding.switchBlockAll.setOnCheckedChangeListener(null)
                binding.switchBlockAll.isChecked = blockInternet
                binding.switchBlockAll.setOnCheckedChangeListener { _, isChecked ->
                    ParentFirebaseManager.setBlockInternet(isChecked)
                    Toast.makeText(
                        this,
                        if (isChecked) getString(R.string.internet_blocked) else getString(R.string.internet_unblocked),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                domains.clear()
                domains.addAll(blockedDomains)
                domainAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun showAddDomainDialog() {
        val input = android.widget.EditText(this)
        input.hint = getString(R.string.domain_hint)
        AlertDialog.Builder(this)
            .setTitle(R.string.add_domain_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val domain = input.text.toString().trim().lowercase()
                if (domain.isNotEmpty() && !domains.contains(domain)) {
                    domains.add(domain)
                    domainAdapter.notifyDataSetChanged()
                    ParentFirebaseManager.setBlockedDomains(domains.toList())
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
