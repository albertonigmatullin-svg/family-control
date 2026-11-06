package com.albert.familycontrol.parent.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.albert.familycontrol.parent.databinding.ItemAppBinding

class AppListAdapter(
    private val apps: List<Pair<String, String>>,
    initialBlocked: Set<String>,
    private val onToggle: (packageName: String, blocked: Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    var blockedApps: Set<String> = initialBlocked
        private set

    fun updateBlocked(blocked: Set<String>) {
        blockedApps = blocked
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemAppBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(packageName: String, appName: String) {
            binding.tvAppName.text = appName
            binding.tvPackageName.text = packageName
            binding.switchBlock.setOnCheckedChangeListener(null)
            binding.switchBlock.isChecked = blockedApps.contains(packageName)
            binding.switchBlock.setOnCheckedChangeListener { _, isChecked ->
                onToggle(packageName, isChecked)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (pkg, name) = apps[position]
        holder.bind(pkg, name)
    }

    override fun getItemCount() = apps.size
}
