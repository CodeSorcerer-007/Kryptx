import os
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

WIDTH, HEIGHT = 960, 540
OUTPUT_DIR = "brag-output"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Cyber aesthetic palette
COLOR_BG = (6, 9, 17, 255)
COLOR_GRID = (20, 28, 48, 80)
COLOR_EMERALD = (0, 230, 118)
COLOR_CYAN = (0, 212, 255)
COLOR_VIOLET = (139, 92, 246)
COLOR_CRIMSON = (255, 30, 75)
COLOR_AMBER = (255, 180, 0)
COLOR_TEXT_WHITE = (248, 250, 252)
COLOR_TEXT_MUTED = (148, 163, 184)
COLOR_TEXT_DARK = (100, 116, 139)
COLOR_CARD_BORDER = (28, 42, 68, 255)
COLOR_CARD_BG = (14, 22, 38, 235)

# Fonts
try:
    font_hero = ImageFont.truetype("C:/Windows/Fonts/seguibl.ttf", 23)
    font_hero_sub = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 15)
    font_card_title = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 13)
    font_card_body = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 11)
    font_mono_bold = ImageFont.truetype("C:/Windows/Fonts/consolab.ttf", 11)
    font_mono_sm = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 10)
    font_badge = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 11)
    font_badge_sm = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 9)
    font_phone_title = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 13)
    font_phone_sub = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 11)
    font_phone_body = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 10)
    font_phone_mono = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 9)
except Exception:
    font_hero = ImageFont.load_default()
    font_hero_sub = ImageFont.load_default()
    font_card_title = ImageFont.load_default()
    font_card_body = ImageFont.load_default()
    font_mono_bold = ImageFont.load_default()
    font_mono_sm = ImageFont.load_default()
    font_badge = ImageFont.load_default()
    font_badge_sm = ImageFont.load_default()
    font_phone_title = ImageFont.load_default()
    font_phone_sub = ImageFont.load_default()
    font_phone_body = ImageFont.load_default()
    font_phone_mono = ImageFont.load_default()

# Load Logo
logo_img = None
logo_large = None
if os.path.exists("Logo.png"):
    try:
        raw = Image.open("Logo.png").convert("RGBA")
        logo_img = raw.resize((68, 68), Image.Resampling.LANCZOS)
        logo_large = raw.resize((110, 110), Image.Resampling.LANCZOS)
    except Exception as e:
        print("Logo error:", e)

def create_base_canvas(t, progress_val, accent_color=COLOR_EMERALD):
    base = Image.new("RGBA", (WIDTH, HEIGHT), COLOR_BG)
    bdraw = ImageDraw.Draw(base)
    
    # Perspective Cyber Grid
    grid_spacing = 34
    for x in range(0, WIDTH, grid_spacing):
        bdraw.line([(x, 0), (x, HEIGHT)], fill=COLOR_GRID, width=1)
    for y in range(0, HEIGHT, grid_spacing):
        bdraw.line([(0, y), (WIDTH, y)], fill=COLOR_GRID, width=1)
        
    # Ambient Moving Glow Orbs
    glow_layer = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow_layer)
    
    glow1_x = int(220 + 40 * math.sin(t * 1.5))
    glow1_y = int(240 + 30 * math.cos(t * 1.2))
    glow2_x = int(740 + 35 * math.cos(t * 1.4))
    glow2_y = int(300 + 30 * math.sin(t * 1.1))
    
    # Left accent glow
    gdraw.ellipse([glow1_x - 90, glow1_y - 90, glow1_x + 90, glow1_y + 90], fill=(accent_color[0], accent_color[1], accent_color[2], 55))
    # Right cyan/violet aura behind phone
    gdraw.ellipse([glow2_x - 110, glow2_y - 110, glow2_x + 110, glow2_y + 110], fill=(0, 212, 255, 45))
    
    glow_blurred = glow_layer.filter(ImageFilter.GaussianBlur(radius=65))
    base = Image.alpha_composite(base, glow_blurred)
    
    # Foreground elements layer
    fg_layer = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    fg_draw = ImageDraw.Draw(fg_layer)
    
    # Floating particles
    for i in range(14):
        px = (int(i * 73 + t * 24)) % WIDTH
        py = (int(i * 43 - t * 32)) % HEIGHT
        pr = 1 if i % 2 == 0 else 2
        pcol = COLOR_EMERALD if i % 3 == 0 else (COLOR_CYAN if i % 3 == 1 else COLOR_VIOLET)
        fg_draw.ellipse([px - pr, py - pr, px + pr, py + pr], fill=(pcol[0], pcol[1], pcol[2], 120))
        
    # Top HUD Bar
    fg_draw.rectangle([0, 0, WIDTH, 36], fill=(10, 15, 26, 250))
    fg_draw.line([(0, 36), (WIDTH, 36)], fill=(24, 36, 58, 255), width=1)
    
    # Pulse dot + Title
    pulse_alpha = int(180 + 75 * math.sin(t * 4))
    fg_draw.ellipse([18, 13, 27, 22], fill=(0, 230, 118, pulse_alpha))
    fg_draw.text((34, 10), "KRYPTX AIR-GAPPED FORTRESS v2.4.0", fill=COLOR_TEXT_WHITE, font=font_badge)
    
    # Top right telemetry badges
    draw_hud_badge(fg_draw, "0 NETWORK PERMISSIONS", (WIDTH - 460, 8), COLOR_EMERALD)
    draw_hud_badge(draw=fg_draw, text="POST-QUANTUM ML-KEM-768", xy=(WIDTH - 280, 8), color=COLOR_CYAN)
    draw_hud_badge(draw=fg_draw, text="603/603 TESTS 100%", xy=(WIDTH - 110, 8), color=COLOR_VIOLET)
    
    # Animated Laser Scanline
    scan_y = int(HEIGHT * ((t * 0.4) % 1.0))
    fg_draw.line([(0, scan_y), (WIDTH, scan_y)], fill=(accent_color[0], accent_color[1], accent_color[2], 55), width=1)
    
    # Bottom HUD Progress Bar
    fg_draw.line([(0, HEIGHT - 3), (WIDTH, HEIGHT - 3)], fill=(17, 24, 39, 255), width=3)
    prog_w = int(WIDTH * progress_val)
    if prog_w > 0:
        fg_draw.line([(0, HEIGHT - 3), (prog_w, HEIGHT - 3)], fill=COLOR_EMERALD, width=3)
        fg_draw.ellipse([prog_w - 3, HEIGHT - 5, prog_w + 3, HEIGHT - 1], fill=COLOR_CYAN)
        
    res = Image.alpha_composite(base, fg_layer)
    return res

