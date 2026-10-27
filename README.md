# Screen to Ctrl+F

An Android-to-desktop workflow for finding IDs in an open Google Sheets spreadsheet using OCR and a Chrome extension.

## Quick download (APK + source)

If you just want to install the app, start at the project's [GitHub Releases page](https://github.com/mt1240405/OverlayScanner/releases). Open the latest release and check **Assets** for both files:

- **APK** — download this to your Android phone and install it.
- **Source ZIP** — download this if you want the source code. Extract it on your PC before following the setup/build instructions below.

## How it works

1. Tap **Find ID** on the Android floating overlay.
2. The Android app captures a screenshot and sends it to the Python bridge running on your PC.
3. The bridge runs Tesseract OCR and extracts an eight-digit ID when it finds exactly one unique match.
4. The Chrome extension polls the bridge for the result.
5. The extension opens Find (`Ctrl+F`) in the configured Google Sheets tab and enters the ID.

## Requirements

- A Windows PC and Android phone connected to the same local network.
- [Python for Windows](https://www.python.org/downloads/windows/) (Python 3.10 or newer).
- [Tesseract OCR](https://tesseract-ocr.github.io/tessdoc/Installation.html). Follow the installation guide and install the Windows executable.
- [Google Chrome](https://www.google.com/chrome/).
- For building the Android app locally: [Android Studio](https://developer.android.com/studio) and JDK 17.

## 1. Download the source code

Install [Git](https://git-scm.com/downloads) if it is not already installed. Open **PowerShell** and run:

```powershell
git clone https://github.com/mt1240405/OverlayScanner.git
cd OverlayScanner
```

If you do not want to install Git, open the [repository page](https://github.com/mt1240405/OverlayScanner), click **Code → Download ZIP**, extract the ZIP, and open PowerShell in the extracted `OverlayScanner` folder.

## 2. Install Tesseract OCR

1. Open the [Tesseract installation guide](https://tesseract-ocr.github.io/tessdoc/Installation.html).
2. Follow its Windows installation link and install Tesseract.
3. The bridge's default executable path is:

   ```text
   C:\Program Files\Tesseract-OCR\tesseract.exe
   ```

4. Check that the file exists at that path in File Explorer. If you installed Tesseract in a different folder, note the full path to `tesseract.exe`; you will set it in Step 3.

## 3. Set up and start the Python bridge

Open PowerShell in the repository's `OverlayScanner` folder. Run each command in order:

```powershell
py -3 --version
py -3 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r bridge\requirements.txt
```

The first command should print your Python version. If `py` is not recognized, reinstall Python from [python.org](https://www.python.org/downloads/windows/). During setup, select **Add python.exe to PATH** if the installer shows that option, then choose **Install Now**. Close and reopen PowerShell and verify:

```powershell
py -3 --version
python --version
```

If Python is already installed but commands are not recognized, add it to PATH manually:

1. Press **Start** and search for **Edit environment variables for your account**.
2. Open it, select **Path** under **User variables**, and click **Edit**.
3. Click **New** and add the folder containing `python.exe`. Typical locations include:
   - `%LocalAppData%\Programs\Python\Python312\`
   - `%LocalAppData%\Programs\Python\Python313\`
   Use the actual folder on your computer; the version number may differ.
4. Click **New** again and add that installation's `Scripts` folder, for example:
   - `%LocalAppData%\Programs\Python\Python312\Scripts\`
5. Click **OK** in each window to save the changes.
6. Close all PowerShell windows, open a new one, and run `python --version` and `python -m pip --version`.

To find the actual installation folder, search Windows for `python.exe`, or open the Python installer and choose **Modify** to view the installation location. Do not add the `.venv` folder to your permanent PATH; activate the virtual environment using the command in Step 3.

If PowerShell blocks virtual-environment activation, run this command in the same PowerShell window and activate again:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\.venv\Scripts\Activate.ps1
```

### Configure the bridge token

Create a private token. In the same PowerShell window, run this command, replacing the example with your own token:

```powershell
$env:BRIDGE_TOKEN = "replace-this-with-a-long-random-secret"
```

You must keep this token and enter the exact same value in the Android app and Chrome extension.

**Important:** The current `bridge/server.py` falls back to the public development token `change-this-dev-token` when `BRIDGE_TOKEN` is not set. Do not use that fallback. Set your own token before starting the bridge.

### Configure the Tesseract path if needed

If `tesseract.exe` is not at `C:\Program Files\Tesseract-OCR\tesseract.exe`, run the following command, replacing the example path with the actual path:

```powershell
$env:TESSERACT_CMD = "C:\path\to\tesseract.exe"
```

### Start the server

From the repository root, run:

```powershell
python bridge\server.py
```

Leave this PowerShell window open. The bridge listens on port `8765` by default.

To check that the server has started, open this address in a browser on the PC:

```text
http://127.0.0.1:8765/health
```

A working server should return:

```json
{"ok":true}
```

## 4. Find your PC's local IP address

The phone must connect to the PC's local network address, not `127.0.0.1`.

1. Keep the bridge running.
2. Open a **second** PowerShell window.
3. Run:

   ```powershell
   ipconfig
   ```

4. Find the network adapter you are actually using (usually **Wireless LAN adapter Wi-Fi**).
5. Copy its **IPv4 Address**, for example `192.168.1.25`.

Your bridge URL is the IPv4 address followed by `:8765`:

```text
http://192.168.1.25:8765
```

Replace `192.168.1.25` with the IPv4 address shown on your PC. Use this same URL in both the Chrome extension and Android app.

Your phone and PC must be connected to the same Wi-Fi/network and the network must allow devices to communicate with each other. If Windows Firewall asks, allow Python on your **Private network**. If the phone still cannot connect, check the Windows Firewall inbound rule for TCP port `8765` and your Wi-Fi router's client/AP isolation setting.

### Optional: change the port

The default port is `8765`. To change it, open `bridge/server.py` in a text editor, find the final line:

```python
app.run(host="0.0.0.0", port=8765, debug=False)
```

Change `8765` to the port you want, save the file, and restart the server. Use that same port in the bridge URL in the extension and Android app. Make sure Windows Firewall permits the chosen port on your private network.

## 5. Install and configure the Chrome extension

Chrome extensions are loaded from the `extension` folder in this repository.

1. Open Google Chrome.
2. Enter `chrome://extensions` in the address bar and press Enter.
3. Turn on **Developer mode** using the toggle on the extensions page.
4. Click **Load unpacked**.
5. In the folder picker, select the repository's `extension` folder (not the repository root).
6. The Screen to Ctrl+F extension should appear in the extensions list. Pin it to the toolbar if you want easier access.
7. Click the extension icon to open its popup.
8. Fill in these fields:

   - **Bridge URL:** your PC IP and bridge port, for example `http://192.168.1.25:8765`.
   - **Bridge token:** the exact token set as `BRIDGE_TOKEN` in Step 3.
   - **Target Google Sheets URL:** Leave this blank to target the active Google Sheets tab. For a specific sheet, paste its full URL here; the extension will target only an open tab whose URL matches that value.

9. Click **Save settings**.
10. Open the target spreadsheet in Chrome and make sure you can edit/use Find in it.
11. Return to the extension popup and click **Start polling**. Leave Chrome open while using the app.

Chrome's official instructions: [Load an unpacked extension](https://developer.chrome.com/docs/extensions/get-started/tutorial/hello-world#load-unpacked).

**Chrome debugger prompt:** The extension uses Chrome's debugger permission to type into Find. Chrome may display a warning when the extension is loaded or starts interacting with a tab.

## 6. Install the Android app

Choose one of the options below.

### Option A — Download an APK from GitHub Releases

1. Open [Screen to Ctrl+F Releases](https://github.com/mt1240405/OverlayScanner/releases).
2. Open the latest release.
3. Under **Assets**, download the `.apk` file to your phone.
4. On the phone, open the downloaded APK from the Downloads app or file manager and follow Android's installation prompts.
5. If Android asks permission to install apps from that source, allow it for the file manager or browser you used, then return and install the APK.

### Option B — Download the APK from a GitHub Actions build

The repository has a **Build Android APK** workflow.

1. Open the [Build Android APK workflow](https://github.com/mt1240405/OverlayScanner/actions/workflows/build-apk.yml).
2. Open the most recent run marked **Success** for the version/commit you want.
3. On the run page, scroll to **Artifacts**.
4. Click `screen-to-ctrl-f-debug-apk` to download the ZIP artifact.
5. Extract the ZIP. It contains the debug APK.
6. Transfer the APK to your Android phone and install it as described in Option A.

### Option C — Build the APK locally

1. Install [Android Studio](https://developer.android.com/studio).
2. Open Android Studio and choose **Open**.
3. Select the repository's `android` folder.
4. Wait for Gradle sync to finish. If Android Studio asks to install a missing Android SDK component, accept the required installation.
5. Connect your phone with USB debugging enabled. Alternatively, build the APK and transfer it to the phone manually.
6. To build from the Android Studio menus, choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

   Or, from PowerShell at the repository root, run:

   ```powershell
   cd android
   .\gradlew.bat assembleDebug
   ```

7. The APK will be created at:

   ```text
   app\build\outputs\apk\debug\app-debug.apk
   ```

   This path is relative to the `android` folder. From the repository root, the full relative path is `android\app\build\outputs\apk\debug\app-debug.apk`.

8. Install the APK on the connected phone from Android Studio, or transfer that APK to the phone and open it to install.

If installation fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, an app with the same package name but a different signing key is already installed. Uninstall the old app and install the new APK again. Uninstalling removes the app's local data and settings.

## 7. Configure Android permissions and bridge settings

1. Open **Screen to Ctrl+F** on your phone.
2. Grant the permissions requested by the app.
3. Enable its floating overlay permission:
   - Open Android **Settings**.
   - Go to **Apps → Screen to Ctrl+F → Display over other apps / Appear on top**.
   - Turn the permission on. Names and menu locations vary by phone; use Settings search for **display over other apps** if needed.
4. If Android 13 or newer blocks the permission and shows **Allow restricted settings**, open **Settings → Apps → Screen to Ctrl+F → ⋮ → Allow restricted settings**, if that option is available. Return to the app settings and enable the required permission. See [Android's official restricted settings guidance](https://support.google.com/android/answer/12623953?hl=en).
5. In the app's settings, enter:
   - **Bridge URL:** the same URL used in the extension, e.g. `http://192.168.1.25:8765`.
   - **Bridge token:** the same token used in the extension and `BRIDGE_TOKEN` in the server terminal.
6. Save the settings.
7. Start screen capture in the app and accept Android's system screen-capture prompt.
8. Confirm the floating **Find ID** control appears. If it does not, check the overlay permission and reopen the app.

## 8. Run a scan

Before scanning, make sure:

- The Python bridge is running in PowerShell.
- The Android app and extension use the same bridge URL and token.
- Chrome has the target Google Sheets tab open.
- The extension popup shows polling has started.
- Android screen capture is active and the floating **Find ID** control is visible.

Then:

1. On the phone, display the screen containing the booking ID.
2. Tap **Find ID** on the floating overlay.
3. Wait for the bridge to process the screenshot.
4. If exactly one unique eight-digit ID is found, the extension should enter it into Find in the matching Google Sheets tab.

The bridge does not guess when OCR finds multiple different eight-digit IDs. Make the target ID clearly visible and avoid capturing other eight-digit numbers in the same screenshot. You can modify this functionality in server.py.

## 9. Troubleshooting

### `py` or Python is not recognized

Install Python from [python.org](https://www.python.org/downloads/windows/), close and reopen PowerShell, and verify with `py -3 --version`.

### The bridge cannot find Tesseract

Confirm that `tesseract.exe` exists at `C:\Program Files\Tesseract-OCR\tesseract.exe`. If it is elsewhere, set `TESSERACT_CMD` in the same PowerShell session before starting `python bridge\server.py`.

### The phone cannot connect to the bridge

Confirm the server is running, use the PC's IPv4 address (not `127.0.0.1`) in the phone's URL, check that both devices are on the same network, and check Windows Firewall for the selected port.

### The extension does not find the target sheet

Keep the sheet open in Chrome. Check **Target Google Sheets URL**: leave `docs.google.com/spreadsheets` to target the active Sheets tab, or paste the full sheet URL to target that specific open sheet. Save settings and click **Start polling** again.

### The scan returns an error

Check the PowerShell window running the bridge. In the current source, `bridge/server.py` attempts to call `request.files.pop("image", None)` in its `finally` block. Flask's `request.files` is immutable, so this can cause an HTTP 500 after a scan. To fix it, open `bridge/server.py`, delete the line `request.files.pop("image", None)`, save the file, and restart the bridge. The image is already closed by the preceding cleanup code.

### The ID is not detected

The current OCR pattern looks for eight consecutive digits. Make sure the number is sharp, fully visible, and not obscured. If your booking ID has a different format, edit `ID_PATTERN` in `bridge/server.py`; if OCR still misses it, test changes to `ocr_config` in the same file.

## Security

Use a private bridge token and do not share it publicly. The bridge is intended for a trusted local network; do not configure router port forwarding or expose port `8765` to the public internet. Screenshots are sent to the PC running the bridge for OCR.

## License

This project is distributed under the MIT License.
