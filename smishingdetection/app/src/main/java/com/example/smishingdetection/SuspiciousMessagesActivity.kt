package com.example.smishingdetection

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import android.widget.ImageButton
import androidx.core.view.WindowCompat
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.telecom.TelecomManager

class SuspiciousMessagesActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private lateinit var emptyView: View
    private lateinit var tabLayout: TabLayout

    private var currentTab = "caution"  // "caution" or "quarantined"

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_suspicious_messages)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        supportActionBar?.apply {
            title = "AI Smishing"
            setDisplayHomeAsUpEnabled(true)
        }

        db = DatabaseHelper(this)

        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.tvEmpty)
        tabLayout = findViewById(R.id.tabLayout)

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
        val messages = db.getByStatus(currentTab)
        adapter.updateData(messages)

        if (messages.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }

    private fun openDetail(msg: Map<String, String>) {
        val intent = Intent(this, MessageDetailActivity::class.java).apply {
            putExtra("phone", msg[DatabaseHelper.COL_PHONE])
            putExtra("date", msg[DatabaseHelper.COL_DATE])
            putExtra("message", msg[DatabaseHelper.COL_MESSAGE])
            putExtra("risk_score", msg[DatabaseHelper.COL_RISK_SCORE])
            putExtra("status", msg[DatabaseHelper.COL_STATUS])
            putExtra("explanation", msg[DatabaseHelper.COL_EXPLANATION])
            putExtra("id", msg[DatabaseHelper.COL_ID])
            putExtra("url_scan_result", msg[DatabaseHelper.COL_URL_SCAN] ?: "")
        }
        startActivity(intent)
    }


    private fun showPopupMenu(msg: Map<String, String>, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Detail")
        if (currentTab == "caution") {
            popup.menu.add(0, 2, 1, "Quarantine")
        } else {
            popup.menu.add(0, 4, 3, "Block")
        }
        popup.menu.add(0, 3, 2, "Mark Safe")

        popup.setOnMenuItemClickListener { item: MenuItem ->
            val id = msg[DatabaseHelper.COL_ID]?.toLongOrNull() ?: -1
            when (item.itemId) {
                1 -> openDetail(msg)
                2 -> {
                    db.updateStatus(id, "quarantined")
                    Toast.makeText(this, "Message quarantined", Toast.LENGTH_SHORT).show()
                    loadMessages()
                }

                3 -> {
                    db.deleteMessage(id)
                    Toast.makeText(this, "Message marked as safe", Toast.LENGTH_SHORT).show()
                    loadMessages()
                }

                4 -> {
                    val phone = msg[DatabaseHelper.COL_PHONE] ?: ""
                    db.blockSender(phone)
                    blockNumber(phone)
                }
            }
            true
        }
        popup.show()
    }

    private fun blockNumber(phoneNumber: String) {
        AlertDialog.Builder(this)
            .setTitle("Block Number")
            .setMessage("$phoneNumber will be copied to your clipboard. You can paste it into the block list that opens.")
            .setPositiveButton("Open Block List") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("phone number", phoneNumber)
                clipboard.setPrimaryClip(clip)

                val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
                val blockIntent = telecomManager.createManageBlockedNumbersIntent()
                startActivity(blockIntent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}