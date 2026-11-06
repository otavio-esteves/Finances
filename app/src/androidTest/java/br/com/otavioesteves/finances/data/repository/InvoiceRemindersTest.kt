package br.com.otavioesteves.finances.data.repository

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.notifications.InvoiceReminders
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class InvoiceRemindersTest {
    @Test fun deliversExactMessageOncePerScheduledDateAndDisableCancels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val prefs = context.getSharedPreferences("invoice_reminders", 0)
        val originalEnabled = prefs.getBoolean("enabled", true)
        val lastDelivered = prefs.getString("last_delivered", null)
        var date = ZonedDateTime.parse("2028-02-29T09:00:00-03:00[America/Sao_Paulo]")
        val reminders = InvoiceReminders(context) { date }
        val manager = context.getSystemService(NotificationManager::class.java)
        fun awaitVisible(visible: Boolean) {
            val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
            while (manager.activeNotifications.any { it.id == 501 } != visible && android.os.SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(50)
            }
            assertEquals(visible, manager.activeNotifications.any { it.id == 501 })
        }
        try {
            prefs.edit().remove("last_delivered").commit()
            reminders.setEnabled(true)
            reminders.deliver()
            awaitVisible(true)
            assertEquals("Já faturou esse mês?", manager.activeNotifications.single { it.id == 501 }.notification.extras.getString(Notification.EXTRA_TEXT))
            assertEquals("2028-02-29", prefs.getString("last_delivered", null))
            manager.cancel(501)
            awaitVisible(false)
            reminders.deliver()
            assertTrue(manager.activeNotifications.none { it.id == 501 })
            date = date.plusDays(1)
            reminders.deliver()
            assertTrue(manager.activeNotifications.none { it.id == 501 })
            date = date.withDayOfMonth(5)
            reminders.deliver()
            awaitVisible(true)
            reminders.setEnabled(false)
            assertFalse(reminders.enabled.value)
            awaitVisible(false)
        } finally {
            manager.cancel(501)
            prefs.edit().putString("last_delivered", lastDelivered).commit()
            InvoiceReminders(context).setEnabled(originalEnabled)
        }
    }
}
