package com.dien.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

/**
 * A minimal but real terminal emulator: it spawns an actual shell process
 * (/system/bin/sh) attached to a pseudo-terminal (PTY) and renders full
 * ANSI/VT100 terminal output, exactly like a desktop terminal would.
 *
 * No root is required - it runs Android's built-in shell inside this app's
 * private sandbox, so standard commands (ls, cd, cat, echo, ps, df, mkdir,
 * ping, am, pm, ...) all work for real.
 */
class MainActivity : AppCompatActivity(), TerminalSessionClient, TerminalViewClient {

    private lateinit var terminalView: TerminalView
    private lateinit var extraKeysRow: LinearLayout

    private var terminalSession: TerminalSession? = null

    // One-shot modifier state toggled by the on-screen CTRL / ALT keys.
    private var ctrlToggled = false
    private var altToggled = false
    private var ctrlButton: Button? = null
    private var altButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        terminalView = findViewById(R.id.terminal_view)
        extraKeysRow = findViewById(R.id.extra_keys_row)

        terminalView.setTerminalViewClient(this)
        terminalView.setTextSize(42)

        buildExtraKeys()

        terminalSession = createSession()
        terminalView.attachSession(terminalSession)
        terminalView.requestFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        terminalSession?.finishIfRunning()
    }

    override fun onBackPressed() {
        // Don't kill the shell session on back press - just move the app to
        // background like a real terminal app would.
        moveTaskToBack(true)
    }

    // ---------------------------------------------------------------------
    // Session setup
    // ---------------------------------------------------------------------

    private fun createSession(): TerminalSession {
        val home = File(filesDir, "home").apply { mkdirs() }

        val shellPath = "/system/bin/sh"
        val args = arrayOf(shellPath)
        val env = buildEnv(home)

        return TerminalSession(shellPath, home.absolutePath, args, env, 4000, this)
    }

    private fun buildEnv(home: File): Array<String> {
        val systemPath = System.getenv("PATH")
            ?: "/sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin"
        return arrayOf(
            "HOME=${home.absolutePath}",
            "PATH=$systemPath",
            "TERM=xterm-256color",
            "LANG=en_US.UTF-8",
            "PS1=\$ ",
            "ANDROID_DATA=${System.getenv("ANDROID_DATA") ?: "/data"}",
            "ANDROID_ROOT=${System.getenv("ANDROID_ROOT") ?: "/system"}",
            "TMPDIR=${cacheDir.absolutePath}"
        )
    }

    // ---------------------------------------------------------------------
    // Extra keys row (ESC / TAB / CTRL / ALT / arrows / symbols / paste)
    // ---------------------------------------------------------------------

    private fun buildExtraKeys() {
        addKey("ESC") { sendString("\u001b") }
        ctrlButton = addToggleKey("CTRL") { ctrlToggled = it }
        altButton = addToggleKey("ALT") { altToggled = it }
        addKey("TAB") { sendString("\t") }
        addKey("◀") { sendString("\u001b[D") }
        addKey("▲") { sendString("\u001b[A") }
        addKey("▼") { sendString("\u001b[B") }
        addKey("▶") { sendString("\u001b[C") }
        addKey("HOME") { sendString("\u001b[H") }
        addKey("END") { sendString("\u001b[F") }
        addKey("/") { sendString("/") }
        addKey("-") { sendString("-") }
        addKey("|") { sendString("|") }
        addKey("~") { sendString("~") }
        addKey("PASTE") { pasteFromClipboard() }
    }

    private fun addKey(label: String, onClick: () -> Unit): Button {
        val button = Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.parseColor("#C9D1D9"))
            setBackgroundColor(Color.parseColor("#1C2128"))
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                marginStart = 4
                marginEnd = 4
            }
            minWidth = 0
            minimumWidth = 0
            setPadding(28, 0, 28, 0)
            setOnClickListener { onClick() }
        }
        extraKeysRow.addView(button)
        return button
    }

    private fun addToggleKey(label: String, onToggle: (Boolean) -> Unit): Button {
        var active = false
        lateinit var button: Button
        button = addKey(label) {
            active = !active
            button.setBackgroundColor(
                Color.parseColor(if (active) "#2EA043" else "#1C2128")
            )
            onToggle(active)
        }
        return button
    }

    private fun sendString(text: String) {
        terminalSession?.write(text)
        terminalView.onScreenUpdated()
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this)?.toString()
            if (!text.isNullOrEmpty()) sendString(text)
        }
    }

    private fun resetOneShotModifiers() {
        if (ctrlToggled) {
            ctrlToggled = false
            ctrlButton?.setBackgroundColor(Color.parseColor("#1C2128"))
        }
        if (altToggled) {
            altToggled = false
            altButton?.setBackgroundColor(Color.parseColor("#1C2128"))
        }
    }

    // ---------------------------------------------------------------------
    // TerminalViewClient
    // ---------------------------------------------------------------------

    override fun onScale(scale: Float): Float {
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent?) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(terminalView, 0)
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = true

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false

    override fun onLongPress(event: MotionEvent?): Boolean = false

    override fun readControlKey(): Boolean = ctrlToggled

    override fun readAltKey(): Boolean = altToggled

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
        resetOneShotModifiers()
        return false
    }

    override fun onEmulatorSet() {}

    override fun logError(tag: String?, message: String?) {}
    override fun logWarn(tag: String?, message: String?) {}
    override fun logInfo(tag: String?, message: String?) {}
    override fun logDebug(tag: String?, message: String?) {}
    override fun logVerbose(tag: String?, message: String?) {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    override fun logStackTrace(tag: String?, e: Exception?) {}

    // ---------------------------------------------------------------------
    // TerminalSessionClient
    // ---------------------------------------------------------------------

    override fun onTextChanged(changedSession: TerminalSession) {
        terminalView.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        title = changedSession.title ?: getString(R.string.app_name)
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        runOnUiThread {
            Toast.makeText(this, "Shell exited", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        if (text.isNullOrEmpty()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        pasteFromClipboard()
    }

    override fun onBell(session: TerminalSession) {}

    override fun onColorsChanged(session: TerminalSession) {}

    override fun onTerminalCursorStateChange(state: Boolean) {}

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

    override fun getTerminalCursorStyle(): Int? = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
}
