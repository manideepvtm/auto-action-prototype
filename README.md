# AutoAction

AutoAction is a zero-friction Android app that automatically detects screenshots, extracts text using on-device ML, and provides a single, relevant action notification.

## Features
- **Automatic Detection**: Runs in the background (Foreground Service) and listens for new screenshots.
- **Privacy First**: All processing (OCR) happens on-device using ML Kit. No images are uploaded.
- **Smart Actions**:
  - **Tracking Numbers** (FedEx, UPS, USPS, Amazon, DHL) -> Open Tracking
  - **Dates/Times** -> Add to Calendar
  - **Addresses** -> Open Maps
  - **Prices** -> Save Expense (Local DB)
  - **Error Codes** -> Search Google for fix
- **Zero Friction**: One tap execution.

## Project Structure
- `ui/`: Jetpack Compose UI (Setup & Permissions)
- `service/`: Foreground Service handling `MediaStore` observation.
- `domain/`: Logic for OCR (`ScreenshotProcessor`) and Classification (`IntentClassifier`).
- `data/`: Room Database for Expenses.
- `util/`: Notification helpers and Receivers.

## Permissions
- `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`: To read screenshot files.
- `POST_NOTIFICATIONS`: To show action suggestions.
- `FOREGROUND_SERVICE`: To ensure reliable background monitoring.

## How to Run
1. Open the project in Android Studio (Hedgehog or later recommended).
2. Sync Gradle.
3. Run on an Emulator or Device (Android 10+, API 29+).
4. Grant permissions on the setup screen.
5. Exit the app (process keeps running).
6. **Test**: Take a screenshot of a tracking number or address.
   - *Tip*: You can use `adb shell input keyevent 120` to take a screenshot on emulator.
   - Or download a sample image and save it to the emulator's Gallery/Screenshots folder (might need to trigger media scan).
   - Best tested on a real device by taking an actual screenshot.

## Data Storage
Expenses are saved locally in a Room database (`auto_action_db`). 

## Privacy
- No internet permission used for data exfiltration (only for downloading ML models if needed, though usually bundled).
- Screenshots are processed in memory and discarded.
