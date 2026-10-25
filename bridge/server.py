import os
import re
import time
import threading

from flask import Flask, request, jsonify, abort
from PIL import Image, ImageOps, ImageEnhance, ImageFilter
import pytesseract

pytesseract.pytesseract.tesseract_cmd = os.environ.get(
    "TESSERACT_CMD",
    r"C:\Program Files\Tesseract-OCR\tesseract.exe"
)

app = Flask(__name__)

TOKEN = os.environ.get("BRIDGE_TOKEN", "change-this-dev-token")
ID_PATTERN = re.compile(r"(?<!\d)\d{8}(?!\d)")

# Holds only the current scan status/ID for the existing /latest endpoint.
# OCR text and image data are never stored here.
latest = {
    "id": None,
    "created_at": 0,
    "status": "waiting"
}

lock = threading.Lock()


def require_auth():
    if request.headers.get("Authorization", "") != f"Bearer {TOKEN}":
        abort(401)


def extract_booking_id(image):
    """Run one OCR attempt and discard OCR text after extracting the ID."""
    processed = None
    text = None

    try:
        processed = ImageOps.grayscale(image)
        processed = ImageOps.autocontrast(processed)

        processed = processed.resize(
            (processed.width * 2, processed.height * 2),
            Image.Resampling.LANCZOS
        )

        processed = ImageEnhance.Contrast(processed).enhance(1.8)
        processed = processed.filter(ImageFilter.SHARPEN)

        ocr_config = (
            "--oem 3 --psm 6 "
            "-c tessedit_char_whitelist=0123456789 "
            "-c load_system_dawg=0 "
            "-c load_freq_dawg=0"
        )

        # OCR runs immediately and synchronously for this scan.
        text = pytesseract.image_to_string(
            processed,
            config=ocr_config
        )

        matches = ID_PATTERN.findall(text)
        unique_ids = list(dict.fromkeys(matches))

        # Do not guess if multiple distinct IDs are detected.
        if len(unique_ids) == 1:
            return unique_ids[0]

        if len(unique_ids) > 1:
            print("Multiple IDs detected; scan rejected.")

        return None

    finally:
        # Release image-processing objects and OCR text.
        text = None
        if processed is not None:
            processed.close()
        processed = None


@app.get("/health")
def health():
    return jsonify({"ok": True})


@app.post("/screenshot")
def screenshot():
    require_auth()

    if "image" not in request.files:
        return jsonify({
            "error": "Expected multipart file field named image"
        }), 400

    # Clear previous scan state immediately for a fresh scan.
    with lock:
        latest.update({
            "id": None,
            "created_at": 0,
            "status": "processing"
        })

    image = None

    try:
        image = Image.open(
            request.files["image"].stream
        ).convert("RGB")

        print("New scan received; starting OCR.")

        found = extract_booking_id(image)

        with lock:
            latest.update({
                "id": found,
                "created_at": time.time(),
                "status": "id_found" if found else "no_id_found"
            })

        print("Scan completed:", "ID found" if found else "No ID found")

        # Return the result directly; no OCR text or screenshot is returned.
        return jsonify({
            "status": "id_found" if found else "no_id_found",
            "id": found
        })

    except Exception as exc:
        print("Scan failed:", type(exc).__name__)

        with lock:
            latest.update({
                "id": None,
                "created_at": time.time(),
                "status": "error"
            })

        return jsonify({
            "status": "error",
            "id": None
        }), 500

    finally:
        if image is not None:
            image.close()

        # Remove the uploaded file reference from this request.
        request.files.pop("image", None)


@app.get("/latest")
def get_latest():
    require_auth()
    with lock:
        return jsonify(latest.copy())


@app.post("/ack")
def ack():
    require_auth()

    with lock:
        latest.update({
            "id": None,
            "created_at": 0,
            "status": "waiting"
        })

    return jsonify({"ok": True})


if __name__ == "__main__":
    print("Bridge listening on port 8765; keep this terminal open.")
    app.run(host="0.0.0.0", port=8765, debug=False)
