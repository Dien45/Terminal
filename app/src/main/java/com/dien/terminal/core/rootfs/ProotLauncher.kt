package com.dien.terminal.core.rootfs

import android.content.Context
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Builds proot invocations that drop the caller into (or run a single
 * command inside) the Alpine rootfs managed by [RootfsManager] - FR-1.
 *
 * The `proot` executable is shipped as a per-ABI native library
 * (`app/src/main/jniLibs/<abi>/libproot.so`) so that Android's APK installer
 * extracts it with the executable bit set, which is otherwise unavailable
 * for arbitrary files placed under internal storage on modern Android.
 *
 * There are two distinct environments at play here, which must not be mixed up:
 *  - the HOST environment of the `proot` process itself (needs PROOT_TMP_DIR
 *    so proot has a writable+executable place to unpack its embedded loader);
 *  - the GUEST environment of the shell running *inside* the Alpine rootfs
 *    (HOME, PATH, TERM, ...), set up via `/usr/bin/env -i` so none of
 *    Android's own (incompatible) environment variables leak into Alpine.
 */
class ProotLauncher(private val context: Context, private val rootfsManager: RootfsManager) {

    class UnsupportedDeviceException(message: String) : Exception(message)

    private fun prootBinary(): File {
        val lib = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
        if (!lib.exists()) {
            throw UnsupportedDeviceException(
                "Tidak ada build proot untuk arsitektur perangkat ini (${android.os.Build.SUPPORTED_ABIS.joinToString()})."
            )
        }
        return lib
    }

    /** Real process environment for the `proot` binary itself (NOT for the guest shell). */
    private fun hostEnv(): Array<String> {
        val tmp = File(context.cacheDir, "proot-tmp").apply { mkdirs() }
        val l2s = File(context.cacheDir, "proot-l2s").apply { mkdirs() }
        return arrayOf(
            "PROOT_TMP_DIR=${tmp.absolutePath}",
            "PROOT_L2S_DIR=${l2s.absolutePath}",
            "TMPDIR=${tmp.absolutePath}"
        )
    }

    /** Clean environment handed to the guest shell inside Alpine via `env -i`. */
    private fun guestEnvArgs(): List<String> = listOf(
        "HOME=/root",
        "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "TERM=xterm-256color",
        "LANG=C.UTF-8"
    )

    /** Builds the full argv used to enter the rootfs and run [command] (or an interactive login shell if null). */
    private fun buildArgv(command: List<String>?): Array<String> {
        val proot = prootBinary().absolutePath
        val rootfs = rootfsManager.rootfsDir.absolutePath
        val args = mutableListOf(
            proot,
            "--link2symlink",
            "-0",
            "-r", rootfs,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-w", "/root",
            "--kill-on-exit",
            "/usr/bin/env", "-i"
        )
        args += guestEnvArgs()
        args += if (command.isNullOrEmpty()) {
            listOf("/bin/sh", "--login")
        } else {
            listOf("/bin/sh", "-c", command.joinToString(" "))
        }
        return args.toTypedArray()
    }

    /** Creates an interactive [TerminalSession] (used by the Terminal tab, FR-3). */
    fun createInteractiveSession(client: TerminalSessionClient): TerminalSession {
        val argv = buildArgv(null)
        return TerminalSession(argv[0], rootfsManager.rootfsDir.absolutePath, argv, hostEnv(), 4000, client)
    }

    data class CommandResult(val exitCode: Int, val output: String)

    /**
     * Runs a single non-interactive command inside the rootfs (used by the
     * Dashboard's Update/Upgrade/Fix/Search/Install actions, FR-4/5/6) and
     * captures combined stdout+stderr for logging/parsing.
     */
    fun runCommand(command: String, onOutputLine: ((String) -> Unit)? = null): CommandResult {
        val argv = buildArgv(listOf(command))
        val pb = ProcessBuilder(*argv)
        pb.redirectErrorStream(true)
        pb.directory(context.filesDir)
        pb.environment().apply {
            clear()
            hostEnv().forEach { kv ->
                val idx = kv.indexOf('=')
                put(kv.substring(0, idx), kv.substring(idx + 1))
            }
        }
        val process = pb.start()
        val buffer = ByteArrayOutputStream()
        process.inputStream.bufferedReader().forEachLine { line ->
            buffer.write(line.toByteArray())
            buffer.write('\n'.code)
            onOutputLine?.invoke(line)
        }
        val exit = process.waitFor()
        return CommandResult(exit, buffer.toString(Charsets.UTF_8.name()))
    }
}
