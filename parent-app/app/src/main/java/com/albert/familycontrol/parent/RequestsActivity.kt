package com.albert.familycontrol.parent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.albert.familycontrol.parent.databinding.ActivityRequestsBinding
import com.albert.familycontrol.parent.firebase.ParentFirebaseManager
import com.albert.familycontrol.parent.models.TimeRequest
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RequestsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRequestsBinding
    private lateinit var adapter: RequestsAdapter
    private var requestsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRequestsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Запросы от Льва"

        adapter = RequestsAdapter(
            onApprove = { request ->
                ParentFirebaseManager.approveRequest(request)
                Toast.makeText(this, "✅ +${request.requestedMinutes} мин одобрено", Toast.LENGTH_SHORT).show()
            },
            onDeny = { request ->
                ParentFirebaseManager.denyRequest(request.key)
                Toast.makeText(this, "Отклонено", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvRequests.layoutManager = LinearLayoutManager(this)
        binding.rvRequests.adapter = adapter

        requestsListener = ParentFirebaseManager.listenRequests { requests ->
            runOnUiThread {
                adapter.updateRequests(requests)
                binding.tvEmpty.visibility = if (requests.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        requestsListener?.let { ParentFirebaseManager.removeRequestsListener(it) }
    }
}

class RequestsAdapter(
    private val onApprove: (TimeRequest) -> Unit,
    private val onDeny: (TimeRequest) -> Unit
) : RecyclerView.Adapter<RequestsAdapter.VH>() {

    private val items = mutableListOf<TimeRequest>()
    private val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun updateRequests(requests: List<TimeRequest>) {
        items.clear()
        items.addAll(requests)
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvAppName: TextView = view.findViewById(R.id.tvRequestAppName)
        val tvTime: TextView = view.findViewById(R.id.tvRequestTime)
        val tvStatus: TextView = view.findViewById(R.id.tvRequestStatus)
        val btnApprove: Button = view.findViewById(R.id.btnApprove)
        val btnDeny: Button = view.findViewById(R.id.btnDeny)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_request, parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val req = items[position]
        holder.tvAppName.text = "${req.appName.ifEmpty { req.packageName }} — +${req.requestedMinutes} мин"
        holder.tvTime.text = sdf.format(Date(req.ts))
        when (req.status) {
            "approved" -> {
                holder.tvStatus.text = "✅ Одобрено"
                holder.tvStatus.setTextColor(0xFF22C55E.toInt())
                holder.btnApprove.visibility = View.GONE
                holder.btnDeny.visibility = View.GONE
            }
            "denied" -> {
                holder.tvStatus.text = "❌ Отклонено"
                holder.tvStatus.setTextColor(0xFFEF4444.toInt())
                holder.btnApprove.visibility = View.GONE
                holder.btnDeny.visibility = View.GONE
            }
            else -> {
                holder.tvStatus.text = "⏳ Ожидает"
                holder.tvStatus.setTextColor(0xFFF59E0B.toInt())
                holder.btnApprove.visibility = View.VISIBLE
                holder.btnDeny.visibility = View.VISIBLE
                holder.btnApprove.setOnClickListener { onApprove(req) }
                holder.btnDeny.setOnClickListener { onDeny(req) }
            }
        }
    }
}
