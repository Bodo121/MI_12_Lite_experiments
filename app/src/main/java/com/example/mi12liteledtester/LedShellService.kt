package com.example.mi12liteledtester

import java.util.concurrent.TimeUnit

/** Runs only read-only diagnostics in Shizuku's shell-identity UserService. */
class LedShellService : ILedShellService.Stub() {
    override fun diagnostics(): String {
        val commands = listOf(
            "id" to "/system/bin/id",
            "LED device names and permissions" to "/system/bin/ls -la /sys/class/leds",
            "Potential front-flash interfaces" to "/system/bin/sh -c 'ls /sys/class/leds 2>/dev/null | grep -Ei "flash|torch|front|selfie|led"'",
            "Camera system service" to "/system/bin/sh -c 'cmd -l 2>/dev/null | grep -i camera'"
        )
        return commands.joinToString("\n\n") { (label, command) ->
            val result = runCommand(command)
            "=== $label ===\n$result"
        }.take(12000)
    }

    private fun runCommand(command: String): String {
        // Arguments are hardcoded above; user input is never passed to a shell.
        val process = try {
            ProcessBuilder("/system/bin/sh", "-c", command)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) { return "Start failed: ${e.javaClass.simpleName}: ${e.message}" }

        return try {
            val finished = process.waitFor(4, TimeUnit.SECONDS)
            if (!finished) { process.destroyForcibly(); "Command timed out" }
            else {
                val out = process.inputStream.bufferedReader().use { it.readText().take(2500) }
                "exit=${process.exitValue()}\n$out"
            }
        } catch (e: Exception) {
            process.destroyForcibly()
            "Error: ${e.javaClass.simpleName}: ${e.message}"
        }
    }
}
