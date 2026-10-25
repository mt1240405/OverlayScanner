# Screen to Ctrl+F

An Android-to-desktop workflow for finding booking IDs in an open Google Sheets spreadsheet using OCR and a Chrome extension.

## Overview

Screen to Ctrl+F connects an Android screen capture workflow to a local Python bridge on a computer. The bridge extracts an eight-digit booking ID from the screenshot using Tesseract OCR. A Chrome extension then searches for that ID in a Google Sheets tab.

## How It Works

1. Tap the Android floating **Find ID** overlay.
2. The app captures the screen and sends the screenshot to the Python bridge.
3. The bridge processes the image with Tesseract OCR and attempts to extract an eight-digit ID.
4. The Chrome extension polls the bridge for a new result.
5. The extension searches for the ID in the configured open Google Sheets tab using `Ctrl+F`.

## Components

- **Android app:** Floating overlay and screenshot capture.
- **Python bridge:** Receives screenshots, runs OCR, and exposes the result to the extension.
- **Chrome extension:** Retrieves the extracted ID and searches the selected spreadsheet.

## Requirements

- Android device and the project’s required Android SDK/build tools.
- Windows computer or another environment supported by the Python bridge.
- Python and the dependencies listed by the project.
- [Tesseract OCR](https://github.com/tesseract-ocr/tesseract).
- Google Chrome and access to the target Google Sheets spreadsheet.
- Android device and computer connected to a network that permits communication between them.

## Setup

### 1. Configure the Python bridge

From the repository root, set the bridge token as an environment variable.

PowerShell:

```powershell
$env:BRIDGE_TOKEN = "YOUR_NEW_SECRET_TOKEN"
python bridge/server.py
```

Set the Tesseract executable path in the bridge configuration if it is not already discoverable. For a typical Windows installation:

```text
C:\Program Files\Tesseract-OCR\tesseract.exe
```

Keep the token private. Do not commit it to source control.

### 2. Configure the Android app

Configure the bridge address and authentication token using the project's supported configuration mechanism. The Android device must be able to reach the computer running the bridge.

Do not commit local configuration files or real tokens. A token embedded in an APK can be extracted, even if it is read from a build configuration variable.

### 3. Load the Chrome extension

1. Open `chrome://extensions` in Chrome.
2. Enable **Developer mode**.
3. Select **Load unpacked**.
4. Select the Chrome extension directory in this repository.
5. Open the extension's settings and configure the bridge address, token, and target spreadsheet as supported by the extension.

Keep the bridge running while using the extension.

## OCR Behavior

The bridge is designed to extract a single eight-digit ID from a screenshot. OCR accuracy depends on the screenshot quality, text size, contrast, and surrounding content.

Check the detected ID if the screenshot contains multiple similar numbers or the text is unclear.

## Network and Security

- Use a trusted local network.
- Do not expose the bridge port directly to the public internet.
- Keep authentication tokens out of Git history and public releases.
- Rotate any token that has been exposed.
- Remember that a token bundled into the Android app or extension is not a durable secret.
- Use appropriate firewall rules and authentication for your environment.

## Troubleshooting

- **Bridge connection fails:** Confirm the Python bridge is running, the configured IP and port are correct, and the device can reach the computer.
- **No ID is detected:** Improve screenshot clarity and verify that the booking ID has eight digits.
- **Wrong spreadsheet is searched:** Check the extension's target spreadsheet configuration and ensure the intended sheet is open in Chrome.
- **Extension does not respond:** Check `chrome://extensions` for errors and inspect the bridge console output.
- **Tesseract is not found:** Verify the installed executable path and the bridge configuration.

## License

This project is licensed under the MIT License.
