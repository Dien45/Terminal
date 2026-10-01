package com.dien.terminal

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.dien.terminal.core.rootfs.RootfsManager
import com.dien.terminal.ui.dashboard.DashboardFragment
import com.dien.terminal.ui.logs.LogListActivity
import com.dien.terminal.ui.settings.SettingsFragment
import com.dien.terminal.ui.terminal.TerminalFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var fragmentContainer: android.widget.FrameLayout
    private lateinit var setupOverlay: android.view.View

    private val dashboardFragment by lazy { DashboardFragment() }
    private val terminalFragment by lazy { TerminalFragment() }
    private val settingsFragment by lazy { SettingsFragment() }
    private var activeFragment: Fragment? = null

    private lateinit var rootfsManager: RootfsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        bottomNav = findViewById(R.id.bottom_nav)
        fragmentContainer = findViewById(R.id.fragment_container)
        setupOverlay = findViewById(R.id.setup_overlay)

        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        rootfsManager = RootfsManager(this)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> showFragment(dashboardFragment)
                R.id.nav_terminal -> showFragment(terminalFragment)
                R.id.nav_settings -> showFragment(settingsFragment)
            }
            true
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, settingsFragment, "settings").hide(settingsFragment)
                .add(R.id.fragment_container, terminalFragment, "terminal").hide(terminalFragment)
                .add(R.id.fragment_container, dashboardFragment, "dashboard")
                .commitNow()
            activeFragment = dashboardFragment
        }

        if (rootfsManager.isInstalled()) {
            showMainUi()
        } else {
            runSetup()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_logs) {
            startActivity(Intent(this, LogListActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showFragment(target: Fragment) {
        if (activeFragment === target) return
        val tx = supportFragmentManager.beginTransaction()
        activeFragment?.let { tx.hide(it) }
        tx.show(target)
        tx.commit()
        activeFragment = target
    }

    private fun showMainUi() {
        setupOverlay.visibility = android.view.View.GONE
        fragmentContainer.visibility = android.view.View.VISIBLE
        bottomNav.visibility = android.view.View.VISIBLE
    }

    fun requestRootfsReset() {
        rootfsManager.reset()
        recreate()
    }

    private fun runSetup() {
        val title: TextView = setupOverlay.findViewById(R.id.setup_title)
        val detail: TextView = setupOverlay.findViewById(R.id.setup_detail)
        val progress: ProgressBar = setupOverlay.findViewById(R.id.setup_progress_bar)
        val retryBtn = setupOverlay.findViewById<android.widget.Button>(R.id.setup_retry_button)
        retryBtn.visibility = android.view.View.GONE

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    rootfsManager.setup { p ->
                        runOnUiThread {
                            progress.progress = p.percent
                            detail.text = p.detail
                        }
                    }
                }
                showMainUi()
            } catch (e: Exception) {
                title.text = getString(R.string.app_name)
                detail.text = "Gagal menyiapkan Alpine: ${e.message}"
                retryBtn.visibility = android.view.View.VISIBLE
                retryBtn.setOnClickListener { runSetup() }
            }
        }
    }
}
