package com.dien.terminal.core.logging

/** Classifies raw `apk` output lines for highlighting (FR-13) and result counting (FR-10/11). */
object LogLineClassifier {
    enum class Level { ERROR, WARNING, SUCCESS, NORMAL }

    private val errorRegex = Regex("error|failed|unable to|unsatisfiable", RegexOption.IGNORE_CASE)
    private val warningRegex = Regex("warning|missing|deprecated", RegexOption.IGNORE_CASE)
    private val successRegex = Regex("^\\(\\d+/\\d+\\)|installing|upgrading|reinstalling|fetching|ok:", RegexOption.IGNORE_CASE)

    fun classify(line: String): Level = when {
        errorRegex.containsMatchIn(line) -> Level.ERROR
        warningRegex.containsMatchIn(line) -> Level.WARNING
        successRegex.containsMatchIn(line) -> Level.SUCCESS
        else -> Level.NORMAL
    }

    /** Best-effort counts of fixed vs failed packages from an `apk fix`/`apk upgrade` transcript. */
    fun countFixResult(output: String): Pair<Int, Int> {
        var fixed = 0
        var failed = 0
        output.lines().forEach { line ->
            when (classify(line)) {
                Level.SUCCESS -> fixed++
                Level.ERROR -> failed++
                else -> {}
            }
        }
        return fixed to failed
    }
}
