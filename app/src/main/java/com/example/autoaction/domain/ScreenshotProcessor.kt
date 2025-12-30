package com.example.autoaction.domain

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class ScreenshotProcessor(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processScreenshot(uri: Uri): ActionIntent {
        try {
            val image = InputImage.fromFilePath(context, uri)
            val result = recognizer.process(image).await()
            return IntentClassifier.classify(result.text)
        } catch (e: Exception) {
            e.printStackTrace()
            return ActionIntent.None
        }
    }
}
