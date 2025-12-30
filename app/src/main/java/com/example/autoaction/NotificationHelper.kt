package com.example.autoaction

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "auto_action_channel"
        const val CHANNEL_NAME = "Auto Action"
    }

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showActionNotification(action: Action) {
        val intent: Intent
        val title: String
        val text: String
        val buttonLabel: String
        
        when (action) {
            is Action.TrackPackage -> {
                intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
                title = "Tracking Number Detected (${action.carrier})"
                text = "Tap to track: ${action.code}"
                buttonLabel = "Track Package"
            }
            is Action.CreateEvent -> {
                // Calendar Insert Intent
                intent = Intent(Intent.ACTION_INSERT).apply {
                    data = android.provider.CalendarContract.Events.CONTENT_URI
                    putExtra(android.provider.CalendarContract.Events.TITLE, action.title)
                    putExtra(android.provider.CalendarContract.Events.DESCRIPTION, action.description)
                    // Set event to the expiration date (all day)
                    putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                    putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, action.dateInMillis)
                    putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, action.dateInMillis)
                }
                title = "Expiration Date Detected"
                text = "Tap to create reminder: ${action.description}"
                buttonLabel = "Add to Calendar"
            }
        }

        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_search) 
            .setContentTitle(title)
            .setContentText(text)
            .addAction(android.R.drawable.ic_menu_my_calendar, buttonLabel, pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
