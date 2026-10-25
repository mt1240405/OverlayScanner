# Screen → Ctrl+F (simplified prototype)

This version removes Google Sheets API and OAuth. After the Android side sends an ID, the Chrome extension:
1. finds an already-open Google Sheets tab,
2. focuses it,
3. presses Ctrl+F,
4. types the ID,
5. presses Enter.

## What's included
- `bridge/`: Python web server + Tesseract OCR. Accepts a screenshot and exposes the latest detected ID.
- `extension/`: Chrome extension that polls the bridge and uses Chrome's DevTools Protocol to send real Ctrl+F, type the ID, and press Enter in the Sheets tab.
- `android/`: Android Studio starter app from the earlier prototype.
- `android/`: Android Studio starter app. Important: the included Activity-based capture flow is only a prototype and will capture the app's current screen after the permission prompt, not reliably the previous app's screen. A persistent MediaProjection foreground service plus floating overlay is required for the exact one-tap overlay behavior. See "Android capture limitation".

## 1. Start the bridge on your computer

Install Python 3.10+ and Tesseract OCR:
- Windows: install Tesseract OCR and ensure `tesseract.exe` is on PATH.
- macOS: `brew install tesseract`
- Debian/Ubuntu: `sudo apt install tesseract-ocr`

Open a terminal in `bridge/`:
```bash
python -m venv .venv
# Windows:
.venv\Scripts\activate
# macOS/Linux:
source .venv/bin/activate
pip install -r requirements.txt
```

Set a shared secret before starting the server.

PowerShell:
```powershell
$env:BRIDGE_TOKEN="replace-with-a-long-random-secret"
python server.py
```

macOS/Linux:
```bash
export BRIDGE_TOKEN="replace-with-a-long-random-secret"
python server.py
```

The bridge listens on port 8765. Keep the terminal open. Use only on a trusted private network and do not expose port 8765 to the public internet.

## 2. Install the Chrome extension

1. Open `chrome://extensions`.
2. Turn on Developer mode.
3. Choose **Load unpacked** and select the `extension/` folder.
4. Open the extension's **Details** page and allow access to the local network/host permissions if Chrome asks.
5. Open the extension popup and set:
   - Bridge URL, e.g. `http://192.168.1.25:8765` (computer's LAN IP)
   - same bridge token as the server
   - Google Sheets tab URL match, default `docs.google.com/spreadsheets`
6. Keep a Google Sheet open in a normal Chrome tab.
7. Click **Start polling** in the popup.

### Important Chrome permission
The extension uses the `debugger` permission to send real Ctrl+F keystrokes. Chrome displays a prominent debugging permission warning. This is necessary because synthetic keyboard events from a content script generally cannot reliably trigger the browser/app's real shortcut behavior. The extension only attaches to a Sheets tab when a new ID is received, sends Ctrl+F, types the ID, presses Enter, and detaches.

## 3. Android / bridge address

The phone and computer should be on the same trusted Wi-Fi. Find the computer's LAN IPv4 address:
- Windows: `ipconfig`
- macOS/Linux: `ifconfig` or `ip addr`

Set the bridge URL and token in `android/app/src/main/java/com/example/screentosheets/Config.kt`.

The Android code is a prototype, not a finished floating-overlay app. Android's screen-capture permission flow needs a persistent MediaProjection foreground service and overlay button to capture another app's screen reliably. For now, the simplest test is to post a screenshot to the bridge from a test script or API client, verify OCR, and then verify that the extension searches Sheets.

## 4. Test without Android first

With the bridge running, use this command from another terminal (replace the token and image path):
```bash
curl -X POST "http://127.0.0.1:8765/screenshot" \
  -H "Authorization: Bearer replace-with-a-long-random-secret" \
  -F "image=@test-screenshot.png"
```
Then the extension should detect the new ID and search for it in the open Google Sheet.

## OCR customization

Default regex in `bridge/server.py` looks for an alphanumeric ID containing at least one digit, length 5–40. If your IDs use a different format, change `ID_REGEX`.

## Behavior
This extension only searches; it does not edit spreadsheet cells. It skips processing if no ID is detected, no matching Sheets tab is open, or the extension is not polling.


## Build an APK

This workspace did not have the Android SDK or Gradle installed, so an APK could not be compiled here. The project includes `.github/workflows/build-apk.yml` for building it on GitHub Actions:
1. Upload/push this project to a GitHub repository.
2. Open the repository's **Actions** tab.
3. Select **Build Android APK** and click **Run workflow**.
4. Download the `screen-to-ctrl-f-debug-apk` artifact from the completed workflow.

Alternatively, open the `android/` folder in Android Studio and use **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

The app asks once for Android's screen-capture consent and displays a floating "Find ID" button. When tapped, it posts a screenshot to the bridge. Android can restrict protected/secure screens, and OEM behavior may vary. This is an unsigned debug APK intended for personal testing.


## Clean repository build workflow

The GitHub Actions workflow intentionally does **not** use `android-actions/setup-android@v3`. It installs the required SDK packages using the runner's `sdkmanager` to avoid the obsolete `tools` package failure seen in the previous workflow. After uploading this folder to a new GitHub repository, open **Actions → Build Android APK → Run workflow**. Download the `screen-to-ctrl-f-debug-apk` artifact after the run succeeds.

Note: this is a source repository, not a precompiled APK. The Android capture/overlay behavior remains subject to the prototype limitations described above; this workflow change addresses the SDK setup failure only.
