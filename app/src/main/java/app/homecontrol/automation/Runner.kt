package app.homecontrol.automation

import android.content.Context
import app.homecontrol.data.Automation
import app.homecontrol.data.Store
import app.homecontrol.devices.DeviceController
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Runner {
    /** Runs an automation now, retrying once (Wi-Fi can take a moment to wake). Records the result. */
    suspend fun run(context: Context, a: Automation): String {
        val store = Store(context)
        val devices = store.devices().filter { it.id in a.deviceIds }
        val controller = DeviceController(context)
        var errors = controller.setMany(devices, a.turnOn, a.brightness)
        if (errors.isNotEmpty()) {
            delay(4000)
            val failedNames = errors.map { it.substringBefore(':') }.toSet()
            errors = controller.setMany(devices.filter { it.name in failedNames }, a.turnOn, a.brightness)
        }
        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE HH:mm"))
        val result = if (errors.isEmpty()) "Ran $time ✓" else "Ran $time, problems: ${errors.joinToString("; ")}"
        store.updateAutomation(a.id) { it.copy(lastRun = result) }
        return result
    }
}
