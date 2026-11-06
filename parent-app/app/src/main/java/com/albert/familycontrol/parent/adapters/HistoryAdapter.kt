package com.albert.familycontrol.parent.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.albert.familycontrol.parent.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val items = mutableListOf<Pair<String, Long>>()

    fun submitList(list: List<Pair<String, Long>>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(domain: String, ts: Long) {
            binding.tvDomain.text = domain
            val sdf = SimpleDateFormat("HH:mm dd.MM.yy", Locale.getDefault())
            binding.tvTime.text = sdf.format(Date(ts))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (domain, ts) = items[position]
        holder.bind(domain, ts)
    }

    override fun getItemCount() = items.size
}