def draw_hud_badge(draw, text, xy, color):
    x, y = xy
    bbox = font_badge_sm.getbbox(text)
    w = bbox[2] - bbox[0] + 14
    h = bbox[3] - bbox[1] + 8
    draw.rounded_rectangle([x, y, x + w, y + h], radius=5, fill=(16, 24, 40, 230), outline=(color[0], color[1], color[2], 120), width=1)
    draw.text((x + 7, y + 3), text, fill=color, font=font_badge_sm)
    return w, h

def draw_pill(draw, text, xy, color, bg_alpha=35):
    x, y = xy
    bbox = font_badge.getbbox(text)
    w = bbox[2] - bbox[0] + 18
    h = bbox[3] - bbox[1] + 10
    draw.rounded_rectangle([x, y, x + w, y + h], radius=6, fill=(color[0], color[1], color[2], bg_alpha), outline=(color[0], color[1], color[2], 220), width=1)
    draw.text((x + 9, y + 4), text, fill=color, font=font_badge)
    return w, h

def render_phone(screen_img, t):
    pw, ph = 250, 470
    phone = Image.new("RGBA", (pw, ph), (0, 0, 0, 0))
    pdraw = ImageDraw.Draw(phone)
    
    # Outer frame shadow / glow
    pdraw.rounded_rectangle([0, 0, pw - 1, ph - 1], radius=26, fill=(18, 25, 40, 255), outline=(45, 65, 95, 255), width=2)
    pdraw.rounded_rectangle([2, 2, pw - 3, ph - 3], radius=24, fill=None, outline=(28, 40, 62, 180), width=1)
    
    # Screen Cutout Mask
    sx, sy, sw, sh = 8, 8, pw - 16, ph - 16
    screen_mask = Image.new("L", (sw, sh), 0)
    smask_draw = ImageDraw.Draw(screen_mask)
    smask_draw.rounded_rectangle([0, 0, sw, sh], radius=20, fill=255)
    
    # Scaled screen content
    scaled_screen = screen_img.resize((sw, sh), Image.Resampling.LANCZOS)
    phone.paste(scaled_screen, (sx, sy), screen_mask)
    
    # Dynamic Island / Camera Notch
    nw, nh = 46, 12
    nx = (pw - nw) // 2
    pdraw.rounded_rectangle([nx, 13, nx + nw, 13 + nh], radius=6, fill=(4, 7, 12, 255))
    pdraw.ellipse([nx + 8, 16, nx + 14, 22], fill=(15, 23, 42, 255))
    
    # Specular Edge Shimmer on Frame
    shimmer_y = int(ph * ((t * 0.35) % 1.0))
    pdraw.line([(0, max(0, shimmer_y - 25)), (0, min(ph, shimmer_y + 25))], fill=(0, 212, 255, 190), width=2)
    pdraw.line([(pw - 1, max(0, shimmer_y - 25)), (pw - 1, min(ph, shimmer_y + 25))], fill=(0, 230, 118, 190), width=2)
    
    return phone

