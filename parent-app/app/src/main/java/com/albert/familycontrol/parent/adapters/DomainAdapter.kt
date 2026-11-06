package com.albert.familycontrol.parent.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.albert.familycontrol.parent.databinding.ItemHistoryBinding

class DomainAdapter(
    private val domains: MutableList<String>,
    private val onRemove: (String) -> Unit
) : RecyclerView.Adapter<DomainAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(domain: String) {
            binding.tvDomain.text = domain
            binding.tvTime.text = ""
            binding.root.setOnLongClickListener {
                onRemove(domain)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(domains[position])
    }

    override fun getItemCount() = domains.size
}
