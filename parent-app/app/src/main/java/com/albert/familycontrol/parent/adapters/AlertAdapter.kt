package com.albert.familycontrol.parent.adapters

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.albert.familycontrol.parent.databinding.ItemAlertBinding
import com.albert.familycontrol.parent.models.Alert
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertAdapter : RecyclerView.Adapter<AlertAdapter.ViewHolder>() {

    private val items = mutableListOf<Alert>()

    fun submitList(list: List<Alert>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemAlertBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(alert: Alert) {
            binding.tvAlertTitle.text = alert.title
            binding.tvAlertBody.text = alert.body
            val sdf = SimpleDateFormat("HH:mm dd.MM.yy", Locale.getDefault())
            binding.tvAlertTime.text = sdf.format(Date(alert.ts))

            if (!alert.image.isNullOrEmpty()) {
                try {
                    val bytes = Base64.decode(alert.image, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    binding.ivAlertImage.setImageBitmap(bitmap)
                    binding.ivAlertImage.visibility = View.VISIBLE
                } catch (e: Exception) {
                    binding.ivAlertImage.visibility = View.GONE
                }
            } else {
                binding.ivAlertImage.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAlertBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size
}
