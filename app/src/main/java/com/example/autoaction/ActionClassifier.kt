package com.example.autoaction

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

object ActionClassifier {
    // Regex for common tracking numbers
    private val FEDEX_REGEX = Regex("\\b[0-9]{12}\\b")
    private val UPS_REGEX = Regex("\\b1Z[0-9A-Z]{16}\\b")
    private val USPS_REGEX = Regex("\\b(9[0-9]{21})\\b")

    // Keywords to confirm tracking context
    private val TRACKING_KEYWORDS = listOf("tracking", "track", "shipment", "delivery", "label", "fedex", "ups", "usps")

    // Regex for Dates: MM/DD/YYYY, MM-DD-YYYY, etc.
    // Simple approach: \d{1,2}[/-]\d{1,2}[/-]\d{2,4}
    private val DATE_REGEX = Regex("\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b")
    
    // Keywords for Expiration
    private val EXPIRATION_KEYWORDS = listOf("expires", "expiration", "exp", "valid until", "good thru")

    fun classify(text: String): Action? {
        val lowerText = text.lowercase()

        // 1. Check for Tracking Numbers with CONTEXT
        val trackingAction = checkTracking(text, lowerText)
        if (trackingAction != null) return trackingAction

        // 2. Check for Expiration Dates
        val expirationAction = checkExpiration(text, lowerText)
        if (expirationAction != null) return expirationAction

        return null
    }

    private fun checkTracking(text: String, lowerText: String): Action? {
        // Must contain at least one tracking keyword to be valid (reduces False Positives)
        val hasKeyword = TRACKING_KEYWORDS.any { lowerText.contains(it) }
        if (!hasKeyword) return null

        // FedEx
        var match = FEDEX_REGEX.find(text)
        if (match != null) {
            return Action.TrackPackage(match.value, "https://www.fedex.com/fedextrack/?trknbr=${match.value}", "FedEx")
        }

        // UPS
        match = UPS_REGEX.find(text)
        if (match != null) {
            return Action.TrackPackage(match.value, "https://www.ups.com/track?tracknum=${match.value}", "UPS")
        }

        // USPS
        match = USPS_REGEX.find(text)
        if (match != null) {
            return Action.TrackPackage(match.value, "https://tools.usps.com/go/TrackConfirmAction?tLabels=${match.value}", "USPS")
        }
        
        return null
    }

    private fun checkExpiration(text: String, lowerText: String): Action? {
        // Must find date AND have "expiration" context nearby
        val dateMatches = DATE_REGEX.findAll(text)
        val today = Calendar.getInstance().time

        for (match in dateMatches) {
            val dateStr = match.value
            val date = parseDate(dateStr) ?: continue

            // Logic: Is it in the future?
            if (date.after(today)) {
                // Check context: is "expire" word nearby? (e.g. within 50 chars)
                val rangeStart = (match.range.first - 50).coerceAtLeast(0)
                val rangeEnd = (match.range.last + 50).coerceAtMost(lowerText.length)
                val contextWindow = lowerText.substring(rangeStart, rangeEnd)
                
                if (EXPIRATION_KEYWORDS.any { contextWindow.contains(it) }) {
                    return Action.CreateEvent(
                        title = "Document Expiration",
                        description = "Expires on $dateStr",
                        dateInMillis = date.time
                    )
                }
            }
        }
        return null
    }

    private fun parseDate(dateStr: String): Date? {
        val formats = listOf(
            SimpleDateFormat("MM/dd/yyyy", Locale.US),
            SimpleDateFormat("MM-dd-yyyy", Locale.US),
            SimpleDateFormat("M/d/yyyy", Locale.US), // Handle single digits
            SimpleDateFormat("MM/dd/yy", Locale.US)  // Handle 2-digit year
        )
        
        for (sdf in formats) {
            try {
                sdf.isLenient = false
                return sdf.parse(dateStr)
            } catch (e: Exception) {
                // continue
            }
        }
        return null
    }
}

sealed class Action {
    data class TrackPackage(val code: String, val url: String, val carrier: String) : Action()
    data class CreateEvent(val title: String, val description: String, val dateInMillis: Long) : Action()
}
