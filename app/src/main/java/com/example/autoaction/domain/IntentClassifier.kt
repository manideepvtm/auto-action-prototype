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

    data class TrackingRule(val carrier: String, val regex: String)

    // Order matters less now that we score, but improved specific regexes help
    private val TRACKING_RULES = listOf(
        TrackingRule("UPS", "\\b(1Z[0-9A-Z]{16})\\b"),
        TrackingRule("Amazon", "\\b(TBA[0-9]{12})\\b"),
        TrackingRule("USPS", "\\b(\\d{22})\\b"),
        TrackingRule("USPS", "\\b(9\\d{21})\\b"),
        TrackingRule("USPS", "\\b(\\d{20})\\b"),
        TrackingRule("FedEx", "\\b(\\d{12})\\b"),
        TrackingRule("FedEx", "\\b(\\d{15})\\b"),
        TrackingRule("FedEx", "\\b(\\d{20})\\b"),
        TrackingRule("DHL", "\\b(\\d{10})\\b")
    )

    fun classify(text: String): ActionIntent {
        val textUpper = text.uppercase()
        // Remove spaces/dashes between digits: "9434 6301" -> "94346301"
        val cleanText = text.replace(Regex("(\\d)[\\s-]+(?=\\d)"), "$1")

        // 1. Check for Tracking Numbers with Scoring
        var bestMatch: ActionIntent.TrackPackage? = null
        var bestScore = -1

        for (rule in TRACKING_RULES) {
            // Check against CLEAN text
            val matcher = Pattern.compile(rule.regex).matcher(cleanText)
            if (matcher.find()) {
                val number = matcher.group(0)
                var score = 0
                
                // Boost for carrier keyword
                if (textUpper.contains(rule.carrier.uppercase())) {
                    score += 10
                }
                
                // Boost for specificity (longer is usually better for pure digits)
                score += number.length

                // Tie-breaking (e.g. 20 digits overlap)
                if (number.length == 20) {
                    if (rule.carrier == "USPS" && textUpper.contains("USPS")) score += 20
                    if (rule.carrier == "FedEx" && textUpper.contains("FEDEX")) score += 20
                }
                
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = ActionIntent.TrackPackage(number, rule.carrier)
                }
            }
        }

        if (bestMatch != null) {
            return bestMatch
        }

        // 2. Check for Address
        val lines = text.lines()
        for (line in lines) {
            if (ADDRESS_KEYWORDS.any { line.contains(it, ignoreCase = true) }) {
                if (line.trim().firstOrNull()?.isDigit() == true) {
                    return ActionIntent.OpenMap(line.trim())
                }
            }
        }

        // 3. Check for Date/Time (Calendar)
        val dateMatcher = DATE_PATTERN.matcher(text)
        if (dateMatcher.find()) {
            val date = dateMatcher.group(0)
            val timeMatcher = TIME_PATTERN.matcher(text)
            val time = if (timeMatcher.find()) timeMatcher.group(0) else null
            val title = lines.firstOrNull { it.isNotBlank() } ?: "New Event"
            return ActionIntent.AddCalendarEvent(date, time, title)
        }

        // 4. Expense
        val priceMatcher = PRICE_PATTERN.matcher(text)
        if (priceMatcher.find()) {
            val currency = priceMatcher.group(1)
            val value = priceMatcher.group(2).replace(",", ".").toDoubleOrNull()
            if (value != null) {
                val merchant = lines.firstOrNull { it.isNotBlank() && !it.contains(currency) && !it.any { c -> c.isDigit() } } 
                return ActionIntent.SaveExpense(value, currency, merchant)
            }
        }

        // 5. Error Code
        val errorMatcher = ERROR_CODE_PATTERN.matcher(text)
        if (errorMatcher.find()) {
            return ActionIntent.SearchError(errorMatcher.group(0))
        }

        return ActionIntent.None
    }
}
