package com.dien.terminal.ui.settings

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.dien.terminal.MainActivity
import com.dien.terminal.R
import com.dien.terminal.core.logging.AppDatabase
import com.dien.terminal.core.logging.OperationLogger
import com.dien.terminal.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Settings tab (FR-15): font size, log retention, log export/clear (FR-14), reset rootfs. */
class SettingsFragment : Fragment() {

    private lateinit var prefs: Prefs

    private val exportLocationPicker =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) exportLogsTo(uri)
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs.get(requireContext())

        val fontSeek = view.findViewById<SeekBar>(R.id.seek_font_size)
        val fontValue = view.findViewById<TextView>(R.id.font_size_value)
        fontSeek.max = 20
        fontSeek.progress = (prefs.terminalFontSize - 8).coerceIn(0, 20)
        fontValue.text = "${prefs.terminalFontSize} sp"
        fontSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sp = progress + 8
                fontValue.text = "$sp sp"
                prefs.terminalFontSize = sp
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val retentionSeek = view.findViewById<SeekBar>(R.id.seek_log_retention)
        val retentionValue = view.findViewById<TextView>(R.id.log_retention_value)
        retentionSeek.max = 30
        retentionSeek.progress = prefs.logRetentionDays.coerceIn(0, 30)
        retentionValue.text = "${prefs.logRetentionDays} hari"
        retentionSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val days = progress.coerceAtLeast(1)
                retentionValue.text = "$days hari"
                prefs.logRetentionDays = days
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        view.findViewById<android.widget.Button>(R.id.btn_export_logs).setOnClickListener {
            exportLocationPicker.launch("terminal-logs-export.txt")
        }
        view.findViewById<android.widget.Button>(R.id.btn_clear_logs).setOnClickListener { confirmClearLogs() }
        view.findViewById<android.widget.Button>(R.id.btn_reset_rootfs).setOnClickListener { confirmResetRootfs() }
    }

    private fun confirmClearLogs() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_clear_logs)
            .setMessage("Semua riwayat log akan dihapus permanen. Lanjutkan?")
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { OperationLogger(requireContext()).clearAll() }
                    Toast.makeText(requireContext(), "Log dihapus", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmResetRootfs() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_reset_rootfs)
            .setMessage("Seluruh sistem Alpine Linux akan dihapus dan diunduh ulang dari awal. Lanjutkan?")
            .setPositiveButton("Reset") { _, _ -> (activity as? MainActivity)?.requestRootfsReset() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun exportLogsTo(uri: Uri) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val dao = AppDatabase.get(requireContext()).logEntryDao()
                val entries = dao.getAll()
                val sb = StringBuilder()
                entries.forEach { e ->
                    sb.appendLine("==== ${e.type} | ${e.title} | ${e.status} ====")
                    sb.appendLine("Mulai: ${e.startTime}  Selesai: ${e.endTime}")
                    sb.appendLine("Ringkasan: ${e.summary}")
                    runCatching { sb.appendLine(File(e.logFilePath).readText()) }
                    sb.appendLine()
                }
                requireContext().contentResolver.openOutputStream(uri)?.use {
                    it.write(sb.toString().toByteArray())
                }
            }
            Toast.makeText(requireContext(), "Log berhasil diekspor", Toast.LENGTH_SHORT).show()
        }
    }
}
