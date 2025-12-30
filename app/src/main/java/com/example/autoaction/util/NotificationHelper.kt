package com.example.autoaction.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.autoaction.R
import com.example.autoaction.domain.ActionIntent

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "AutoAction Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val actionChannel = NotificationChannel(
                CHANNEL_ID_ACTION,
                "AutoAction Suggestions",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(actionChannel)
        }
    }

    fun getServiceNotification(): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
            .setContentTitle("AutoAction is running")
            .setContentText("Monitoring for screenshots...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Standard placeholder
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showActionNotification(intent: ActionIntent) {
        val (title, actionIntent) = when (intent) {
            is ActionIntent.TrackPackage -> "Track Package" to Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${intent.carrier}+tracking+${intent.trackingNumber}"))
            is ActionIntent.OpenMap -> "Open Maps" to Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(intent.address)}")).setPackage("com.google.android.apps.maps")
            is ActionIntent.AddCalendarEvent -> "Add to Calendar" to Intent(Intent.ACTION_INSERT).apply {
                data = android.provider.CalendarContract.Events.CONTENT_URI
                putExtra(android.provider.CalendarContract.Events.TITLE, intent.title)
                putExtra(android.provider.CalendarContract.Events.DESCRIPTION, "Detected auto-action")
                
                // Parse date to set correct time
                try {
                     val formats = listOf(
                        java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US),
                        java.text.SimpleDateFormat("MM-dd-yyyy", java.util.Locale.US),
                        java.text.SimpleDateFormat("M/d/yyyy", java.util.Locale.US)
                    )
                    var dateMillis: Long? = null
                    for (sdf in formats) {
                        try {
                             val d = sdf.parse(intent.date)
                             if (d != null) {
                                 dateMillis = d.time
                                 break
                             }
                        } catch (e: Exception) {}
                    }
                    
                    if (dateMillis != null) {
                        putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                        putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, dateMillis)
                        putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, dateMillis)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            is ActionIntent.SaveExpense -> "Save Expense" to null // Handled internally
            is ActionIntent.SearchError -> "Search Error" to Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${intent.errorCode}"))
            ActionIntent.None -> return
        }

        val pendingIntent = if (actionIntent != null) {
            PendingIntent.getActivity(
                context, 0, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            // Internal action for Expense
            // We need a BroadcastReceiver or Service action to handle this without opening UI?
            // "Zero friction" -> One tap saves.
            // Create a PendingIntent that triggers a helper receiver/service call.
            // For MVP simplicity, let's just assume we trigger an Activity that saves and closes, or a BroadcastReceiver.
            // Let's use a BroadcastReceiver.
            val broadcastIntent = Intent(context, ActionReceiver::class.java).apply {
                action = ACTION_SAVE_EXPENSE
                putExtra("amount", (intent as ActionIntent.SaveExpense).amount)
                putExtra("currency", intent.currency)
                putExtra("merchant", intent.merchant)
            }
            PendingIntent.getBroadcast(
                context, 0, broadcastIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ACTION)
            .setContentTitle("Screenshot Detected")
            .setContentText(title)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, title, pendingIntent) // Add as action button too
            .build()

        notificationManager.notify(NOTIFICATION_ID_ACTION, notification)
    }

    companion object {
        const val CHANNEL_ID_SERVICE = "service_channel"
        const val CHANNEL_ID_ACTION = "action_channel"
        const val NOTIFICATION_ID_ACTION = 1001
        const val ACTION_SAVE_EXPENSE = "com.example.autoaction.SAVE_EXPENSE"
    }
}
