package app.homecontrol.data

import android.content.Context
import org.json.JSONArray

/** Everything is kept on the phone, in the app's private storage. Nothing goes to a cloud. */
class Store(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("home", Context.MODE_PRIVATE)

    fun devices(): List<Device> = readArray("devices").map { Device.fromJson(it) }
    fun saveDevices(list: List<Device>) = writeArray("devices", list.map { it.toJson() })

    fun automations(): List<Automation> = readArray("automations").map { Automation.fromJson(it) }
    fun saveAutomations(list: List<Automation>) = writeArray("automations", list.map { it.toJson() })

    fun updateAutomation(id: String, change: (Automation) -> Automation) =
        saveAutomations(automations().map { if (it.id == id) change(it) else it })

    fun settings() = Settings(
        hueBridgeIp = prefs.getString("hueBridgeIp", "")!!,
        hueUsername = prefs.getString("hueUsername", "")!!,
        tapoEmail = prefs.getString("tapoEmail", "")!!,
        tapoPassword = prefs.getString("tapoPassword", "")!!,
    )

    fun saveSettings(s: Settings) = prefs.edit()
        .putString("hueBridgeIp", s.hueBridgeIp)
        .putString("hueUsername", s.hueUsername)
        .putString("tapoEmail", s.tapoEmail)
        .putString("tapoPassword", s.tapoPassword)
        .apply()

    private fun readArray(key: String) =
        JSONArray(prefs.getString(key, "[]")).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

    private fun writeArray(key: String, items: List<org.json.JSONObject>) =
        prefs.edit().putString(key, JSONArray(items).toString()).commit()
}
