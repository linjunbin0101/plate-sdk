"""Test plate detection on images in Test_imgs folder."""
import os
import sys
import numpy as np
import cv2
import hyperlpr3

# force UTF-8 output
sys.stdout.reconfigure(encoding="utf-8")

test_dir = os.path.join(os.path.dirname(__file__), "Test_imgs")
images = [f for f in os.listdir(test_dir) if f.lower().endswith(('.jpg', '.png', '.jpeg'))]

print(f"Found {len(images)} image(s)")
catcher = hyperlpr3.LicensePlateCatcher()

for img_name in images:
    img_path = os.path.join(test_dir, img_name)
    print(f"\n{'='*50}")
    print(f"Image: {img_name}")
    with open(img_path, "rb") as f:
        data = np.frombuffer(f.read(), dtype=np.uint8)
    img = cv2.imdecode(data, cv2.IMREAD_COLOR)
    print(f"  Size: {img.shape[1]}x{img.shape[0]}")

    results = catcher.pipeline(img)
    if not results:
        print(f"  NO plate detected")
    else:
        print(f"  Found {len(results)} plate(s):")
        for i, r in enumerate(results):
            code, confidence, ptype, box = r
            x1, y1, x2, y2 = box
            print(f"  [{i+1}] plate={code}, confidence={confidence:.3f},"
                  f" type={ptype}, box=[{x1},{y1},{x2},{y2}]")

print("\nDone.")
