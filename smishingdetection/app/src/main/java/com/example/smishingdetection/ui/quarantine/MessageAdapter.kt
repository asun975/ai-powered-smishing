package com.example.smishingdetection.ui.quarantine

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smishingdetection.R
import com.example.smishingdetection.data.local.model.AnalyzedMessage

class MessageAdapter(
    private val onItemClick: (AnalyzedMessage) -> Unit,
    private val onMenuClick: (AnalyzedMessage, View) -> Unit
) : RecyclerView.Adapter<MessageAdapter.ViewHolder>() {
    private val messages = mutableListOf<AnalyzedMessage>()

    fun submitList(items: List<AnalyzedMessage>) {
        messages.clear()
        messages.addAll(items)
        notifyDataSetChanged()
    }
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

        holder.dateText.text = msg.date
        holder.messageText.text = msg.message

        val score = msg.riskScore
        holder.riskScore.text = String.format("%.0f%%", score)

        holder.itemView.setOnClickListener { onItemClick(msg) }
        holder.menuButton.setOnClickListener { onMenuClick(msg, holder.menuButton) }
    }

    override fun getItemCount() = messages.count()

}