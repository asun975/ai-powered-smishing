package com.example.smishingdetection

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smishingdetection.data.AnalyzedMessage
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class SuspiciousMessagesActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private lateinit var emptyView: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var db: DatabaseHelper

    private var currentTab = "caution"  // "caution" or "quarantined"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_suspicious_messages)

        supportActionBar?.apply {
            title = "AI Smishing"
            setDisplayHomeAsUpEnabled(true)
        }

        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.tvEmpty)
        tabLayout = findViewById(R.id.tabLayout)

        db = DatabaseHelper(application) // get database instance
        adapter = MessageAdapter(
            mutableListOf(),
            onItemClick = { msg -> openDetail(msg) },
            onMenuClick = { msg, anchor -> showPopupMenu(msg, anchor) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        tabLayout.addTab(tabLayout.newTab().setText("Caution"))
        tabLayout.addTab(tabLayout.newTab().setText("Quarantine"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = if (tab.position == 0) "caution" else "quarantined"
                loadMessages()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        loadMessages()
    }

    override fun onResume() {
        super.onResume()
        loadMessages()
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            db.getByStatus(currentTab).collect { messages ->
                adapter.updateData(messages)
                if (messages.isEmpty()) {
                    // show empty state
                    recyclerView.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                    emptyView.text = if (currentTab == "caution")
                        "No caution messages" else "No quarantined messages"
                } else {
                    // show data
                    recyclerView.visibility = View.VISIBLE
                    emptyView.visibility = View.GONE
                }
            }
        }
    }

    private fun openDetail(msg: AnalyzedMessage) {
        val intent = Intent(this, MessageDetailActivity::class.java).apply {
            putExtra("phone", msg.phoneNumber)
            putExtra("date", msg.date)
            putExtra("message", msg.message)
            putExtra("risk_score", msg.riskScore)
            putExtra("status", msg.status)
            putExtra("explanation", msg.explanation)
            putExtra("id", msg.id)
            putExtra("url_scan_result", msg.urlScanResult ?: "")
        }
        startActivity(intent)
    }


    private fun showPopupMenu(msg: AnalyzedMessage, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Detail")
        popup.menu.add(0, 2, 1, if (currentTab == "caution") "Quarantine" else "Mark Safe")

        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                1 -> openDetail(msg)
                2 -> {
                    val id = msg.id
                    if (currentTab == "caution") {
                        lifecycleScope.launch {
                            db.quarantineMessage(id)
                        }
                        Toast.makeText(this, "AnalyzedMessage moved to quarantine!", Toast.LENGTH_SHORT).show()
                    } else {
                        lifecycleScope.launch {
                            db.markAsSafe(id)
                        }
                        Toast.makeText(this, "AnalyzedMessage marked as safe", Toast.LENGTH_SHORT).show()
                    }
                    loadMessages()
                }
            }
            true
        }
        popup.show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}