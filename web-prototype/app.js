
// DOM Elements
const dropArea = document.getElementById('drop-area');
const fileElem = document.getElementById('fileElem');
const previewContainer = document.getElementById('preview-container');
const previewImage = document.getElementById('preview-image');
const resultCard = document.getElementById('result-card');
const actionTitle = document.getElementById('action-title');
const actionDesc = document.getElementById('action-desc');
const actionButton = document.getElementById('action-button');
const loadingDiv = document.getElementById('loading');
const statusText = document.getElementById('status-text');
const logContainer = document.getElementById('log-container');
const ocrResult = document.getElementById('ocr-result');

// Regex Patterns (Ported from Kotlin)
const PATTERNS = {
    // Regex Patterns (Improved)
    // Note: We will now use a list of objects to control order and add metadata
    TRACKING_RULES: [
        { carrier: 'UPS', regex: /\b(1Z[0-9A-Z]{16})\b/ },
        { carrier: 'Amazon', regex: /\b(TBA[0-9]{12})\b/ },
        // USPS Spaced Pattern (matches 9xxx xxxx xxxx ... format common on labels)
        { carrier: 'USPS', regex: /\b(9\d{3}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{2})\b/ },
        { carrier: 'USPS', regex: /\b(\d{22})\b/ },
        { carrier: 'USPS', regex: /\b(9\d{21})\b/ },
        { carrier: 'FedEx', regex: /\b(\d{12})\b/ },
        { carrier: 'FedEx', regex: /\b(\d{15})\b/ },
        { carrier: 'FedEx', regex: /\b(\d{20})\b/ },
        { carrier: 'DHL', regex: /\b(\d{10})\b/ }
    ],
    DATE: /\b(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})\b/,
    TIME: /\b(\d{1,2}:\d{2}\s?(AM|PM)?)\b/i,
    ADDRESS_KEYWORDS: ["Street", "St.", "Avenue", "Ave", "Road", "Rd", "Boulevard", "Blvd", "Lane", "Ln", "Drive", "Dr"],
    PRICE: /([$€£₹])\s?(\d+[.,]?\d*)/,
    ERROR_CODE: /\b(Error|Code|Status)\s?(\d{3,4}|0x[0-9A-F]+)\b/i
};

// Drag & Drop Events
['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
    dropArea.addEventListener(eventName, preventDefaults, false);
});

function preventDefaults(e) {
    e.preventDefault();
    e.stopPropagation();
}

['dragenter', 'dragover'].forEach(eventName => {
    dropArea.addEventListener(eventName, highlight, false);
});

['dragleave', 'drop'].forEach(eventName => {
    dropArea.addEventListener(eventName, unhighlight, false);
});

function highlight(e) {
    dropArea.classList.add('highlight');
}

function unhighlight(e) {
    dropArea.classList.remove('highlight');
}

dropArea.addEventListener('drop', handleDrop, false);

function handleDrop(e) {
    const dt = e.dataTransfer;
    const files = dt.files;
    handleFiles(files);
}

function handleFiles(files) {
    const file = files[0];
    if (file && file.type.startsWith('image/')) {
        processImage(file);
    }
}

async function processImage(file) {
    // Reset UI
    resultCard.classList.add('hidden');
    logContainer.classList.add('hidden');
    loadingDiv.classList.remove('hidden');
    previewContainer.classList.remove('hidden');

    // Show Preview
    const reader = new FileReader();
    reader.onload = (e) => {
        previewImage.src = e.target.result;
    };
    reader.readAsDataURL(file);

    // Run OCR
    try {
        statusText.textContent = "Scanning text...";
        const worker = await Tesseract.createWorker('eng');
        const ret = await worker.recognize(file);
        await worker.terminate();

        const text = ret.data.text;
        ocrResult.textContent = text;
        logContainer.classList.remove('hidden');

        // Classify
        const action = classifyIntent(text);
        showAction(action);

    } catch (err) {
        console.error(err);
        statusText.textContent = "Error processing image.";
    } finally {
        loadingDiv.classList.add('hidden');
    }
}

