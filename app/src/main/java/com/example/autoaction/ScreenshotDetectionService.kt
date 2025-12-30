package com.example.autoaction

import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScreenshotDetectionService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private lateinit var ocrManager: OcrManager
    private lateinit var notificationHelper: NotificationHelper
    
    private var lastProcessedId: Long = -1

    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            checkLatestImage()
        }
    }

    override fun onCreate() {
        super.onCreate()
        ocrManager = OcrManager(this)
        notificationHelper = NotificationHelper(this)
        
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
        Log.d("AutoAction", "Service Started, Observer Registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(contentObserver)
        job.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkLatestImage() {
         val projection = arrayOf(
             MediaStore.Images.Media._ID,
             MediaStore.Images.Media.DISPLAY_NAME,
             MediaStore.Images.Media.DATE_ADDED
         )
         val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
         
         try {
             contentResolver.query(
                 MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                 projection,
                 null,
                 null,
                 sortOrder
             )?.use { cursor ->
                 if (cursor.moveToFirst()) {
                     val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                     val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                     val id = cursor.getLong(idIndex)
                     val name = cursor.getString(nameIndex)
                     
                     if (id != lastProcessedId && name.contains("Screenshot", ignoreCase = true)) {
                         lastProcessedId = id
                         val contentUri = android.content.ContentUris.withAppendedId(
                             MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                             id
                         )
                         processScreenshot(contentUri)
                     }
                 }
             }
         } catch (e: Exception) {
             e.printStackTrace()
         }
    }

    private fun processScreenshot(uri: Uri) {
        scope.launch {
             Log.d("AutoAction", "Processing screenshot: $uri")
             val text = ocrManager.processImage(uri)
             Log.d("AutoAction", "Extracted text: $text")
             
             val action = ActionClassifier.classify(text)
             
             if (action is Action.TrackPackage) {
                 Log.d("AutoAction", "Action found: $action")
                 notificationHelper.showActionNotification(action)
             } else {
                 Log.d("AutoAction", "No relevant action found")
             }
        }
    }
}
