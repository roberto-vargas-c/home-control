package app.homecontrol.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class Brand(val label: String) { HUE("Philips Hue"), TAPO("Tapo"), GOVEE("Govee") }

/**
 * One controllable light or plug.
 * - HUE:   address = bridge IP, ref = Hue light id
 * - TAPO:  address = device IP, ref = "" (unused)
 * - GOVEE: address = device IP, ref = Govee device id / MAC
 */
data class Device(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val brand: Brand,
    val address: String,
    val ref: String = "",
    val model: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("brand", brand.name)
        .put("address", address).put("ref", ref).put("model", model)

    companion object {
        fun fromJson(o: JSONObject) = Device(
            id = o.getString("id"),
            name = o.getString("name"),
            brand = Brand.valueOf(o.getString("brand")),
            address = o.getString("address"),
            ref = o.optString("ref"),
            model = o.optString("model"),
        )
    }
}

/**
 * A time-based automation, e.g. "Morning lights: 07:00 on weekdays, turn on at 80%".
 * days uses ISO numbering: 1 = Monday ... 7 = Sunday. Empty means every day.
 */
data class Automation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hour: Int,
    val minute: Int,
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),
    val deviceIds: List<String>,
    val turnOn: Boolean = true,
    val brightness: Int = 100,
    val enabled: Boolean = true,
    val lastRun: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("hour", hour).put("minute", minute)
        .put("days", JSONArray(days.sorted()))
        .put("deviceIds", JSONArray(deviceIds))
        .put("turnOn", turnOn).put("brightness", brightness)
        .put("enabled", enabled).put("lastRun", lastRun)

    companion object {
        fun fromJson(o: JSONObject) = Automation(
            id = o.getString("id"),
            name = o.getString("name"),
            hour = o.getInt("hour"),
            minute = o.getInt("minute"),
            days = o.getJSONArray("days").let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() },
            deviceIds = o.getJSONArray("deviceIds").let { a -> (0 until a.length()).map { a.getString(it) } },
            turnOn = o.getBoolean("turnOn"),
            brightness = o.getInt("brightness"),
            enabled = o.getBoolean("enabled"),
            lastRun = o.optString("lastRun"),
        )
    }
}

data class Settings(
    val hueBridgeIp: String = "",
    val hueUsername: String = "",
    val tapoEmail: String = "",
    val tapoPassword: String = "",
)
