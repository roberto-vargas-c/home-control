package app.homecontrol.devices

import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * TP-Link Tapo local control (KLAP protocol), used by current Tapo bulbs, plugs and strips.
 * Uses your Tapo account email + password (the device checks them locally; nothing goes online).
 * On newer firmware, turn on "Third-Party Compatibility" in the Tapo app (Me > Third-Party Services).
 */
class TapoClient(private val ip: String, email: String, password: String) {

    private val authHash = sha256(sha1(email.toByteArray()) + sha1(password.toByteArray()))
    private val terminalUuid = java.util.UUID.randomUUID().toString()

    private class Session(val cookie: String, val key: ByteArray, val iv: ByteArray, val sig: ByteArray, var seq: Int)

    private fun handshake(): Session {
        val localSeed = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val r1 = http("POST", "http://$ip/app/handshake1", localSeed, octet)
        if (r1.code != 200 || r1.body.size < 48) throw Exception("Tapo $ip: no answer to handshake (HTTP ${r1.code})")
        val remoteSeed = r1.body.copyOfRange(0, 16)
        val serverHash = r1.body.copyOfRange(16, 48)
        if (!serverHash.contentEquals(sha256(localSeed + remoteSeed + authHash))) {
            throw Exception("Tapo $ip: wrong email or password, or Third-Party Compatibility is off")
        }
        // Header looks like "TP_SESSIONID=ABC;TIMEOUT=86400"; only the session id is sent back.
        val cookie = r1.headers.entries.filter { it.key.equals("Set-Cookie", true) }
            .flatMap { it.value }.flatMap { it.split(';') }.map { it.trim() }
            .firstOrNull { it.startsWith("TP_SESSIONID=") }
            ?: throw Exception("Tapo $ip: no session cookie")

        val r2 = http(
            "POST", "http://$ip/app/handshake2",
            sha256(remoteSeed + localSeed + authHash), octet + ("Cookie" to cookie),
        )
        if (r2.code != 200) throw Exception("Tapo $ip: login refused (HTTP ${r2.code})")

        val seeds = localSeed + remoteSeed + authHash
        val ivFull = sha256("iv".toByteArray() + seeds)
        return Session(
            cookie = cookie,
            key = sha256("lsk".toByteArray() + seeds).copyOf(16),
            iv = ivFull.copyOf(12),
            sig = sha256("ldk".toByteArray() + seeds).copyOf(28),
            seq = ByteBuffer.wrap(ivFull, 28, 4).int,
        )
    }

    private fun request(s: Session, payload: JSONObject): JSONObject {
        s.seq += 1
        val seqBytes = ByteBuffer.allocate(4).putInt(s.seq).array()
        val iv = s.iv + seqBytes
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(s.key, "AES"), IvParameterSpec(iv))
        val encrypted = cipher.doFinal(payload.toString().toByteArray())
        val body = sha256(s.sig + seqBytes + encrypted) + encrypted

        val r = http("POST", "http://$ip/app/request?seq=${s.seq}", body, octet + ("Cookie" to s.cookie))
        if (r.code != 200) throw Exception("Tapo $ip: request failed (HTTP ${r.code})")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(s.key, "AES"), IvParameterSpec(iv))
        val plain = cipher.doFinal(r.body, 32, r.body.size - 32)
        return JSONObject(String(plain, Charsets.UTF_8))
    }

    private fun call(method: String, params: JSONObject? = null): JSONObject {
        val payload = JSONObject()
            .put("method", method)
            .put("request_time_milis", System.currentTimeMillis())
            .put("terminal_uuid", terminalUuid)
        if (params != null) payload.put("params", params)
        return request(handshake(), payload)
    }

    /** Turns on/off. Brightness is applied for bulbs and silently skipped for plugs. */
    fun setState(on: Boolean, brightnessPct: Int?) {
        val params = JSONObject().put("device_on", on)
        if (on && brightnessPct != null) params.put("brightness", brightnessPct.coerceIn(1, 100))
        var res = call("set_device_info", params)
        if (res.optInt("error_code") != 0 && params.has("brightness")) {
            params.remove("brightness")
            res = call("set_device_info", params)
        }
        if (res.optInt("error_code") != 0) throw Exception("Tapo $ip: error ${res.optInt("error_code")}")
    }

    data class Info(val nickname: String, val model: String, val on: Boolean, val brightness: Int?)

    fun info(): Info {
        val res = call("get_device_info")
        if (res.optInt("error_code") != 0) throw Exception("Tapo $ip: error ${res.optInt("error_code")}")
        val r = res.getJSONObject("result")
        val nick = runCatching {
            String(android.util.Base64.decode(r.optString("nickname"), android.util.Base64.DEFAULT))
        }.getOrDefault("")
        return Info(
            nickname = nick.ifBlank { r.optString("model", "Tapo device") },
            model = r.optString("model"),
            on = r.optBoolean("device_on"),
            brightness = if (r.has("brightness")) r.getInt("brightness") else null,
        )
    }

    private companion object {
        val octet = mapOf("Content-Type" to "application/octet-stream")
        fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)
        fun sha1(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(b)
    }
}
