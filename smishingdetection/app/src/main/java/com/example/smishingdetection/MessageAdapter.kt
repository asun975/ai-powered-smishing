package com.example.smishingdetection

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

        holder.dateText.text = msg[DatabaseHelper.COL_DATE] ?: ""
        holder.messageText.text = msg[DatabaseHelper.COL_MESSAGE] ?: ""

        val score = msg[DatabaseHelper.COL_RISK_SCORE]?.toDoubleOrNull() ?: 0.0
        holder.riskScore.text = String.format("%.0f%%", score)

        holder.itemView.setOnClickListener { onItemClick(msg) }
        holder.menuButton.setOnClickListener { onMenuClick(msg, holder.menuButton) }
    }

    override fun getItemCount() = messages.size

    fun updateData(newMessages: List<Map<String, String>>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }
}