# Phone Screen 1: Lock & Biometrics / YubiKey Hardware
def render_screen_lock(w, h, t):
    im = Image.new("RGBA", (w, h), (9, 13, 24, 255))
    draw = ImageDraw.Draw(im)
    
    # Status bar
    draw.text((12, 6), "09:41", fill=COLOR_TEXT_WHITE, font=font_phone_mono)
    draw.text((w - 60, 6), "100% [OFF]", fill=COLOR_EMERALD, font=font_phone_mono)
    
    # Soft ambient glow behind lock logo
    glow_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow_layer)
    cx, cy = w // 2, 85
    gdraw.ellipse([cx - 45, cy - 45, cx + 45, cy + 45], fill=(0, 212, 255, 80))
    glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(radius=20))
    im = Image.alpha_composite(im, glow_layer)
    draw = ImageDraw.Draw(im)
    
    if logo_img:
        im.paste(logo_img, (cx - 34, cy - 34), logo_img)
        
    draw.text((cx - 38, cy + 40), "KRYPTX VAULT", fill=COLOR_TEXT_WHITE, font=font_phone_title)
    draw.text((cx - 48, cy + 58), "Touch sensor to decrypt", fill=COLOR_TEXT_MUTED, font=font_phone_body)
    
    # Master Password masked input box
    draw.rounded_rectangle([20, 180, w - 20, 215], radius=8, fill=(15, 23, 42, 255), outline=(0, 212, 255, 180), width=1)
    dots = "●  ●  ●  ●  ●  ●  ●  ●"
    draw.text((42, 190), dots, fill=COLOR_CYAN, font=font_phone_title)
    
    # Biometric Shockwave Rings on separate layer
    bio_y = 290
    bio_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bdraw = ImageDraw.Draw(bio_layer)
    
    pulse = (t * 2.5) % 1.0
    for i in range(3):
        r_pulse = int(22 + ((pulse + i * 0.33) % 1.0) * 36)
        alpha = int(140 * (1.0 - (((pulse + i * 0.33) % 1.0))))
        bdraw.ellipse([cx - r_pulse, bio_y - r_pulse, cx + r_pulse, bio_y + r_pulse], outline=(0, 230, 118, alpha), width=1)
        
    # Center Fingerprint Sensor Circle
    bdraw.ellipse([cx - 24, bio_y - 24, cx + 24, bio_y + 24], fill=(16, 28, 48, 255), outline=COLOR_EMERALD, width=2)
    bdraw.arc([cx - 14, bio_y - 14, cx + 14, bio_y + 14], start=30, end=330, fill=COLOR_CYAN, width=2)
    bdraw.arc([cx - 8, bio_y - 8, cx + 8, bio_y + 8], start=50, end=310, fill=COLOR_EMERALD, width=2)
    
    im = Image.alpha_composite(im, bio_layer)
    draw = ImageDraw.Draw(im)
    
    draw.text((cx - 46, bio_y + 36), "BIOMETRIC SENSOR", fill=COLOR_EMERALD, font=font_phone_mono)
    draw.text((cx - 52, bio_y + 50), "YubiKey NFC Slot-2 Ready", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    
    # Bottom Security Pill
    draw.rounded_rectangle([18, h - 34, w - 18, h - 12], radius=6, fill=(12, 22, 38, 255), outline=(0, 230, 118, 120), width=1)
    draw.text((cx - 68, h - 28), "100% AIR-GAPPED • ZERO NETWORK", fill=COLOR_EMERALD, font=font_phone_mono)
    
    return im

# Phone Screen 2: Sovereign Vault & 1-Tap Autofill
def render_screen_vault(w, h, t):
    im = Image.new("RGBA", (w, h), (9, 13, 24, 255))
    draw = ImageDraw.Draw(im)
    
    # Status bar
    draw.text((12, 6), "09:41", fill=COLOR_TEXT_WHITE, font=font_phone_mono)
    draw.text((w - 60, 6), "100% [OFF]", fill=COLOR_EMERALD, font=font_phone_mono)
    
    # Search bar
    draw.rounded_rectangle([14, 28, w - 14, 56], radius=8, fill=(15, 23, 42, 255), outline=(28, 42, 68, 255), width=1)
    draw.text((26, 36), "Search 42 sovereign vault items...", fill=COLOR_TEXT_MUTED, font=font_phone_body)
    
    # Health Score Pill
    draw.rounded_rectangle([14, 66, w - 14, 102], radius=8, fill=(14, 26, 46, 255), outline=(0, 230, 118, 140), width=1)
    draw.ellipse([26, 75, 44, 93], outline=COLOR_EMERALD, width=2)
    draw.text((30, 78), "98", fill=COLOR_EMERALD, font=font_phone_mono)
    draw.text((54, 73), "Security Score: Excellent", fill=COLOR_TEXT_WHITE, font=font_phone_title)
    draw.text((54, 87), "0 breached • 100% offline audit", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    
    # Vault Item 1: GitHub
    draw.rounded_rectangle([14, 112, w - 14, 160], radius=8, fill=(15, 23, 42, 230), outline=COLOR_CARD_BORDER, width=1)
    draw.rounded_rectangle([24, 122, 48, 146], radius=6, fill=(24, 34, 56, 255))
    draw.text((31, 127), "GH", fill=COLOR_TEXT_WHITE, font=font_phone_sub)
    draw.text((58, 120), "GitHub Enterprise", fill=COLOR_TEXT_WHITE, font=font_phone_title)
    draw.text((58, 137), "@CodeSorcerer-007 • 2FA Active", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    draw_hud_badge(draw, "2FA", (w - 44, 124), COLOR_CYAN)
    
    # Vault Item 2: AWS Console
    draw.rounded_rectangle([14, 168, w - 14, 216], radius=8, fill=(15, 23, 42, 230), outline=COLOR_CARD_BORDER, width=1)
    draw.rounded_rectangle([24, 178, 48, 202], radius=6, fill=(24, 34, 56, 255))
    draw.text((29, 183), "AWS", fill=COLOR_AMBER, font=font_phone_sub)
    draw.text((58, 176), "AWS Root Security", fill=COLOR_TEXT_WHITE, font=font_phone_title)
    draw.text((58, 193), "prod-cluster • YubiKey Slot-2", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    draw_hud_badge(draw, "NFC", (w - 44, 180), COLOR_EMERALD)
    
    # Vault Item 3: Proton Mail
    draw.rounded_rectangle([14, 224, w - 14, 272], radius=8, fill=(15, 23, 42, 230), outline=COLOR_CARD_BORDER, width=1)
    draw.rounded_rectangle([24, 234, 48, 258], radius=6, fill=(24, 34, 56, 255))
    draw.text((30, 239), "PM", fill=COLOR_VIOLET, font=font_phone_sub)
    draw.text((58, 232), "Proton Mail Secure", fill=COLOR_TEXT_WHITE, font=font_phone_title)
    draw.text((58, 249), "pgp-master • AES-256-GCM", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    draw_hud_badge(draw, "AES", (w - 44, 236), COLOR_VIOLET)
    
    # Floating 1-Tap Autofill Prompt (Active Simulation)
    draw.rounded_rectangle([10, 286, w - 10, 428], radius=10, fill=(13, 25, 46, 250), outline=(0, 212, 255, 200), width=1)
    draw.text((22, 296), "1-TAP ANTI-PHISHING AUTOFILL", fill=COLOR_CYAN, font=font_phone_sub)
    draw.text((22, 314), "Service: paypal.com", fill=COLOR_TEXT_WHITE, font=font_phone_title)
    draw.text((22, 332), "User: sovereign@kryptx.dev", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    draw.rounded_rectangle([22, 350, w - 22, 372], radius=5, fill=(18, 38, 64, 255), outline=(0, 230, 118, 160), width=1)
    draw.text((28, 354), "[PASS] Strict Domain Match: Phishing Blocked", fill=COLOR_EMERALD, font=font_phone_mono)
    # 1-Tap Fill Button
    draw.rounded_rectangle([22, 382, w - 22, 414], radius=6, fill=COLOR_CYAN)
    draw.text((w // 2 - 38, 392), "1-TAP FILL", fill=(5, 12, 24), font=font_phone_title)
    
    return im

# Phone Screen 3: Post-Quantum & Dual-Cipher RAM Engine
def render_screen_crypto(w, h, t):
    im = Image.new("RGBA", (w, h), (9, 13, 24, 255))
    draw = ImageDraw.Draw(im)
    
    # Status bar
    draw.text((12, 6), "09:41", fill=COLOR_TEXT_WHITE, font=font_phone_mono)
    draw.text((w - 60, 6), "100% [OFF]", fill=COLOR_EMERALD, font=font_phone_mono)
    
    draw.text((16, 26), "CRYPTOGRAPHIC CORE", fill=COLOR_VIOLET, font=font_phone_mono)
    draw.text((16, 42), "Post-Quantum Fortress", fill=COLOR_TEXT_WHITE, font=font_phone_title)
    
    # Matrix hex stream animation
    hex_lines = [
        "0x7F 0x45 0x4C 0x46 0x02 0x01 0x01",
        "0x5A 0x8F 0x3C 0x1B 0x7E 0x90 0x4A",
        "0xD4 0x2E 0xA9 0xF1 0x88 0x33 0x6C",
        "0x10 0xBC 0xFE 0x05 0x77 0x82 0x9D"
    ]
    draw.rounded_rectangle([14, 68, w - 14, 150], radius=8, fill=(12, 16, 28, 255), outline=(139, 92, 246, 120), width=1)
    for idx, l in enumerate(hex_lines):
        col = COLOR_CYAN if idx % 2 == 0 else COLOR_EMERALD
        draw.text((22, 76 + idx * 17), l, fill=col, font=font_phone_mono)
        
    # Card 1: ML-KEM-768
    draw.rounded_rectangle([14, 160, w - 14, 224], radius=8, fill=(15, 23, 42, 240), outline=(0, 212, 255, 160), width=1)
    draw.text((24, 170), "NIST FIPS 203 (ML-KEM-768)", fill=COLOR_CYAN, font=font_phone_sub)
    draw.text((24, 188), "Post-quantum lattice key encapsulation.", fill=COLOR_TEXT_WHITE, font=font_phone_body)
    draw.text((24, 204), "Zero decryption by quantum attacks.", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    
    # Card 2: Dual Rust Ciphers
    draw.rounded_rectangle([14, 234, w - 14, 298], radius=8, fill=(15, 23, 42, 240), outline=(0, 230, 118, 160), width=1)
    draw.text((24, 244), "DUAL BARE-METAL CIPHERS", fill=COLOR_EMERALD, font=font_phone_sub)
    draw.text((24, 262), "XChaCha20-Poly1305 + AES-256-GCM", fill=COLOR_TEXT_WHITE, font=font_phone_body)
    draw.text((24, 278), "Enforced by cargo clippy JNI binaries.", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    
    # Card 3: Linux mlock() Memory Lock
    draw.rounded_rectangle([14, 308, w - 14, 388], radius=8, fill=(18, 26, 46, 255), outline=(139, 92, 246, 180), width=1)
    draw.text((24, 318), "LINUX mlock() RAM SHIELD", fill=COLOR_VIOLET, font=font_phone_sub)
    draw.text((24, 336), "Keys locked in physical volatile RAM.", fill=COLOR_TEXT_WHITE, font=font_phone_body)
    draw.text((24, 352), "MADV_DONTDUMP prevents flash swap.", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    draw.text((24, 368), "Two-pass zeroization on close.", fill=COLOR_EMERALD, font=font_phone_mono)
    
    # Bottom Status
    draw.rounded_rectangle([14, 398, w - 14, 428], radius=6, fill=(14, 22, 38, 255), outline=(0, 212, 255, 120), width=1)
    draw.text((24, 406), "PHYSICAL RAM ISOLATION ACTIVE", fill=COLOR_CYAN, font=font_phone_mono)
    
    return im

# Phone Screen 4: Duress Decoy & Forensic Flash Shredder
def render_screen_duress(w, h, t):
    im = Image.new("RGBA", (w, h), (18, 12, 22, 255))
    draw = ImageDraw.Draw(im)
    
    # Status bar
    draw.text((12, 6), "09:41", fill=COLOR_TEXT_WHITE, font=font_phone_mono)
    draw.text((w - 74, 6), "DURESS MODE", fill=COLOR_CRIMSON, font=font_phone_mono)
    
    # Emergency Warning Header
    draw.text((16, 26), "ANTI-COERCION DEFENSE", fill=COLOR_CRIMSON, font=font_phone_mono)
    draw.text((16, 42), "Duress Decoy & Shredder", fill=COLOR_TEXT_WHITE, font=font_phone_title)
    
    # Duress PIN simulation card
    draw.rounded_rectangle([14, 68, w - 14, 180], radius=8, fill=(28, 16, 24, 240), outline=(255, 30, 75, 180), width=1)
    draw.text((24, 78), "DURESS DECOY VAULT", fill=COLOR_CRIMSON, font=font_phone_sub)
    draw.text((24, 98), "Secondary PIN opened decoy DB.", fill=COLOR_TEXT_WHITE, font=font_phone_body)
    draw.text((24, 116), "Primary vault remains encrypted & hidden.", fill=COLOR_TEXT_MUTED, font=font_phone_body)
    draw.rounded_rectangle([24, 138, w - 24, 166], radius=5, fill=(40, 18, 28, 255))
    draw.text((32, 146), "Decoy: 8 realistic dummy logins", fill=COLOR_TEXT_WHITE, font=font_phone_mono)
    
    # Forensic Flash Shredder card
    draw.rounded_rectangle([14, 192, w - 14, 340], radius=8, fill=(28, 16, 24, 240), outline=(255, 180, 0, 180), width=1)
    draw.text((24, 202), "FORENSIC DISK SHREDDER", fill=COLOR_AMBER, font=font_phone_sub)
    draw.text((24, 222), "CSPRNG multi-pass sector wipe.", fill=COLOR_TEXT_WHITE, font=font_phone_body)
    draw.text((24, 240), "Synchronous fd.sync() write barrier.", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    draw.text((24, 258), "Wipes -wal and -shm SQLite journals.", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    
    # Shredding Progress Simulation
    draw.rounded_rectangle([24, 280, w - 24, 300], radius=4, fill=(40, 24, 20, 255))
    shred_prog = int((w - 48) * (0.85 + 0.15 * math.sin(t * 3)))
    draw.rounded_rectangle([24, 280, 24 + shred_prog, 300], radius=4, fill=COLOR_CRIMSON)
    draw.text((26, 310), "ZERO FORENSIC DATA RECOVERABLE", fill=COLOR_CRIMSON, font=font_phone_mono)
    
    # Emergency Action button
    draw.rounded_rectangle([14, 360, w - 14, 404], radius=8, fill=(60, 16, 24, 255), outline=COLOR_CRIMSON, width=1)
    draw.text((w // 2 - 58, 376), "PURGE COMPLETED", fill=COLOR_TEXT_WHITE, font=font_phone_title)
    
    return im

# Phone Screen 5: Outro - Simple. Human. Sovereign.
def render_screen_outro(w, h, t):
    im = Image.new("RGBA", (w, h), (9, 13, 24, 255))
    draw = ImageDraw.Draw(im)
    
    # Status bar
    draw.text((12, 6), "09:41", fill=COLOR_TEXT_WHITE, font=font_phone_mono)
    draw.text((w - 60, 6), "100% [OFF]", fill=COLOR_EMERALD, font=font_phone_mono)
    
    # Ambient glow behind outro logo
    glow_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow_layer)
    cx, cy = w // 2, 130
    gdraw.ellipse([cx - 50, cy - 50, cx + 50, cy + 50], fill=(0, 230, 118, 90))
    glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(radius=25))
    im = Image.alpha_composite(im, glow_layer)
    draw = ImageDraw.Draw(im)
    
    if logo_large:
        logo_phone = logo_large.resize((84, 84), Image.Resampling.LANCZOS)
        im.paste(logo_phone, (cx - 42, cy - 42), logo_phone)
        
    draw.text((cx - 36, cy + 54), "KRYPTX", fill=COLOR_TEXT_WHITE, font=font_phone_title)
    draw.text((cx - 62, cy + 74), "Simple. Human. Sovereign.", fill=COLOR_EMERALD, font=font_phone_mono)
    
    # Key Badges
    items = [
        ("603 UNIT TESTS", "100% Pass Rate (0 Failures)", COLOR_EMERALD),
        ("120 FPS FLUID", "High-Refresh Compose Engine", COLOR_CYAN),
        ("ANDROID 16", "Platform Target SDK 36 Ready", COLOR_VIOLET),
        ("APACHE 2.0", "100% Free & Open Source", COLOR_AMBER)
    ]
    
    by = cy + 105
    for title, desc, col in items:
        draw.rounded_rectangle([14, by, w - 14, by + 38], radius=6, fill=(14, 22, 38, 240), outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([24, by + 13, 34, by + 23], fill=col)
        draw.text((44, by + 6), title, fill=COLOR_TEXT_WHITE, font=font_phone_sub)
        draw.text((44, by + 21), desc, fill=COLOR_TEXT_MUTED, font=font_phone_mono)
        by += 44
        
    return im

def render_left_panel_overlay(scene_id, t, enter_offset=0):
    overlay = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    lx = 48
    ly = 65 + enter_offset
    
    if scene_id == 1:
        # Hook: Air-Gapped Kernel Isolation & Hardware Keys
        draw_pill(draw, "// AIR-GAPPED SOVEREIGNTY", (lx, ly), COLOR_EMERALD, bg_alpha=35)
        
        draw.text((lx, ly + 36), "WHAT IF YOUR PASSWORD MANAGER", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 68), "COULD NEVER BE HACKED?", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 102), "Because it physically cannot touch the internet.", fill=COLOR_EMERALD, font=font_hero_sub)
        
        desc = ("Zero sockets. Zero telemetry. Zero servers to breach. The Android\n"
                "Linux kernel physically blocks network sockets at system level.\n"
                "Even if physical hardware is lost, 0 bytes can leave your phone.")
        draw.text((lx, ly + 132), desc, fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 1
        c1_y = ly + 195
        draw.rounded_rectangle([lx, c1_y, lx + 540, c1_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c1_y + 18, lx + 30, c1_y + 30], fill=COLOR_EMERALD)
        draw.text((lx + 42, c1_y + 12), "0 Network Permissions Enforced", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c1_y + 34), "Look at AndroidManifest.xml: android.permission.INTERNET does not exist.\nAll network creation attempts return -EACCES kernel socket errors.", fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 2
        c2_y = c1_y + 80
        draw.rounded_rectangle([lx, c2_y, lx + 540, c2_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c2_y + 18, lx + 30, c2_y + 30], fill=COLOR_CYAN)
        draw.text((lx + 42, c2_y + 12), "StrongBox TEE & Hardware YubiKeys", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c2_y + 34), "Class-3 biometrics with ECDH forward key agreement, physical YubiKey\nNFC IsoDep (HMAC-SHA1 Slot 2 instruction 0x38), and FIDO2 BLE keys.", fill=COLOR_TEXT_MUTED, font=font_card_body)

    elif scene_id == 2:
        # Sovereign Vault & 1-Tap Autofill
        draw_pill(draw, "// 1-TAP PHISHING SHIELD", (lx, ly), COLOR_CYAN, bg_alpha=35)
        
        draw.text((lx, ly + 36), "1-TAP ANTI-PHISHING AUTOFILL", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 68), "WITH 100% OFFLINE AUDIT", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 102), "Effortless speed without compromising mathematics.", fill=COLOR_CYAN, font=font_hero_sub)
        
        desc = ("Strict terminal-segment DomainMatcher prevents phishing attacks\n"
                "from lookalikes like 'evil-paypal.com'. Built-in Bloom filter checks\n"
                "billions of breached credentials 100% offline with zero cloud queries.")
        draw.text((lx, ly + 132), desc, fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 1
        c1_y = ly + 195
        draw.rounded_rectangle([lx, c1_y, lx + 540, c1_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c1_y + 18, lx + 30, c1_y + 30], fill=COLOR_CYAN)
        draw.text((lx + 42, c1_y + 12), "Phishing Shield (DomainMatcher)", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c1_y + 34), "Strict host boundaries verify top-level domains before providing autofill.\nNever falls for substring tricks or lookalike homograph phishing.", fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 2
        c2_y = c1_y + 80
        draw.rounded_rectangle([lx, c2_y, lx + 540, c2_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c2_y + 18, lx + 30, c2_y + 30], fill=COLOR_EMERALD)
        draw.text((lx + 42, c2_y + 12), "100% Offline Breach Checker", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c2_y + 34), "In-memory Bloom filter k-Anonymity HIBP verification with zero network\nqueries. Your credential hashes are never transmitted to external APIs.", fill=COLOR_TEXT_MUTED, font=font_card_body)

    elif scene_id == 3:
        # Post-Quantum & Dual Rust Ciphers
        draw_pill(draw, "// POST-QUANTUM CRYPTOGRAPHY", (lx, ly), COLOR_VIOLET, bg_alpha=35)
        
        draw.text((lx, ly + 36), "NIST ML-KEM-768 & DUAL CIPHERS", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 68), "MATHEMATICALLY IMPENETRABLE", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 102), "Quantum-resistant defense for the next century.", fill=COLOR_VIOLET, font=font_hero_sub)
        
        desc = ("Fortified with NIST FIPS 203 ML-KEM-768 hybrid key encapsulation,\n"
                "ML-DSA-65 signatures, and dual bare-metal Rust 2024 JNI engines\n"
                "across all 4 Android ABIs (arm64, armv7, x86, x86_64).")
        draw.text((lx, ly + 132), desc, fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 1
        c1_y = ly + 195
        draw.rounded_rectangle([lx, c1_y, lx + 540, c1_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c1_y + 18, lx + 30, c1_y + 30], fill=COLOR_VIOLET)
        draw.text((lx + 42, c1_y + 12), "Dual Native Rust Engines", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c1_y + 34), "Rust XChaCha20-Poly1305 with authenticated length-prefixed AAD framing\nplus NIST AES-256-GCM with deterministic 96-bit nonces.", fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 2
        c2_y = c1_y + 80
        draw.rounded_rectangle([lx, c2_y, lx + 540, c2_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c2_y + 18, lx + 30, c2_y + 30], fill=COLOR_CYAN)
        draw.text((lx + 42, c2_y + 12), "Linux mlock() & Memory Lock", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c2_y + 34), "Cryptographic keys are locked in physical RAM via Linux mlock() system calls.\nMADV_DONTDUMP prevents flash storage swapping; zeroized on close.", fill=COLOR_TEXT_MUTED, font=font_card_body)

    elif scene_id == 4:
        # Anti-Coercion & Panic Shredder
        draw_pill(draw, "// ANTI-COERCION DEFENSE", (lx, ly), COLOR_CRIMSON, bg_alpha=35)
        
        draw.text((lx, ly + 36), "DURESS DECOY VAULT &", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 68), "FORENSIC DISK SHREDDER", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 102), "Protection when you are physically forced to unlock.", fill=COLOR_CRIMSON, font=font_hero_sub)
        
        desc = ("If forced to unlock your phone under duress, enter your secondary\n"
                "Duress PIN to open an innocent decoy vault with realistic fake accounts.\n"
                "Emergency wipe triggers CSPRNG multi-pass flash sector shredding.")
        draw.text((lx, ly + 132), desc, fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 1
        c1_y = ly + 195
        draw.rounded_rectangle([lx, c1_y, lx + 540, c1_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c1_y + 18, lx + 30, c1_y + 30], fill=COLOR_CRIMSON)
        draw.text((lx + 42, c1_y + 12), "Duress Decoy Vault (Secondary PIN)", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c1_y + 34), "100% isolated fake SQLite database satisfies coercers under threat.\nZero leakage of primary master key, salts, or genuine accounts.", fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 2
        c2_y = c1_y + 80
        draw.rounded_rectangle([lx, c2_y, lx + 540, c2_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c2_y + 18, lx + 30, c2_y + 30], fill=COLOR_AMBER)
        draw.text((lx + 42, c2_y + 12), "Forensic Flash Sector Shredder", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c2_y + 34), "Multi-pass CSPRNG random sector overwrite with synchronous fd.sync()\nbarrier. Wipes and shreds -wal and -shm journals before unlinking.", fill=COLOR_TEXT_MUTED, font=font_card_body)

    elif scene_id == 5:
        # Outro: Simple. Human. Sovereign.
        draw_pill(draw, "// THE GOLD STANDARD OF SECURITY", (lx, ly), COLOR_VIOLET, bg_alpha=35)
        
        draw.text((lx, ly + 36), "KRYPTX: SIMPLE. HUMAN.", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 68), "SOVEREIGN.", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 102), "The Last Password Manager You'll Ever Need.", fill=COLOR_EMERALD, font=font_hero_sub)
        
        desc = ("Built from the ground up from the user's perspective. Fluid 120 FPS\n"
                "Compose animations, instant biometric access, and complete mathematical\n"
                "sovereignty. 100% Free & Open Source under Apache 2.0.")
        draw.text((lx, ly + 132), desc, fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 1
        c1_y = ly + 195
        draw.rounded_rectangle([lx, c1_y, lx + 540, c1_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c1_y + 18, lx + 30, c1_y + 30], fill=COLOR_EMERALD)
        draw.text((lx + 42, c1_y + 12), "603 Unit Tests Passing (100% Pass Rate)", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c1_y + 34), "Zero test failures, zero regressions, SLSA Level 2 build provenance,\nand OpenSSF supply-chain verification on every release build.", fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 2
        c2_y = c1_y + 80
        draw.rounded_rectangle([lx, c2_y, lx + 540, c2_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c2_y + 18, lx + 30, c2_y + 30], fill=COLOR_CYAN)
        draw.text((lx + 42, c2_y + 12), "Google Play Store & Standalone APKs", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c2_y + 34), "Available on Google Play Store with Play Protect verification,\nplus direct standalone signed APKs on GitHub Releases for air-gap devices.", fill=COLOR_TEXT_MUTED, font=font_card_body)
        
    return overlay

def render_frame(scene_id, t, progress_val, enter_offset=0):
    accent = COLOR_EMERALD if scene_id == 1 else (COLOR_CYAN if scene_id == 2 else (COLOR_VIOLET if scene_id == 3 else (COLOR_CRIMSON if scene_id == 4 else COLOR_EMERALD)))
    base_im = create_base_canvas(t, progress_val, accent_color=accent)
    
    # Composite Left Panel
    left_overlay = render_left_panel_overlay(scene_id, t, enter_offset=enter_offset)
    base_im = Image.alpha_composite(base_im, left_overlay)
    
    # Render Corresponding Phone Screen
    sw, sh = 234, 454
    if scene_id == 1:
        s_img = render_screen_lock(sw, sh, t)
    elif scene_id == 2:
        s_img = render_screen_vault(sw, sh, t)
    elif scene_id == 3:
        s_img = render_screen_crypto(sw, sh, t)
    elif scene_id == 4:
        s_img = render_screen_duress(sw, sh, t)
    else:
        s_img = render_screen_outro(sw, sh, t)
        
    phone = render_phone(s_img, t)
    
    # Phone position
    px = 645
    py = 50
    base_im.paste(phone, (px, py), phone)
    
    return base_im.convert("RGB")

print("Rendering refined preview frames for all 5 scenes...")
for s in [1, 2, 3, 4, 5]:
    preview = render_frame(s, 0.5, (s - 1) / 5.0)
    p_path = os.path.join(OUTPUT_DIR, f"scene_{s}_preview.jpg")
    preview.save(p_path, quality=95)
    print(f"Saved: {p_path}")

print("Rendering full animated sequence with ease-in transitions...")
scenes = [1, 2, 3, 4, 5]
frames_per_scene = 14
total_frames = len(scenes) * frames_per_scene
frame_duration_ms = 90  # ~11.1 FPS, silky smooth and fast

all_frames = []
all_durations = []

for s_idx, scene_id in enumerate(scenes):
    for f_idx in range(frames_per_scene):
        frame_number = s_idx * frames_per_scene + f_idx
        progress = frame_number / float(total_frames)
        t = frame_number * (frame_duration_ms / 1000.0)
        
        # Subtle 2-frame ease-in offset on scene entry
        offset = int(8 * (1.0 - (f_idx / 2.0))) if f_idx < 2 else 0
        
        frame = render_frame(scene_id, t, progress, enter_offset=offset)
        all_frames.append(frame)
        all_durations.append(frame_duration_ms)

# Save poster frame: High-resolution settled frame of Scene 1
poster_frame = all_frames[frames_per_scene // 2]
poster_path = os.path.join(OUTPUT_DIR, "brag.jpg")
poster_frame.save(poster_path, quality=96)
print(f"Saved poster frame: {poster_path}")

# Quantize and save GIF with per-frame adaptive palette to guarantee 100% color fidelity
print(f"Quantizing and compiling {len(all_frames)} frames (Total: {len(all_frames)*frame_duration_ms/1000.0:.1f}s)...")
quantized_frames = [f.convert("P", palette=Image.Palette.ADAPTIVE, colors=256) for f in all_frames]

gif_path = os.path.join(OUTPUT_DIR, "kryptx-showcase.gif")
quantized_frames[0].save(
    gif_path,
    save_all=True,
    append_images=quantized_frames[1:],
    duration=all_durations,
    loop=0,
    optimize=True
)

gif_size = os.path.getsize(gif_path)
print(f"Saved showcase GIF: {gif_path} ({gif_size / 1024:.1f} KB, {gif_size / (1024*1024):.2f} MB)")
