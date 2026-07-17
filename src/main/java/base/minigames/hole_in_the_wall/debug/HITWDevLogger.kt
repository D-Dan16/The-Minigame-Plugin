package base.minigames.hole_in_the_wall.debug

import base.MinigamePlugin
import base.minigames.hole_in_the_wall.HITWConst
import base.minigames.hole_in_the_wall.models.Wall
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Dev-only logger for Hole In The Wall lifecycle tracing.
 *
 * Writes to a file under the plugin data folder and stays silent unless
 * [HITWConst.Development.IS_IN_DEVELOPMENT] is enabled.
 */
object HITWDevLogger {
    private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val lock = Any()

    private var writer: BufferedWriter? = null
    private var logFile: File? = null

    /** Initializes the log file. Safe to call multiple times. */
    fun initialize() {
        if (!HITWConst.Development.IS_IN_DEVELOPMENT) return

        synchronized(lock) {
            if (writer != null) return

            val logsFolder = File(MinigamePlugin.plugin.dataFolder, "logs")
            if (!logsFolder.exists()) {
                logsFolder.mkdirs()
            }

            logFile = File(logsFolder, "hole_in_the_wall-dev.log")
            writer = BufferedWriter(
                OutputStreamWriter(
                    FileOutputStream(logFile!!, true),
                    StandardCharsets.UTF_8
                )
            )

            writeLine("logger initialized at ${LocalDateTime.now().format(timestampFormatter)}")
        }
    }

    /** Closes the file handle if logging is enabled. Safe to call multiple times. */
    fun shutdown() {
        synchronized(lock) {
            writer?.flush()
            writer?.close()
            writer = null
        }
    }

    /** Logs a plain dev message. */
    fun log(message: String) {
        if (!HITWConst.Development.IS_IN_DEVELOPMENT) return
        synchronized(lock) {
            if (writer == null) initialize()
            writeLine("INFO | $message")
        }
    }

    /** Logs a warning-level dev message. */
    fun warn(message: String) {
        if (!HITWConst.Development.IS_IN_DEVELOPMENT) return
        synchronized(lock) {
            if (writer == null) initialize()
            writeLine("WARN | $message")
        }
    }

    /** Logs an exception and its complete stack trace to the HITW dev log. */
    fun error(message: String, throwable: Throwable) {
        if (!HITWConst.Development.IS_IN_DEVELOPMENT) return
        synchronized(lock) {
            if (writer == null) initialize()
            writeLine("ERROR | $message")

            val stackTrace = StringWriter().also { stringWriter ->
                throwable.printStackTrace(PrintWriter(stringWriter))
            }.toString().trimEnd()

            stackTrace.lineSequence().forEach { line ->
                writeLine("ERROR | $line")
            }
        }
    }

    /** Logs a wall-scoped dev message with a stable wall id. */
    fun wall(wall: Wall, message: String) {
        log("wall#${wall.debugId} | $message")
    }

    /** Returns the current log file if logging is enabled. */
    fun currentLogFile(): File? = logFile

    private fun writeLine(message: String) {
        if (!HITWConst.Development.IS_IN_DEVELOPMENT) return

        val now = LocalDateTime.now().format(timestampFormatter)
        writer?.apply {
            append("[$now] $message")
            newLine()
            flush()
        }
    }
}
