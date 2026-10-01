package com.dien.terminal.ui.logs

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.dien.terminal.R
import com.dien.terminal.core.logging.AppDatabase
import com.dien.terminal.core.logging.LogEntry
import com.dien.terminal.core.logging.LogLineClassifier
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Detail screen for one logged operation, with error/warning highlighting (FR-13). */
class LogDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
        const val EXTRA_PROCESS_ID = "process_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_detail)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val summaryView = findViewById<android.widget.TextView>(R.id.detail_summary)
        val outputView = findViewById<android.widget.TextView>(R.id.detail_output)

        val entryId = intent.getLongExtra(EXTRA_ENTRY_ID, -1L)
        val processId = intent.getStringExtra(EXTRA_PROCESS_ID)

        lifecycleScope.launch {
            val dao = AppDatabase.get(this@LogDetailActivity).logEntryDao()
            val entry: LogEntry? = withContext(Dispatchers.IO) {
                if (entryId >= 0) dao.getById(entryId) else processId?.let { dao.getByProcessId(it) }
            }
            if (entry == null) {
                toolbar.title = getString(R.string.log_detail_title)
                summaryView.text = "Log tidak ditemukan"
                return@launch
            }
            toolbar.title = entry.title
            summaryView.text = "${entry.type} • ${entry.status}\n${entry.summary}"

            val rawText = withContext(Dispatchers.IO) {
                runCatching { File(entry.logFilePath).readText() }.getOrDefault("(tidak ada output)")
            }
            outputView.text = highlight(rawText)
        }
    }

    private fun highlight(text: String): CharSequence {
        val builder = SpannableStringBuilder()
        text.lines().forEach { line ->
            val start = builder.length
            builder.append(line).append('\n')
            val color = when (LogLineClassifier.classify(line)) {
                LogLineClassifier.Level.ERROR -> R.color.log_error
                LogLineClassifier.Level.WARNING -> R.color.log_warning
                LogLineClassifier.Level.SUCCESS -> R.color.log_success
                LogLineClassifier.Level.NORMAL -> null
            }
            if (color != null) {
                builder.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(this, color)),
                    start, builder.length, 0
                )
            }
        }
        return builder
    }
}
