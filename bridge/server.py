import os
import re
import time
import threading
from flask import Flask, request, jsonify, abort
from PIL import Image
from PIL import ImageOps, ImageEnhance, ImageFilter
import pytesseract

pytesseract.pytesseract.tesseract_cmd = os.environ.get(
    "TESSERACT_CMD",
    r"C:\Program Files\Tesseract-OCR\tesseract.exe"
)

app = Flask(__name__)
TOKEN = os.environ.get("BRIDGE_TOKEN", "change-this-dev-token")
ID_PATTERN = re.compile(r"\b\d{8}\b")
latest = {"id": None, "created_at": 0, "status": "waiting", "ocr_preview": ""}
lock = threading.Lock()

def require_auth():
    if request.headers.get("Authorization", "") != f"Bearer {TOKEN}":
        abort(401)

@app.get("/health")
def health():
    return jsonify({"ok": True})

@app.post("/screenshot")
def screenshot():
    require_auth()
    if "image" not in request.files:
        return jsonify({"error": "Expected multipart file field named image"}), 400
    try:
        image = Image.open(request.files["image"].stream).convert("RGB")
    except Exception:
        return jsonify({"error": "Invalid image"}), 400

    # Convert to grayscale and increase contrast.
    processed = ImageOps.grayscale(image)
    processed = ImageOps.autocontrast(processed)

    # Enlarge text to make individual digits easier to distinguish.
    processed = processed.resize(
        (processed.width * 3, processed.height * 3),
        Image.Resampling.LANCZOS
    )

    processed = ImageEnhance.Contrast(processed).enhance(2.0)
    processed = processed.filter(ImageFilter.SHARPEN)

    # Recognize digits only; ignore other screen text and icons.
    ocr_config = (
        "--oem 3 --psm 11 "
        "-c tessedit_char_whitelist=0123456789"
    )

    text = pytesseract.image_to_string(processed, config=ocr_config)
    print("OCR TEXT:", repr(text))
    image.save("debug_screenshot.png")
    print("Screenshot size:", image.size)
    print("Image mode:", image.mode)
    matches = ID_PATTERN.findall(text.upper())
    found = matches[0] if matches else None
    with lock:
        latest.update({
            "id": found,
            "created_at": time.time(),
            "status": "id_found" if found else "no_id_found",
            "ocr_preview": text[:500],
        })
    return jsonify({"status": latest["status"], "id": found, "ocr_preview": text[:500]})

@app.get("/latest")
def get_latest():
    require_auth()
    with lock:
        return jsonify(latest)

@app.post("/ack")
def ack():
    require_auth()
    with lock:
        latest.update({"id": None, "created_at": 0, "status": "waiting", "ocr_preview": ""})
    return jsonify({"ok": True})

if __name__ == "__main__":
    print("Bridge listening on port 8765; keep this terminal open.")
    app.run(host="0.0.0.0", port=8765, debug=False)
