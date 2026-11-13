package app.homecontrol.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.homecontrol.automation.Runner
import app.homecontrol.automation.Scheduler
import app.homecontrol.data.Automation
import app.homecontrol.data.Brand
import app.homecontrol.data.Device
import app.homecontrol.data.Settings
import app.homecontrol.data.Store
import app.homecontrol.devices.DeviceController
import app.homecontrol.devices.GoveeClient
import app.homecontrol.devices.HueClient
import app.homecontrol.devices.TapoClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val store = Store(app)
    private val controller = DeviceController(app)

    var devices by mutableStateOf(store.devices()); private set
    var automations by mutableStateOf(store.automations()); private set
    var settings by mutableStateOf(store.settings()); private set
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false); private set

    /** Last known (on, brightness) per device id. */
    val states = mutableStateMapOf<String, Pair<Boolean, Int>>()

    init { refreshStates() }

    private fun task(block: suspend () -> Unit) = viewModelScope.launch {
        busy = true
        try { block() } catch (e: Exception) { message = e.message ?: e.toString() } finally { busy = false }
    }

    // ---- Devices -------------------------------------------------------

    fun refreshStates() = viewModelScope.launch {
        devices.forEach { d -> launch { controller.state(d)?.let { states[d.id] = it } } }
    }

    fun toggle(d: Device, on: Boolean) = task {
        val bri = states[d.id]?.second ?: 100
        controller.set(d, on, if (on) bri else null)
        states[d.id] = on to bri
    }

    fun setBrightness(d: Device, pct: Int) = task {
        controller.set(d, true, pct)
        states[d.id] = true to pct
    }

    fun allOff() = task {
        val errors = controller.setMany(devices, false, null)
        devices.forEach { d -> states[d.id] = false to (states[d.id]?.second ?: 100) }
        if (errors.isNotEmpty()) message = errors.joinToString("\n")
    }

    fun removeDevice(d: Device) {
        devices = devices - d
        store.saveDevices(devices)
    }

    fun renameDevice(d: Device, name: String) {
        devices = devices.map { if (it.id == d.id) it.copy(name = name) else it }
        store.saveDevices(devices)
    }

    private fun addDevices(new: List<Device>) {
        // Same brand + address + ref means it is already known.
        val known = devices.map { Triple(it.brand, it.address, it.ref) }.toSet()
        val fresh = new.filter { Triple(it.brand, it.address, it.ref) !in known }
        devices = devices + fresh
        store.saveDevices(devices)
        message = if (fresh.isEmpty()) "No new devices found." else "Added ${fresh.size} device(s)."
        refreshStates()
    }

    // ---- Setup ---------------------------------------------------------

    fun saveSettings(s: Settings) { settings = s; store.saveSettings(s) }

    fun findHueBridge() = task {
        val ips = withContext(Dispatchers.IO) { HueClient.discover() }
        if (ips.isEmpty()) throw Exception("No Hue Bridge found. Enter its IP (Hue app > Settings > My Hue system).")
        saveSettings(settings.copy(hueBridgeIp = ips.first()))
        message = "Found Hue Bridge at ${ips.first()}"
    }

    fun pairHue() = task {
        val ip = settings.hueBridgeIp.trim()
        if (ip.isEmpty()) throw Exception("Enter or find the bridge IP first.")
        val user = withContext(Dispatchers.IO) { HueClient.pair(ip) }
        saveSettings(settings.copy(hueBridgeIp = ip, hueUsername = user))
        importHueLights()
    }

    fun importHueLights() = task {
        val s = settings
        if (s.hueUsername.isEmpty()) throw Exception("Pair with the Hue Bridge first.")
        val lights = withContext(Dispatchers.IO) { HueClient.lights(s.hueBridgeIp, s.hueUsername) }
        addDevices(lights.map { (id, name, model) -> Device(name = name, brand = Brand.HUE, address = s.hueBridgeIp, ref = id, model = model) })
    }

    fun addTapo(ip: String, name: String) = task {
        val s = settings
        if (s.tapoEmail.isBlank() || s.tapoPassword.isBlank()) throw Exception("Save your Tapo email and password first.")
        val info = withContext(Dispatchers.IO) { TapoClient(ip.trim(), s.tapoEmail, s.tapoPassword).info() }
        addDevices(listOf(Device(name = name.ifBlank { info.nickname }, brand = Brand.TAPO, address = ip.trim(), model = info.model)))
    }

    fun scanGovee() = task {
        val found = withContext(Dispatchers.IO) { GoveeClient.scan(ctx) }
        if (found.isEmpty()) throw Exception("No Govee devices answered. Turn on LAN Control for each device in the Govee Home app.")
        addDevices(found.map { Device(name = "Govee ${it.sku}", brand = Brand.GOVEE, address = it.ip, ref = it.deviceId, model = it.sku) })
    }

    // ---- Automations ---------------------------------------------------

    fun saveAutomation(a: Automation) {
        automations = if (automations.any { it.id == a.id }) automations.map { if (it.id == a.id) a else it } else automations + a
        store.saveAutomations(automations)
        Scheduler.schedule(ctx, a)
    }

    fun setEnabled(a: Automation, enabled: Boolean) = saveAutomation(a.copy(enabled = enabled))

    fun deleteAutomation(a: Automation) {
        Scheduler.cancel(ctx, a.id)
        automations = automations - a
        store.saveAutomations(automations)
    }

    fun runNow(a: Automation) = task {
        message = Runner.run(ctx, a)
        automations = store.automations()
        refreshStates()
    }

    fun reload() {
        automations = store.automations()
        devices = store.devices()
    }
}
