package com.example.autoaction.domain

import java.util.regex.Pattern

sealed class ActionIntent {
    data class TrackPackage(val trackingNumber: String, val carrier: String) : ActionIntent()
    data class AddCalendarEvent(val date: String, val time: String?, val title: String) : ActionIntent()
    data class OpenMap(val address: String) : ActionIntent()
    data class SaveExpense(val amount: Double, val currency: String, val merchant: String?) : ActionIntent()
    data class SearchError(val errorCode: String) : ActionIntent()
    object None : ActionIntent()
}

object IntentClassifier {

    private val DATE_PATTERN = Pattern.compile("\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b") 
    private val TIME_PATTERN = Pattern.compile("\\b(\\d{1,2}:\\d{2}\\s?(AM|PM)?)\\b", Pattern.CASE_INSENSITIVE)
    private val ADDRESS_KEYWORDS = listOf("Street", "St.", "Avenue", "Ave", "Road", "Rd", "Boulevard", "Blvd", "Lane", "Ln", "Drive", "Dr")
    private val PRICE_PATTERN = Pattern.compile("([$€£₹])\\s?(\\d+[.,]?\\d*)")
    private val ERROR_CODE_PATTERN = Pattern.compile("\\b(Error|Code|Status)\\s?(\\d{3,4}|0x[0-9A-F]+)\\b", Pattern.CASE_INSENSITIVE)

    // Context Keywords
    private val EXPIRATION_KEYWORDS = listOf("expires", "expiration", "exp", "valid until", "good thru", "due date")
    private val TRACKING_KEYWORDS = listOf("tracking", "track", "shipment", "delivery", "label", "fedex", "ups", "usps", "dhl")

    data class TrackingRule(val carrier: String, val regex: String)

    private val TRACKING_RULES = listOf(
        TrackingRule("UPS", "\\b(1Z[0-9A-Z]{16})\\b"),
        TrackingRule("Amazon", "\\b(TBA[0-9]{12})\\b"),
        TrackingRule("USPS", "\\b(\\d{22})\\b"),
        TrackingRule("USPS", "\\b(9\\d{21})\\b"),
        TrackingRule("FedEx", "\\b(\\d{12})\\b"),
        TrackingRule("FedEx", "\\b(\\d{15})\\b"),
        TrackingRule("DHL", "\\b(\\d{10})\\b")
    )

    fun classify(text: String): ActionIntent {
        val textUpper = text.uppercase()
        val textLower = text.lowercase()
        // Remove spaces/dashes between digits: "9434 6301" -> "94346301"
        val cleanText = text.replace(Regex("(\\d)[\\s-](?=\\d)"), "$1")

        // 1. Check for Tracking Numbers with Scoring & Context
        var bestMatch: ActionIntent.TrackPackage? = null
        var bestScore = -1

        // Require at least one tracking context keyword to avoid false positives (like IDs)
        val hasTrackingContext = TRACKING_KEYWORDS.any { textLower.contains(it) }

        for (rule in TRACKING_RULES) {
            val matcher = Pattern.compile(rule.regex).matcher(cleanText)
            if (matcher.find()) {
                val number = matcher.group(0)
                var score = 0
                
                // If it's a generic digit rule (FedEx 12 digits), REQUIRE strong context or carrier name
                if (rule.regex.contains("\\d{12}") || rule.regex.contains("\\d{10}") || rule.regex.contains("\\d{15}")) {
                     if (!hasTrackingContext && !textUpper.contains(rule.carrier.uppercase())) {
                         continue // Skip aggressive matches without context
                     }
                }

                if (textUpper.contains(rule.carrier.uppercase())) score += 10
                score += number.length

                if (score > bestScore) {
                    bestScore = score
                    bestMatch = ActionIntent.TrackPackage(number, rule.carrier)
                }
            }
        }

        if (bestMatch != null) {
            return bestMatch
        }

        // 2. Check for Expiration Date/Time
        val dateMatcher = DATE_PATTERN.matcher(text)
        while (dateMatcher.find()) { // Use while to check all dates
            val dateStr = dateMatcher.group(0)
            
            // Validate: Is it in the future? Is it an "Expiration"?
            if (isFutureDate(dateStr) && hasExpirationContext(textLower, dateMatcher.start(), dateMatcher.end())) {
                 val timeMatcher = TIME_PATTERN.matcher(text)
                 val time = if (timeMatcher.find()) timeMatcher.group(0) else null
                 val title = "Document Expiration"
                 return ActionIntent.AddCalendarEvent(dateStr, time, title)
            }
        }

        // 3. Check for Address
        val lines = text.lines()
        for (line in lines) {
            if (ADDRESS_KEYWORDS.any { line.contains(it, ignoreCase = true) }) {
                if (line.trim().firstOrNull()?.isDigit() == true) {
                    return ActionIntent.OpenMap(line.trim())
                }
            }
        }

        // 4. Expense
        val priceMatcher = PRICE_PATTERN.matcher(text)
        if (priceMatcher.find()) {
            val currency = priceMatcher.group(1)
            val value = priceMatcher.group(2).replace(",", ".").toDoubleOrNull()
            if (value != null) {
                 // Try to find merchant name (simplified)
                val merchant = lines.firstOrNull { it.isNotBlank() && !it.contains(currency) && !it.any { c -> c.isDigit() } } 
                return ActionIntent.SaveExpense(value, currency, merchant)
            }
        }

        return ActionIntent.None
    }

    private fun isFutureDate(dateStr: String): Boolean {
        try {
             // Try common formats
             val formats = listOf(
                java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US),
                java.text.SimpleDateFormat("MM-dd-yyyy", java.util.Locale.US),
                java.text.SimpleDateFormat("M/d/yyyy", java.util.Locale.US)
            )
            val now = java.util.Date()
            for (sdf in formats) {
                sdf.isLenient = false
                try {
                    val date = sdf.parse(dateStr)
                    if (date != null && date.after(now)) return true
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
        return false
    }

    private fun hasExpirationContext(text: String, start: Int, end: Int): Boolean {
        // Look within 50 chars before/after
        val rangeStart = (start - 50).coerceAtLeast(0)
        val rangeEnd = (end + 50).coerceAtMost(text.length)
        val contextWindow = text.substring(rangeStart, rangeEnd)
        return EXPIRATION_KEYWORDS.any { contextWindow.contains(it) }
    }
}
