package app.homecontrol.devices

import org.json.JSONArray
import org.json.JSONObject

/**
 * Philips Hue, talking straight to the Hue Bridge on your Wi-Fi (local API v1).
 * Pairing: press the round button on the bridge, then call [pair] within 30 seconds.
 */
object HueClient {

    /** Finds bridges on the network via Hue's discovery endpoint. Returns IPs. */
    fun discover(): List<String> {
        val r = http("GET", "https://discovery.meethue.com/")
        if (r.code != 200) throw Exception("Hue discovery failed (HTTP ${r.code}). Enter the bridge IP by hand.")
        val arr = JSONArray(r.text)
        return (0 until arr.length()).map { arr.getJSONObject(it).getString("internalipaddress") }
    }

    /** Returns the new API username (key) for this app. */
    fun pair(ip: String): String {
        val body = JSONObject().put("devicetype", "homecontrol#android").toString().toByteArray()
        val r = http("POST", "http://$ip/api", body, mapOf("Content-Type" to "application/json"))
        val first = JSONArray(r.text).getJSONObject(0)
        first.optJSONObject("success")?.let { return it.getString("username") }
        val err = first.optJSONObject("error")
        if (err?.optInt("type") == 101) throw Exception("Press the button on the Hue Bridge, then tap Pair again.")
        throw Exception("Hue pairing failed: ${err?.optString("description") ?: r.text}")
    }

    /** Light id -> name, plus model id. */
    fun lights(ip: String, user: String): List<Triple<String, String, String>> {
        val r = http("GET", "http://$ip/api/$user/lights")
        val trimmed = r.text.trim()
        if (trimmed.startsWith("[")) throw Exception("Hue: ${JSONArray(trimmed).getJSONObject(0)}")
        val obj = JSONObject(trimmed)
        return obj.keys().asSequence().map { id ->
            val l = obj.getJSONObject(id)
            Triple(id, l.optString("name", "Hue light $id"), l.optString("modelid"))
        }.toList()
    }

    fun setState(ip: String, user: String, lightId: String, on: Boolean, brightnessPct: Int?) {
        val state = JSONObject().put("on", on)
        if (on && brightnessPct != null) state.put("bri", (brightnessPct * 254 / 100).coerceIn(1, 254))
        val r = http(
            "PUT", "http://$ip/api/$user/lights/$lightId/state",
            state.toString().toByteArray(), mapOf("Content-Type" to "application/json"),
        )
        if (r.text.contains("\"error\"")) throw Exception("Hue: ${r.text}")
    }

    /** Returns (on, brightnessPct) or null if unknown. */
    fun getState(ip: String, user: String, lightId: String): Pair<Boolean, Int>? {
        val r = http("GET", "http://$ip/api/$user/lights/$lightId")
        val state = JSONObject(r.text).optJSONObject("state") ?: return null
        val bri = if (state.has("bri")) state.getInt("bri") * 100 / 254 else 100
        return state.getBoolean("on") to bri.coerceIn(1, 100)
    }
}
