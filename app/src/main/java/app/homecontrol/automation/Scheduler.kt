package app.homecontrol.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.homecontrol.data.Automation
import app.homecontrol.data.Store
import java.time.ZonedDateTime

/**
 * Turns automations into Android alarms. Each enabled automation has exactly one pending
 * alarm for its next run; when it fires, AlarmReceiver runs it and books the following one.
 */
object Scheduler {

    fun nextRun(a: Automation, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        for (dayOffset in 0..7) {
            val candidate = now.toLocalDate().plusDays(dayOffset.toLong())
                .atTime(a.hour, a.minute).atZone(now.zone)
            if (!candidate.isAfter(now)) continue
            if (a.days.isEmpty() || candidate.dayOfWeek.value in a.days) return candidate
        }
        return null
    }

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun rescheduleAll(context: Context) {
        Store(context).automations().forEach { schedule(context, it) }
    }

    fun schedule(context: Context, a: Automation) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, a.id)
        am.cancel(pi)
        if (!a.enabled) return
        val at = nextRun(a)?.toInstant()?.toEpochMilli() ?: return
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            // Without the "Alarms & reminders" permission Android may delay this by several minutes.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context, automationId: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, automationId))
    }

    private fun pendingIntent(context: Context, automationId: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction("app.homecontrol.RUN_AUTOMATION")
            .putExtra(AlarmReceiver.EXTRA_ID, automationId)
        return PendingIntent.getBroadcast(
            context, automationId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
