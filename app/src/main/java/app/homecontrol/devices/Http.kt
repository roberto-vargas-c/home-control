package app.homecontrol.devices

import java.net.HttpURLConnection
import java.net.URL

class HttpResult(val code: Int, val body: ByteArray, val headers: Map<String, List<String>>) {
    val text get() = String(body, Charsets.UTF_8)
}

/** Tiny blocking HTTP helper. Always call from Dispatchers.IO. */
fun http(
    method: String,
    url: String,
    body: ByteArray? = null,
    headers: Map<String, String> = emptyMap(),
    timeoutMs: Int = 5000,
): HttpResult {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = method
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.useCaches = false
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
        }
        val code = conn.responseCode
        val stream = if (code >= 400) conn.errorStream else conn.inputStream
        val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
        return HttpResult(code, bytes, conn.headerFields.filterKeys { it != null })
    } finally {
        conn.disconnect()
    }
}