function classifyIntent(text) {
    const textUpper = text.toUpperCase();

    // 1. Tracking
    // Strategy: Score matches based on presence of keywords. 
    // If specific carrier keyword is found in text, prioritize that carrier.

    let bestMatch = null;
    let bestScore = -1;

    for (const rule of PATTERNS.TRACKING_RULES) {
        const match = text.match(rule.regex);
        if (match) {
            let score = 0;
            const cleanNumber = match[0].replace(/[\s-]/g, '');

            // Boost score if carrier name is explicitly in text
            if (textUpper.includes(rule.carrier.toUpperCase())) {
                score += 20; // Stronger boost
            } else {
                // PENALIZE if the text explicitly mentions a DIFFERENT carrier
                // Example: If text says "USPS" but we matched a "FedEx" rule, heavily penalize it.
                if (rule.carrier === 'FedEx' && textUpper.includes('USPS')) score -= 50;
                if (rule.carrier === 'USPS' && textUpper.includes('FEDEX')) score -= 50;
                if (rule.carrier === 'UPS' && textUpper.includes('FEDEX')) score -= 50;
            }

            // Boost score for longer numbers (longer is usually more specific and less likely to be random noise)
            score += cleanNumber.length;

            if (score > bestScore && score > 5) { // Threshold to avoid trash matches
                bestScore = score;
                bestMatch = {
                    type: 'TRACKING',
                    carrier: rule.carrier,
                    number: cleanNumber,
                    title: `Track ${rule.carrier}`,
                    desc: `Detected ${rule.carrier} tracking number: ${cleanNumber}`,
                    url: `https://www.google.com/search?q=${rule.carrier}+tracking+${cleanNumber}`
                };
            }
        }
    }

    // If found a match with decent confidence or keyword backing
    if (bestMatch) {
        // Sanity check: If score is low (just digits) and we have multiple candidates, maybe hold off?
        // For MVP, just return the best fit.
        return bestMatch;
    }

    // 2. Address
    const lines = text.split('\n');
    for (const line of lines) {
        if (PATTERNS.ADDRESS_KEYWORDS.some(k => line.includes(k))) {
            // Simple heuristic to verify it looks like an address (starts with digit?)
            if (line.trim().match(/^\d/)) {
                const address = line.trim();
                return {
                    type: 'MAP',
                    address: address,
                    title: 'Open in Maps',
                    desc: `Detected address: ${address}`,
                    url: `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`
                };
            }
        }
    }

    // 3. Calendar
    const dateMatch = text.match(PATTERNS.DATE);
    if (dateMatch) {
        const date = dateMatch[0];
        const timeMatch = text.match(PATTERNS.TIME);
        const time = timeMatch ? timeMatch[0] : "All Day";
        // Google Calendar Link Construction
        // Format: YYYYMMDD is best, but for basic web link query works too sometimes, or just a generic calendar open command?
        // Web intents for calendar are tricky. Let's send to Google Calendar Web.
        return {
            type: 'CALENDAR',
            title: 'Add to Calendar',
            desc: `Detected Event: ${date} at ${time}`,
            url: `https://calendar.google.com/calendar/r/eventedit?text=New+Event&dates=${formatDateForGCal(date)}` // Simplified
        };
    }

    // 4. Expense
    const priceMatch = text.match(PATTERNS.PRICE);
    if (priceMatch) {
        const currency = priceMatch[1];
        const amount = priceMatch[2];
        return {
            type: 'EXPENSE',
            title: 'Save Expense',
            desc: `Detected expense of ${currency}${amount}.`,
            action: () => alert(`Expense Saved: ${currency}${amount} (Simulated)`)
        };
    }

    // 5. Error Code
    const errorMatch = text.match(PATTERNS.ERROR_CODE);
    if (errorMatch) {
        const code = errorMatch[0];
        return {
            type: 'SEARCH',
            title: 'Search Error',
            desc: `Detected error code: ${code}`,
            url: `https://www.google.com/search?q=${encodeURIComponent(code)}`
        };
    }

    return { type: 'NONE' };
}

function showAction(action) {
    if (action.type === 'NONE') {
        actionTitle.textContent = "No Action Detected";
        actionDesc.textContent = "Could not identify a clear action from this screenshot.";
        actionButton.classList.add('hidden');
    } else {
        actionTitle.textContent = action.title;
        actionDesc.textContent = action.desc;
        actionButton.classList.remove('hidden');

        if (action.url) {
            actionButton.href = action.url;
            actionButton.onclick = null;
            actionButton.textContent = `Open ${action.type === 'TRACKING' ? 'Tracker' : action.type === 'MAP' ? 'Maps' : 'Link'}`;
        } else if (action.action) {
            actionButton.href = "#";
            actionButton.onclick = (e) => {
                e.preventDefault();
                action.action();
            };
            actionButton.textContent = "Save Now";
        }
    }
    resultCard.classList.remove('hidden');
}

// Helper for GCal (Naive)
function formatDateForGCal(dateStr) {
    // Very naive parser for YYYYMMDD. 
    // In prod, use a date library. Here just returning 'now' if fail.
    try {
        const d = new Date(dateStr);
        if (isNaN(d.getTime())) return new Date().toISOString().replace(/-|:|\.\d\d\d/g, "");
        return d.toISOString().replace(/-|:|\.\d\d\d/g, "");
    } catch {
        return new Date().toISOString().replace(/-|:|\.\d\d\d/g, "");
    }
}
