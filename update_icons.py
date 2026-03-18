
import os
import subprocess
import sys

# Source logo
LOGO_PATH = r"C:\VG\logo.png"
RES_PATH = r"C:\VG\android\app\src\main\res"

MIPMAPS = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

def install_pillow():
    print("Installing Pillow...")
    try:
        subprocess.check_call([sys.executable, "-m", "pip", "install", "Pillow"])
        print("Pillow installed successfully.")
    except Exception as e:
        print(f"Failed to install Pillow: {e}")
        sys.exit(1)

def update_icons():
    try:
        from PIL import Image
    except ImportError:
        install_pillow()
        from PIL import Image

    if not os.path.exists(LOGO_PATH):
        print(f"Error: Logo not found at {LOGO_PATH}")
        return

    try:
        img = Image.open(LOGO_PATH)
        print("Logo loaded successfully.")
    except Exception as e:
        print(f"Error loading logo: {e}")
        return

    for folder, size in MIPMAPS.items():
        folder_path = os.path.join(RES_PATH, folder)
        if not os.path.exists(folder_path):
            try:
                os.makedirs(folder_path)
            except OSError:
                continue
        
        try:
            # Launcher Icon
            icon = img.resize((size, size), Image.Resampling.LANCZOS)
            icon.save(os.path.join(folder_path, "ic_launcher.png"))
            icon.save(os.path.join(folder_path, "ic_launcher_round.png"))
            print(f"Updated {folder} ({size}x{size})")
        except Exception as e:
            print(f"Failed to update {folder}: {e}")

if __name__ == "__main__":
    update_icons()
