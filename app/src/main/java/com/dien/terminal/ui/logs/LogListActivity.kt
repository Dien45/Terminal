package com.dien.terminal.ui.logs

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dien.terminal.R
import com.dien.terminal.core.logging.AppDatabase
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

/** System Log Viewer (FR-12): lists every logged operation (install/update/upgrade/fix/backup/restore). */
class LogListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_list)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.log_viewer_title)
        toolbar.setNavigationOnClickListener { finish() }

        val list = findViewById<RecyclerView>(R.id.log_list)
        val empty = findViewById<View>(R.id.empty_view)
        val adapter = LogAdapter { entry ->
            startActivity(
                Intent(this, LogDetailActivity::class.java)
                    .putExtra(LogDetailActivity.EXTRA_ENTRY_ID, entry.id)
            )
        }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        val dao = AppDatabase.get(this).logEntryDao()
        lifecycleScope.launch {
            dao.observeAll().collect { entries ->
                adapter.submit(entries)
                empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
                list.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
}
