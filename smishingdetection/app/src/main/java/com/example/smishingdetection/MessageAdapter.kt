package com.example.smishingdetection

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter(
    private val messages: MutableList<Map<String, String>>,
    private val onItemClick: (Map<String, String>) -> Unit,
    private val onMenuClick: (Map<String, String>, View) -> Unit
) : RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateText: TextView = view.findViewById(R.id.tvDate)
        val phoneText: TextView = view.findViewById(R.id.tvPhone)
        val messageText: TextView = view.findViewById(R.id.tvMessage)
        val riskScore: TextView = view.findViewById(R.id.tvRiskScore)
        val menuButton: ImageButton = view.findViewById(R.id.btnMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = messages[position]

        // Shorten date from "2026-07-10 14:13:09" to "Jul 10, 2:13 PM"
        val rawDate = msg[DatabaseHelper.COL_DATE] ?: ""
        holder.dateText.text = formatDate(rawDate)

        // Phone number
        holder.phoneText.text = msg[DatabaseHelper.COL_PHONE] ?: "Unknown"

        // Message preview
        holder.messageText.text = msg[DatabaseHelper.COL_MESSAGE] ?: ""

        // Risk score + badge color
        val score = msg[DatabaseHelper.COL_RISK_SCORE]?.toDoubleOrNull() ?: 0.0
        val status = msg[DatabaseHelper.COL_STATUS] ?: DatabaseHelper.STATUS_CAUTION

        if (status == DatabaseHelper.STATUS_PENDING) {
            holder.riskScore.text = "..."
        } else {
            holder.riskScore.text = String.format("%.0f%%", score)
        }

        val badgeColor = when (status) {
            DatabaseHelper.STATUS_QUARANTINED -> Color.parseColor("#E53935") // red
            DatabaseHelper.STATUS_CAUTION -> Color.parseColor("#FB8C00") // orange
            DatabaseHelper.STATUS_PENDING -> Color.parseColor("#757575") // gray
            else -> Color.parseColor("#43A047") // green
        }

        val drawable = androidx.core.content.ContextCompat.getDrawable(
            holder.itemView.context, R.drawable.circle_badge
        )?.mutate()
        drawable?.setTint(badgeColor)
        holder.riskScore.background = drawable

        holder.itemView.setOnClickListener { onItemClick(msg) }
        holder.menuButton.setOnClickListener { onMenuClick(msg, holder.menuButton) }
    }

    override fun getItemCount() = messages.size

    fun updateData(newMessages: List<Map<String, String>>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    private fun formatDate(raw: String): String {
        return try {
            val inputFormat = java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()
            )
            val outputFormat = java.text.SimpleDateFormat(
                "MMM d, h:mm a", java.util.Locale.getDefault()
            )
            val date = inputFormat.parse(raw)
            if (date != null) outputFormat.format(date) else raw
        } catch (e: Exception) {
            raw
        }
    }
}