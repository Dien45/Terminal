package com.dien.terminal.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.dien.terminal.R
import com.dien.terminal.core.backup.BackupRestoreService
import com.dien.terminal.core.pm.PackageManagerOps
import com.dien.terminal.core.rootfs.ProotLauncher
import com.dien.terminal.core.rootfs.RootfsManager
import com.dien.terminal.ui.logs.LogListActivity
import com.dien.terminal.util.DeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardFragment : Fragment() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var infoCpu: TextView
    private lateinit var infoRam: TextView
    private lateinit var infoDisk: TextView
    private lateinit var maintenanceProgress: ProgressBar
    private lateinit var searchInput: EditText
    private lateinit var searchProgress: ProgressBar
    private lateinit var searchResults: RecyclerView
    private lateinit var adapter: PackageResultAdapter

    private val backupFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) startBackup(uri)
    }
    private val restoreFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) startRestore(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        infoCpu = view.findViewById(R.id.info_cpu)
        infoRam = view.findViewById(R.id.info_ram)
        infoDisk = view.findViewById(R.id.info_disk)
        maintenanceProgress = view.findViewById(R.id.maintenance_progress)
        searchInput = view.findViewById(R.id.search_input)
        searchProgress = view.findViewById(R.id.search_progress)
        searchResults = view.findViewById(R.id.search_results)

        adapter = PackageResultAdapter { pkg -> installPackage(pkg.name) }
        searchResults.layoutManager = LinearLayoutManager(requireContext())
        searchResults.adapter = adapter

        swipeRefresh.setOnRefreshListener { refreshSystemInfo() }

        view.findViewById<android.widget.Button>(R.id.btn_update).setOnClickListener { runMaintenance("update") }
        view.findViewById<android.widget.Button>(R.id.btn_upgrade).setOnClickListener { runMaintenance("upgrade") }
        view.findViewById<android.widget.Button>(R.id.btn_fix).setOnClickListener { runMaintenance("fix") }
        view.findViewById<android.widget.Button>(R.id.btn_backup).setOnClickListener { backupFolderPicker.launch(null) }
        view.findViewById<android.widget.Button>(R.id.btn_restore).setOnClickListener {
            restoreFilePicker.launch(arrayOf("application/gzip", "*/*"))
        }
        view.findViewById<android.widget.Button>(R.id.btn_view_logs).setOnClickListener {
            startActivity(Intent(requireContext(), LogListActivity::class.java))
        }
        view.findViewById<ImageButton>(R.id.btn_search).setOnClickListener { performSearch() }
        searchInput.setOnEditorActionListener { _, _, _ -> performSearch(); true }

        refreshSystemInfo()
    }

    private fun refreshSystemInfo() {
        val snap = DeviceInfo.snapshot(requireContext())
        infoCpu.text = "${getString(R.string.dashboard_cpu)}: ${snap.cpuAbi} (${snap.cpuCores} core)"
        infoRam.text = "${getString(R.string.dashboard_ram)}: ${snap.ramUsedMb} / ${snap.ramTotalMb} MB"
        infoDisk.text = "${getString(R.string.dashboard_disk)}: ${snap.diskUsedMb} / ${snap.diskTotalMb} MB"
        swipeRefresh.isRefreshing = false
    }

    private fun opsOrNull(): PackageManagerOps? {
        val rootfsManager = RootfsManager(requireContext())
        if (!rootfsManager.isInstalled()) {
            Toast.makeText(requireContext(), "Rootfs Alpine belum siap", Toast.LENGTH_SHORT).show()
            return null
        }
        return PackageManagerOps(requireContext(), ProotLauncher(requireContext(), rootfsManager))
    }

    private fun runMaintenance(action: String) {
        val ops = opsOrNull() ?: return
        maintenanceProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                when (action) {
                    "update" -> ops.update()
                    "upgrade" -> ops.upgrade()
                    else -> ops.fix()
                }
            }
            maintenanceProgress.visibility = View.GONE
            val msg = if (action == "fix") {
                "Fix selesai - Diperbaiki: ${outcome.fixed}, Gagal: ${outcome.failed}"
            } else {
                if (outcome.success) "$action berhasil" else "$action gagal"
            }
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun performSearch() {
        val term = searchInput.text.toString().trim()
        if (term.isEmpty()) return
        val ops = opsOrNull() ?: return
        searchProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) { ops.search(term) }
            searchProgress.visibility = View.GONE
            adapter.submit(results)
            if (results.isEmpty()) {
                Toast.makeText(requireContext(), "Tidak ada paket ditemukan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun installPackage(name: String) {
        val ops = opsOrNull() ?: return
        Toast.makeText(requireContext(), "Menginstal $name...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { ops.install(name) }
            Toast.makeText(
                requireContext(),
                if (outcome.success) "$name berhasil diinstal" else "Gagal menginstal $name",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun startBackup(destTree: Uri) {
        requireContext().grantAndPersist(destTree)
        val intent = Intent(requireContext(), BackupRestoreService::class.java).apply {
            action = BackupRestoreService.ACTION_BACKUP
            putExtra(BackupRestoreService.EXTRA_URI, destTree)
        }
        androidx.core.content.ContextCompat.startForegroundService(requireContext(), intent)
        Toast.makeText(requireContext(), "Backup dimulai di latar belakang", Toast.LENGTH_SHORT).show()
    }

    private fun startRestore(fileUri: Uri) {
        val intent = Intent(requireContext(), BackupRestoreService::class.java).apply {
            action = BackupRestoreService.ACTION_RESTORE
            putExtra(BackupRestoreService.EXTRA_URI, fileUri)
        }
        androidx.core.content.ContextCompat.startForegroundService(requireContext(), intent)
        Toast.makeText(requireContext(), "Restore dimulai di latar belakang", Toast.LENGTH_SHORT).show()
    }

    private fun android.content.Context.grantAndPersist(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }
}
