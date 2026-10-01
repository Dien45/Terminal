package com.dien.terminal.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.dien.terminal.R
import com.dien.terminal.core.rootfs.ProotLauncher
import com.dien.terminal.core.rootfs.RootfsManager
import com.dien.terminal.util.Prefs
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/** The Terminal tab (FR-3): a real PTY-backed shell running inside the Alpine/proot rootfs. */
class TerminalFragment : Fragment(), TerminalSessionClient, TerminalViewClient {

    private lateinit var terminalView: TerminalView
    private lateinit var extraKeysRow: LinearLayout
    private var terminalSession: TerminalSession? = null

    private var ctrlToggled = false
    private var altToggled = false
    private var ctrlButton: Button? = null
    private var altButton: Button? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_terminal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        terminalView = view.findViewById(R.id.terminal_view)
        extraKeysRow = view.findViewById(R.id.extra_keys_row)

        terminalView.setTerminalViewClient(this)
        terminalView.setTextSize(spToPx(Prefs.get(requireContext()).terminalFontSize))

        buildExtraKeys()

        if (terminalSession == null) {
            terminalSession = startSession()
        }
        terminalView.attachSession(terminalSession)
    }

    override fun onResume() {
        super.onResume()
        terminalView.requestFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (requireActivity().isFinishing) {
            terminalSession?.finishIfRunning()
        }
    }

    private fun spToPx(sp: Int): Int {
        val density = resources.displayMetrics.scaledDensity
        return (sp * density).toInt()
    }

    private fun startSession(): TerminalSession? {
        val rootfsManager = RootfsManager(requireContext())
        if (!rootfsManager.isInstalled()) {
            Toast.makeText(requireContext(), "Rootfs Alpine belum siap", Toast.LENGTH_SHORT).show()
            return null
        }
        val launcher = ProotLauncher(requireContext(), rootfsManager)
        return try {
            launcher.createInteractiveSession(this)
        } catch (e: ProotLauncher.UnsupportedDeviceException) {
            Toast.makeText(requireContext(), e.message, Toast.LENGTH_LONG).show()
            null
        }
    }

    // ---------------------------------------------------------------------
    // Extra keys row
    // ---------------------------------------------------------------------

    private fun buildExtraKeys() {
        extraKeysRow.removeAllViews()
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
        val button = Button(requireContext()).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.parseColor("#E3F2FD"))
            setBackgroundColor(Color.parseColor("#13233B"))
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { marginStart = 4; marginEnd = 4 }
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
            button.setBackgroundColor(Color.parseColor(if (active) "#1565C0" else "#13233B"))
            onToggle(active)
        }
        return button
    }

    private fun sendString(text: String) {
        terminalSession?.write(text)
        terminalView.onScreenUpdated()
    }

    private fun pasteFromClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(requireContext())?.toString()
            if (!text.isNullOrEmpty()) sendString(text)
        }
    }

    private fun resetOneShotModifiers() {
        if (ctrlToggled) {
            ctrlToggled = false
            ctrlButton?.setBackgroundColor(Color.parseColor("#13233B"))
        }
        if (altToggled) {
            altToggled = false
            altButton?.setBackgroundColor(Color.parseColor("#13233B"))
        }
    }

    // ---------------------------------------------------------------------
    // TerminalViewClient
    // ---------------------------------------------------------------------

    override fun onScale(scale: Float): Float = scale

    override fun onSingleTapUp(e: MotionEvent?) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
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

    override fun onTitleChanged(changedSession: TerminalSession) {}

    override fun onSessionFinished(finishedSession: TerminalSession) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), "Sesi shell berakhir", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        if (text.isNullOrEmpty()) return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        pasteFromClipboard()
    }

    override fun onBell(session: TerminalSession) {}
    override fun onColorsChanged(session: TerminalSession) {}
    override fun onTerminalCursorStateChange(state: Boolean) {}
    override fun getTerminalCursorStyle(): Int? = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
}
