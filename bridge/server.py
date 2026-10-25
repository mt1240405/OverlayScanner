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

latest = {
    "id": None,
    "created_at": 0,
    "status": "waiting",
    "ocr_preview": ""
}
lock = threading.Lock()


def require_auth():
    if request.headers.get("Authorization", "") != f"Bearer {TOKEN}":
        abort(401)


def preprocess_variants(image):
    """Create several image versions for OCR."""
    gray = ImageOps.grayscale(image)
    gray = ImageOps.autocontrast(gray)

    # Full screenshot, enlarged.
    full = gray.resize(
        (gray.width * 2, gray.height * 2),
        Image.Resampling.LANCZOS
    )
    full = ImageEnhance.Contrast(full).enhance(1.8)
    full = full.filter(ImageFilter.SHARPEN)

    variants = [("full", full)]

    # Try the likely ID area on a 1080x2400 screenshot.
    # If the ID is elsewhere, the full screenshot is still tested.
    left = int(image.width * 0.03)
    right = int(image.width * 0.55)
    top = int(image.height * 0.15)
    bottom = int(image.height * 0.30)

    if right > left and bottom > top:
        crop = gray.crop((left, top, right, bottom))
        crop = crop.resize(
            (crop.width * 4, crop.height * 4),
            Image.Resampling.LANCZOS
        )
        crop = ImageEnhance.Contrast(crop).enhance(2.0)
        crop = crop.filter(ImageFilter.SHARPEN)
        crop = ImageOps.expand(crop, border=20, fill=255)
        variants.append(("crop", crop))

    # Also test inverted colors, useful for light text on dark backgrounds.
    inverted = ImageOps.invert(gray)
    inverted = inverted.resize(
        (inverted.width * 2, inverted.height * 2),
        Image.Resampling.LANCZOS
    )
    inverted = ImageEnhance.Contrast(inverted).enhance(1.8)
    variants.append(("inverted", inverted))

    return variants


def extract_booking_id(image):
    base_config = (
        "--oem 3 "
        "-c tessedit_char_whitelist=0123456789 "
        "-c load_system_dawg=0 "
        "-c load_freq_dawg=0"
    )

    candidates = []
    previews = []

    for variant_name, processed in preprocess_variants(image):
        if variant_name == "crop":
            processed.save("debug_crop.png")

        for psm in (6, 7, 8, 11, 13):
            config = f"{base_config} --psm {psm}"

            try:
                raw_text = pytesseract.image_to_string(
                    processed,
                    config=config
                ).strip()
            except Exception as exc:
                print(f"OCR error ({variant_name}, PSM {psm}): {exc}")
                continue

            digits = re.sub(r"\D", "", raw_text)

            print(
                f"OCR {variant_name}, PSM {psm}: "
                f"raw={raw_text!r}, digits={digits!r}"
            )

            previews.append(
                f"[{variant_name}/psm{psm}] {raw_text}"
            )

            # Only accept an exact eight-digit result.
            if re.fullmatch(r"\d{8}", digits):
                candidates.append(digits)

    # Prefer the most frequently returned exact eight-digit candidate.
    if candidates:
        counts = {}
        for candidate in candidates:
            counts[candidate] = counts.get(candidate, 0) + 1

        best_count = max(counts.values())
        best_candidates = [
            candidate
            for candidate, count in counts.items()
            if count == best_count
        ]

        # Do not guess if equally supported candidates disagree.
        if len(best_candidates) == 1:
            return best_candidates[0], "\n".join(previews)[:500]

        print("OCR candidates disagree:", counts)

    return None, "\n".join(previews)[:500]


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

    try:
        image = Image.open(
            request.files["image"].stream
        ).convert("RGB")
    except Exception:
        return jsonify({"error": "Invalid image"}), 400

    image.save("debug_screenshot.png")
    print("Screenshot size:", image.size)
    print("Image mode:", image.mode)

    found, ocr_preview = extract_booking_id(image)

    with lock:
        latest.update({
            "id": found,
            "created_at": time.time(),
            "status": "id_found" if found else "no_id_found",
            "ocr_preview": ocr_preview
        })
        response = latest.copy()

    print("FINAL RESULT:", response["status"], response["id"])

    return jsonify({
        "status": response["status"],
        "id": response["id"],
        "ocr_preview": response["ocr_preview"]
    })


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
            "status": "waiting",
            "ocr_preview": ""
        })
    return jsonify({"ok": True})


if __name__ == "__main__":
    print("Bridge listening on port 8765; keep this terminal open.")
    app.run(host="0.0.0.0", port=8765, debug=False)
