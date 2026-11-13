package app.homecontrol.devices

import android.content.Context
import android.net.wifi.WifiManager
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * Govee LAN API (UDP). Each device needs "LAN Control" switched on once in the
 * Govee Home app (device page > settings). Not every Govee model supports it.
 */
object GoveeClient {
    private const val MULTICAST = "239.255.255.250"
    private const val SCAN_PORT = 4001
    private const val LISTEN_PORT = 4002
    private const val CONTROL_PORT = 4003

    data class Found(val ip: String, val deviceId: String, val sku: String)

    fun scan(context: Context, waitMs: Int = 3000): List<Found> {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("govee-scan").apply { setReferenceCounted(false); acquire() }
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(LISTEN_PORT))
            soTimeout = 500
        }
        try {
            val msg = """{"msg":{"cmd":"scan","data":{"account_topic":"reserve"}}}""".toByteArray()
            socket.send(DatagramPacket(msg, msg.size, InetAddress.getByName(MULTICAST), SCAN_PORT))
            val found = linkedMapOf<String, Found>()
            val buf = ByteArray(2048)
            val deadline = System.currentTimeMillis() + waitMs
            while (System.currentTimeMillis() < deadline) {
                val p = DatagramPacket(buf, buf.size)
                try { socket.receive(p) } catch (_: SocketTimeoutException) { continue }
                val data = runCatching {
                    JSONObject(String(p.data, 0, p.length)).getJSONObject("msg").getJSONObject("data")
                }.getOrNull() ?: continue
                val ip = data.optString("ip").takeIf { it.isNotEmpty() } ?: p.address.hostAddress ?: continue
                found[ip] = Found(ip, data.optString("device"), data.optString("sku"))
            }
            return found.values.toList()
        } finally {
            socket.close()
            lock.release()
        }
    }

    fun setState(ip: String, on: Boolean, brightnessPct: Int?) {
        send(ip, """{"msg":{"cmd":"turn","data":{"value":${if (on) 1 else 0}}}}""")
        if (on && brightnessPct != null) {
            Thread.sleep(150)
            send(ip, """{"msg":{"cmd":"brightness","data":{"value":${brightnessPct.coerceIn(1, 100)}}}}""")
        }
    }

    private fun send(ip: String, json: String) {
        DatagramSocket().use { s ->
            val b = json.toByteArray()
            s.send(DatagramPacket(b, b.size, InetAddress.getByName(ip), CONTROL_PORT))
        }
    }
}
