package com.example.smishingdetection.ui.quarantine

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smishingdetection.R
import com.example.smishingdetection.data.local.model.AnalyzedMessage
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class SuspiciousMessagesActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private lateinit var emptyView: TextView
    private lateinit var tabLayout: TabLayout
    private val viewModel: SuspiciousMessagesViewModel by viewModels {
        SuspiciousMessagesViewModel.Factory
    }
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

        adapter = MessageAdapter(
            onItemClick = ::openDetail,
            onMenuClick = ::showPopupMenu
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch{
                    viewModel.suspiciousMessageUiState.collect { state->
                        val tab = state.selectedTab
                        viewModel.loadMessages(tab)
                        state.messages.collect { messages ->
                            adapter.submitList(messages)
                        }
                    }
                }
                launch {
                     viewModel.toastEvent.collect { message ->
                        Toast.makeText(this@SuspiciousMessagesActivity, message, Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when(tab.position) {
                    0 -> viewModel.selectTab(MessageTab.CAUTION)
                    1 -> viewModel.selectTab(MessageTab.QUARANTINE)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        // Load initial tab
        viewModel.selectTab(MessageTab.CAUTION)
    }

    private fun openDetail(msg: AnalyzedMessage) {

        val intent = Intent(this, MessageDetailActivity::class.java).apply {
            putExtra("id", msg.id)
        }
        startActivity(intent)
    }


    private fun showPopupMenu(msg: AnalyzedMessage, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Detail")
        popup.menu.add(0, 2, 1, if (currentTab == "caution") "Quarantine" else "Mark Safe")

        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                1 -> {
                    openDetail(msg)
                    true
                }

                2 -> {
                    viewModel.moveTabs(msg.id)
                    }
                else -> {

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