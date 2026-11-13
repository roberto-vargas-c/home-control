package app.homecontrol.devices

import android.content.Context
import app.homecontrol.data.Brand
import app.homecontrol.data.Device
import app.homecontrol.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** One entry point for every brand, so the UI and automations don't care which is which. */
class DeviceController(private val context: Context) {
    private val store = Store(context)

    suspend fun set(device: Device, on: Boolean, brightnessPct: Int? = null) = withContext(Dispatchers.IO) {
        val s = store.settings()
        when (device.brand) {
            Brand.HUE -> HueClient.setState(device.address, s.hueUsername, device.ref, on, brightnessPct)
            Brand.TAPO -> TapoClient(device.address, s.tapoEmail, s.tapoPassword).setState(on, brightnessPct)
            Brand.GOVEE -> GoveeClient.setState(device.address, on, brightnessPct)
        }
    }

    /** Returns (on, brightness) when the device can report it; Govee LAN can't, so null. */
    suspend fun state(device: Device): Pair<Boolean, Int>? = withContext(Dispatchers.IO) {
        val s = store.settings()
        runCatching {
            when (device.brand) {
                Brand.HUE -> HueClient.getState(device.address, s.hueUsername, device.ref)
                Brand.TAPO -> TapoClient(device.address, s.tapoEmail, s.tapoPassword).info()
                    .let { it.on to (it.brightness ?: 100) }
                Brand.GOVEE -> null
            }
        }.getOrNull()
    }

    /** Applies the same action to many devices in parallel. Returns error messages, empty if all OK. */
    suspend fun setMany(devices: List<Device>, on: Boolean, brightnessPct: Int?): List<String> = coroutineScope {
        devices.map { d ->
            async { runCatching { set(d, on, brightnessPct) }.exceptionOrNull()?.let { "${d.name}: ${it.message}" } }
        }.awaitAll().filterNotNull()
    }
}
