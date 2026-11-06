package br.com.otavioesteves.finances.data.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.com.otavioesteves.finances.MainActivity
import br.com.otavioesteves.finances.R
import br.com.otavioesteves.finances.domain.model.nextInvoiceReminder
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InvoiceReminders(private val context: Context, private val clock: () -> ZonedDateTime = { ZonedDateTime.now() }) {
    private val preferences = context.getSharedPreferences("invoice_reminders", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(preferences.getBoolean("enabled", true))
    val enabled = _enabled.asStateFlow()
    val permissionAsked: Boolean get() = preferences.getBoolean("permission_asked", false)

    fun markPermissionAsked() { preferences.edit().putBoolean("permission_asked", true).apply() }
    fun setEnabled(value: Boolean) {
        preferences.edit().putBoolean("enabled", value).apply()
        _enabled.value = value
        schedule()
        if (!value) NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun canNotify(): Boolean = (Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 26 || context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE)

    fun schedule() {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(context, 501, Intent(context, InvoiceReminderReceiver::class.java)
            .setAction(ACTION_REMIND), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.cancel(intent)
        if (!_enabled.value) return
        if (clock().hour >= 9) deliver()
        val next = nextInvoiceReminder(clock())
        // A monthly reminder doesn't need exact-alarm privileges; Android may defer delivery.
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), intent)
    }

    fun deliver() {
        val now = clock()
        val date = now.toLocalDate()
        val key = date.toString()
        if (!_enabled.value || !canNotify() || (date.dayOfMonth != 5 && date.dayOfMonth != date.lengthOfMonth()) ||
            preferences.getString("last_delivered", null) == key) return
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Lembretes de faturas", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(context, 502, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Finances")
            .setContentText("Já faturou esse mês?")
            .setContentIntent(open).setAutoCancel(true).build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            preferences.edit().putString("last_delivered", key).apply()
        } catch (_: SecurityException) { /* Permission can be revoked between the check and delivery. */ }
    }

    companion object {
        const val ACTION_REMIND = "br.com.otavioesteves.finances.REMIND_INVOICE"
        const val CHANNEL_ID = "invoice_reminders"
        private const val NOTIFICATION_ID = 501
    }
}

class InvoiceReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminders = InvoiceReminders(context)
        if (intent.action == InvoiceReminders.ACTION_REMIND || ZonedDateTime.now().hour >= 9) reminders.deliver()
        reminders.schedule()
    }
}
