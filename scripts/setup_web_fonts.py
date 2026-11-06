import os
import urllib.request
from fontTools.ttLib import TTFont

os.makedirs("web/fonts", exist_ok=True)

# 1. Convert local app fonts to woff2
fonts_to_convert = [
    ("app/src/main/res/font/inter.ttf", "web/fonts/inter.woff2"),
    ("app/src/main/res/font/space_grotesk.ttf", "web/fonts/space-grotesk.woff2"),
    ("app/src/main/res/font/ibm_plex_mono.ttf", "web/fonts/ibm-plex-mono.woff2"),
]

for src, dst in fonts_to_convert:
    if os.path.exists(src):
        f = TTFont(src)
        f.flavor = "woff2"
        f.save(dst)
        print(f"Converted {src} -> {dst} ({os.path.getsize(dst)} bytes)")

# 2. Download subsetted Noto Sans Devanagari & Bengali WOFF2
remote_fonts = [
    ("https://fonts.gstatic.com/s/notosansdevanagari/v33/TuG7UUFzXI5FBtUq5a8bjKYTZjtRU6Sgv3NaV_SNmI0b8QQCQmHN5TV_9qo.woff2", "web/fonts/noto-sans-devanagari.woff2"),
    ("https://fonts.gstatic.com/s/notosansbengali/v33/Cn-fJsCGWQxOjaGwMQ6fIiMywrNJIky6nvd8BjzVMvJx2mc4I3mYvNY.woff2", "web/fonts/noto-sans-bengali.woff2"),
]

for url, dst in remote_fonts:
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=10) as resp:
        data = resp.read()
        with open(dst, "wb") as f:
            f.write(data)
    print(f"Downloaded {dst} ({os.path.getsize(dst)} bytes)")

print("All self-hosted fonts created successfully in web/fonts/")
