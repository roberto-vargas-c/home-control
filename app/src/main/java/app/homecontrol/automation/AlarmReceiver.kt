package app.homecontrol.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.PowerManager
import app.homecontrol.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val pending = goAsync()
        val app = context.applicationContext
        val wake = app.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "homecontrol:automation").apply { acquire(30_000) }
        @Suppress("DEPRECATION")
        val wifi = (app.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "homecontrol:automation").apply { acquire() }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val automation = Store(app).automations().firstOrNull { it.id == id }
                if (automation != null && automation.enabled) {
                    withTimeoutOrNull(25_000) { Runner.run(app, automation) }
                }
            } finally {
                // Book the next occurrence (tomorrow, next weekday, ...).
                Store(app).automations().firstOrNull { it.id == id }?.let { Scheduler.schedule(app, it) }
                if (wifi.isHeld) wifi.release()
                if (wake.isHeld) wake.release()
                pending.finish()
            }
        }
    }

    companion object { const val EXTRA_ID = "automation_id" }
}

/** Re-books all alarms after a reboot, app update, clock change or permission change. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Scheduler.rescheduleAll(context)
}
