package com.example.autoaction.service

import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import com.example.autoaction.domain.ActionIntent
import com.example.autoaction.domain.ScreenshotProcessor
import com.example.autoaction.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScreenshotService : Service() {

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var screenshotProcessor: ScreenshotProcessor
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            // If uri is null (can happen on older APIs), we need to query latest.
            // Even if not null, it might be generic. Safest is to query latest image.
            checkLatestImage()
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        screenshotProcessor = ScreenshotProcessor(this)
        startForeground(1, notificationHelper.getServiceNotification())

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure foreground
        startForeground(1, notificationHelper.getServiceNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(contentObserver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkLatestImage() {
        serviceScope.launch {
            try {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.RELATIVE_PATH // API 29+
                )
                
                // Sort by date added desc
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val pathIndex = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                        val dateIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)

                        val id = cursor.getLong(idIndex)
                        val path = if (pathIndex != -1) cursor.getString(pathIndex) else ""
                        val dateAdded = cursor.getLong(dateIndex)

                        // Check if it's recent (last 30 seconds) to avoid reprocessing old images on restart
                        // Note: dateAdded is in seconds
                        val currentTime = System.currentTimeMillis() / 1000
                        if (currentTime - dateAdded > 30) {
                            return@use
                        }

                        // Check if it's in Screenshots folder
                        // RELATIVE_PATH usually returns "DCIM/Screenshots/" or "Pictures/Screenshots/"
                        if (path.contains("Screenshots", ignoreCase = true)) {
                            val contentUri = android.content.ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                            
                            // Process it
                            val intent = screenshotProcessor.processScreenshot(contentUri)
                            if (intent !is ActionIntent.None) {
                                notificationHelper.showActionNotification(intent)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
