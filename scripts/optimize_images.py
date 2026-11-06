import os
from PIL import Image

ASSETS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "web", "assets"))

TARGET_WIDTHS = [480, 800, 1200]

def optimize_and_generate_responsive():
    print(f"Scanning assets in {ASSETS_DIR}...")
    
    # 1. Delete unreferenced PNG files
    png_files_to_delete = [
        "hero-3d-phone.png",
        "screen-backup.png",
        "screen-dashboard.png",
        "screen-details.png",
        "screen-new-record.png",
        "screen-people.png",
        "screen-record-filled.png",
        "screen-security.png",
        "screen-swipe.png",
        "screen-themes.png",
        "screen-whatsapp.png",
        "hero-mockup.png"
    ]
    
    for filename in png_files_to_delete:
        filepath = os.path.join(ASSETS_DIR, filename)
        if os.path.exists(filepath):
            os.remove(filepath)
            print(f"Deleted unreferenced large PNG: {filename}")

    # 2. Key screenshots to generate responsive sizes for
    source_webp_images = [
        "screen-dashboard.webp",
        "screen-new-record.webp",
        "screen-details.webp",
        "screen-whatsapp.webp",
        "screen-backup.webp",
        "og-image.webp"
    ]

    for img_name in source_webp_images:
        src_path = os.path.join(ASSETS_DIR, img_name)
        if not os.path.exists(src_path):
            print(f"Skipping {img_name} (not found)")
            continue

        base_name, _ = os.path.splitext(img_name)
        with Image.open(src_path) as img:
            orig_w, orig_h = img.size
            for target_w in TARGET_WIDTHS:
                if target_w >= orig_w:
                    continue
                ratio = target_w / float(orig_w)
                target_h = int(orig_h * ratio)
                resized = img.resize((target_w, target_h), Image.Resampling.LANCZOS)
                
                out_name = f"{base_name}-{target_w}.webp"
                out_path = os.path.join(ASSETS_DIR, out_name)
                resized.save(out_path, "WEBP", quality=85, method=6)
                print(f"Generated responsive WebP: {out_name} ({target_w}x{target_h})")

    # 3. Ensure hero-3d-phone.webp is replaced with clean emerald hero asset
    dash_src = os.path.join(ASSETS_DIR, "screen-dashboard.webp")
    hero_dst = os.path.join(ASSETS_DIR, "hero-3d-phone.webp")
    if os.path.exists(dash_src):
        with Image.open(dash_src) as img:
            img.save(hero_dst, "WEBP", quality=88)
            print("Replaced hero-3d-phone.webp with clean emerald dashboard screenshot")

    print("Image optimization and responsive sizing complete.")

if __name__ == "__main__":
    optimize_and_generate_responsive()
