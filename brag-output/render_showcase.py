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
    font_phone_title = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 12)
    font_phone_sub = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 10)
    font_phone_body = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 9)
    font_phone_mono = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 9)
    font_phone_mono_lg = ImageFont.truetype("C:/Windows/Fonts/consolab.ttf", 14)
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
    font_phone_mono_lg = ImageFont.load_default()

# Load Logo
logo_img = None
logo_large = None
if os.path.exists("Logo.png"):
    try:
        raw = Image.open("Logo.png").convert("RGBA")
        logo_img = raw.resize((54, 54), Image.Resampling.LANCZOS)
        logo_large = raw.resize((90, 90), Image.Resampling.LANCZOS)
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
    draw_hud_badge(draw=fg_draw, text="STRONGBOX TEE BIOMETRICS", xy=(WIDTH - 280, 8), color=COLOR_CYAN)
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

# Vector icon drawing helpers to ensure 100% crisp rendering across all platforms
def draw_icon_lock(draw, xy, color):
    x, y = xy
    draw.rounded_rectangle([x, y + 4, x + 9, y + 12], radius=2, fill=color)
    draw.arc([x + 1, y, x + 8, y + 7], start=180, end=0, fill=color, width=2)

def draw_icon_search(draw, xy, color):
    x, y = xy
    draw.ellipse([x, y, x + 7, y + 7], outline=color, width=2)
    draw.line([(x + 5, y + 5), (x + 10, y + 10)], fill=color, width=2)

def draw_icon_eye(draw, xy, color):
    x, y = xy
    draw.arc([x, y - 2, x + 11, y + 7], start=20, end=160, fill=color, width=1)
    draw.arc([x, y + 2, x + 11, y + 11], start=200, end=340, fill=color, width=1)
    draw.ellipse([x + 3, y + 2, x + 7, y + 6], fill=color)

def draw_icon_copy(draw, xy, color):
    x, y = xy
    draw.rounded_rectangle([x, y, x + 6, y + 8], radius=1, outline=color, width=1)
    draw.rounded_rectangle([x + 3, y + 3, x + 9, y + 11], radius=1, fill=(15, 23, 42, 255), outline=color, width=1)

def draw_icon_check(draw, xy, color):
    x, y = xy
    draw.line([(x, y + 4), (x + 3, y + 7)], fill=color, width=2)
    draw.line([(x + 3, y + 7), (x + 8, y + 1)], fill=color, width=2)

def draw_icon_refresh(draw, xy, color):
    x, y = xy
    draw.arc([x, y, x + 9, y + 9], start=40, end=310, fill=color, width=2)
    draw.polygon([(x + 5, y - 1), (x + 10, y + 1), (x + 7, y + 4)], fill=color)

# ==============================================================================
# AUTHENTIC SCREEN 1: UnlockScreen.kt (Biometrics, Dial, YubiKey, 0-Network)
# ==============================================================================
def render_screen_unlock(w, h, t):
    im = Image.new("RGBA", (w, h), (9, 13, 24, 255))
    draw = ImageDraw.Draw(im)
    
    # Status bar
    draw.text((12, 6), "09:41", fill=COLOR_TEXT_WHITE, font=font_phone_mono)
    draw.text((w - 58, 6), "100% [OFF]", fill=COLOR_EMERALD, font=font_phone_mono)
    
    # Header Brand: Blue dot + KRYPTX
    cx = w // 2
    draw.ellipse([cx - 36, 26, cx - 28, 34], fill=COLOR_CYAN)
    draw.text((cx - 22, 23), "KRYPTX", fill=COLOR_TEXT_WHITE, font=font_phone_title)
    
    # Mechanical Vault Dial with live rotating ticks and center fingerprint sensor
    cy = 110
    dial_r = 44
    draw.ellipse([cx - dial_r, cy - dial_r, cx + dial_r, cy + dial_r], outline=(28, 42, 68, 255), width=2)
    
    # Rotating ticks
    tick_rot = t * 1.5
    for deg in range(0, 360, 30):
        rad = math.radians(deg + tick_rot * 20)
        x1 = cx + int((dial_r - 2) * math.cos(rad))
        y1 = cy + int((dial_r - 2) * math.sin(rad))
        x2 = cx + int((dial_r - 7) * math.cos(rad))
        y2 = cy + int((dial_r - 7) * math.sin(rad))
        draw.line([(x1, y1), (x2, y2)], fill=(0, 212, 255, 160), width=1)
        
    # Concentric pulsing shockwave rings around sensor
    pulse = (t * 2.2) % 1.0
    for i in range(2):
        p_val = ((pulse + i * 0.5) % 1.0)
        rp = int(22 + p_val * 24)
        alpha = int(140 * (1.0 - p_val))
        draw.ellipse([cx - rp, cy - rp, cx + rp, cy + rp], outline=(0, 230, 118, alpha), width=1)
        
    # Fingerprint Sensor Core
    draw.ellipse([cx - 20, cy - 20, cx + 20, cy + 20], fill=(16, 28, 48, 255), outline=COLOR_EMERALD, width=2)
    draw.arc([cx - 12, cy - 12, cx + 12, cy + 12], start=30, end=330, fill=COLOR_CYAN, width=2)
    draw.arc([cx - 7, cy - 7, cx + 7, cy + 7], start=50, end=310, fill=COLOR_EMERALD, width=2)
    
    # Welcome & prompt
    draw.text((cx - 40, cy + 54), "Welcome Back", fill=COLOR_TEXT_WHITE, font=font_phone_title)
    draw.text((cx - 68, cy + 72), "Touch sensor or enter master password", fill=COLOR_TEXT_MUTED, font=font_phone_body)
    
    # Master Password secure field
    fy = cy + 96
    draw.rounded_rectangle([16, fy, w - 16, fy + 38], radius=10, fill=(15, 23, 42, 255), outline=(0, 212, 255, 140), width=1)
    draw_icon_lock(draw, (26, fy + 12), COLOR_CYAN)
    draw.text((44, fy + 7), "Master Password", fill=COLOR_TEXT_MUTED, font=font_phone_body)
    draw.text((44, fy + 20), "●  ●  ●  ●  ●  ●  ●  ●", fill=COLOR_CYAN, font=font_phone_mono)
    
    # Hardware Key Notification Banner (YubiKey NFC / FIDO2)
    hy = fy + 48
    draw.rounded_rectangle([16, hy, w - 16, hy + 40], radius=8, fill=(14, 26, 46, 255), outline=(0, 212, 255, 90), width=1)
    draw.ellipse([26, hy + 16, 32, hy + 22], fill=COLOR_CYAN)
    draw.text((38, hy + 8), "Hardware Key Ready", fill=COLOR_CYAN, font=font_phone_sub)
    draw.text((38, hy + 22), "YubiKey NFC Slot-2 / FIDO2 BLE", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    
    # Primary Unlock Button
    by = hy + 50
    draw.rounded_rectangle([16, by, w - 16, by + 36], radius=10, fill=COLOR_EMERALD)
    draw.text((cx - 36, by + 10), "UNLOCK VAULT", fill=(5, 15, 28), font=font_phone_sub)
    
    # Bottom Air-Gapped Status Pill
    py = h - 30
    draw.rounded_rectangle([16, py, w - 16, py + 22], radius=6, fill=(12, 22, 38, 255), outline=(0, 230, 118, 120), width=1)
    draw.text((cx - 72, py + 5), "0 NETWORK PERMISSIONS • AIR-GAPPED", fill=COLOR_EMERALD, font=font_phone_mono)
    
    return im

# ==============================================================================
# AUTHENTIC SCREEN 2: VaultDashboardScreen.kt (Real items, search, filters, pulse)
# ==============================================================================
def render_screen_vault(w, h, t):
    im = Image.new("RGBA", (w, h), (9, 13, 24, 255))
    draw = ImageDraw.Draw(im)
    
    # Status bar
    draw.text((12, 6), "09:41", fill=COLOR_TEXT_WHITE, font=font_phone_mono)
    draw.text((w - 58, 6), "100% [OFF]", fill=COLOR_EMERALD, font=font_phone_mono)
    
    # Dashboard Header: User Avatar + Personal Vault + Security Ring + Lock
    draw.ellipse([16, 26, 40, 50], fill=(24, 38, 64, 255), outline=COLOR_CYAN, width=1)
    draw_icon_lock(draw, (24, 33), COLOR_CYAN)
    draw.text((46, 26), "Personal Vault", fill=COLOR_TEXT_WHITE, font=font_phone_title)
    draw.text((46, 40), "42 Items • 100% Offline", fill=COLOR_TEXT_MUTED, font=font_phone_body)
    
    # Security Pulse Ring in top right
    draw.ellipse([w - 48, 26, w - 18, 56], fill=(14, 26, 46, 255), outline=COLOR_EMERALD, width=2)
    draw.text((w - 38, 35), "98", fill=COLOR_EMERALD, font=font_phone_sub)
    
    # Search Bar Capsule
    sy = 62
    draw.rounded_rectangle([14, sy, w - 14, sy + 32], radius=16, fill=(15, 23, 42, 255), outline=(28, 42, 68, 255), width=1)
    draw_icon_search(draw, (26, sy + 10), COLOR_TEXT_MUTED)
    draw.text((44, sy + 9), "Search vault items...", fill=COLOR_TEXT_MUTED, font=font_phone_body)
    
    # Category Filter Chips (All, Logins, Cards, Notes)
    fy = sy + 40
    chips = [
        ("All (42)", True, COLOR_CYAN),
        ("Logins (28)", False, COLOR_TEXT_MUTED),
        ("Cards (6)", False, COLOR_TEXT_MUTED),
        ("Notes (8)", False, COLOR_TEXT_MUTED),
    ]
    cx_offset = 14
    for label, is_act, col in chips:
        bg_col = (0, 212, 255, 45) if is_act else (16, 24, 40, 255)
        border_col = COLOR_CYAN if is_act else (28, 42, 68, 255)
        text_col = COLOR_CYAN if is_act else COLOR_TEXT_MUTED
        bbox = font_phone_mono.getbbox(label)
        cw = bbox[2] - bbox[0] + 12
        draw.rounded_rectangle([cx_offset, fy, cx_offset + cw, fy + 20], radius=10, fill=bg_col, outline=border_col, width=1)
        draw.text((cx_offset + 6, fy + 4), label, fill=text_col, font=font_phone_mono)
        cx_offset += cw + 6
        if cx_offset > w - 30:
            break
            
    # Vault Item List (Authentic Jetpack Compose Item Rows)
    iy = fy + 30
    items = [
        ("GitHub Enterprise", "sovereign@kryptx.dev", "GH", COLOR_CYAN, "2FA", COLOR_CYAN),
        ("AWS Root Security", "prod-cluster-01", "AWS", COLOR_AMBER, "NFC", COLOR_EMERALD),
        ("Proton Mail Secure", "sovereign@pm.me", "PM", COLOR_VIOLET, "AES", COLOR_VIOLET),
        ("Signal Master Key", "Encrypted Note", "SIG", COLOR_EMERALD, "NOTE", COLOR_AMBER),
        ("Bitwarden Import", "Emergency Backup", "BW", COLOR_CYAN, "SEC", COLOR_EMERALD)
    ]
    
    for title, user, icon_txt, icon_col, tag_txt, tag_col in items:
        draw.rounded_rectangle([14, iy, w - 14, iy + 46], radius=10, fill=(15, 23, 42, 240), outline=COLOR_CARD_BORDER, width=1)
        # Identicon avatar
        draw.rounded_rectangle([22, iy + 8, 52, iy + 38], radius=8, fill=(24, 34, 56, 255))
        draw.text((27, iy + 14), icon_txt, fill=icon_col, font=font_phone_sub)
        # Title & Subtitle
        draw.text((58, iy + 8), title, fill=COLOR_TEXT_WHITE, font=font_phone_sub)
        draw.text((58, iy + 24), user, fill=COLOR_TEXT_MUTED, font=font_phone_mono)
        # Badges on right
        bbox = font_phone_mono.getbbox(tag_txt)
        bw = bbox[2] - bbox[0] + 8
        draw.rounded_rectangle([w - 24 - bw, iy + 14, w - 24, iy + 32], radius=4, fill=(tag_col[0], tag_col[1], tag_col[2], 30), outline=tag_col, width=1)
        draw.text((w - 20 - bw, iy + 17), tag_txt, fill=tag_col, font=font_phone_mono)
        iy += 54
        if iy > h - 70:
            break
            
    # Floating Action Button (+) with electric gradient at bottom right
    fab_x, fab_y = w - 54, h - 58
    draw.ellipse([fab_x, fab_y, fab_x + 42, fab_y + 42], fill=COLOR_CYAN, outline=COLOR_EMERALD, width=1)
    draw.text((fab_x + 13, fab_y + 8), "+", fill=(5, 15, 28), font=font_hero)
    
    return im

# ==============================================================================
# AUTHENTIC SCREEN 3: VaultItemDetailScreen.kt + PasswordDnaCard.kt (Entropy, TOTP)
# ==============================================================================
def render_screen_detail_dna(w, h, t):
    im = Image.new("RGBA", (w, h), (9, 13, 24, 255))
    draw = ImageDraw.Draw(im)
    
    # Status bar
    draw.text((12, 6), "09:41", fill=COLOR_TEXT_WHITE, font=font_phone_mono)
    draw.text((w - 58, 6), "100% [OFF]", fill=COLOR_EMERALD, font=font_phone_mono)
    
    # Top Bar: Back arrow < Item Details + Edit
    draw.text((16, 24), "←", fill=COLOR_CYAN, font=font_phone_title)
    draw.text((36, 24), "Item Details", fill=COLOR_TEXT_WHITE, font=font_phone_title)
    draw.text((w - 42, 24), "EDIT", fill=COLOR_CYAN, font=font_phone_mono)
    
    # Header: Large Identicon + Title + Category
    hy = 50
    draw.rounded_rectangle([16, hy, 56, hy + 40], radius=10, fill=(24, 34, 56, 255), outline=COLOR_CYAN, width=1)
    draw.text((22, hy + 12), "GH", fill=COLOR_CYAN, font=font_phone_title)
    draw.text((64, hy + 4), "GitHub Enterprise", fill=COLOR_TEXT_WHITE, font=font_phone_title)
    draw.text((64, hy + 22), "Login • Last updated 2h ago", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    
    # Masked Password Box
    py = hy + 50
    draw.rounded_rectangle([14, py, w - 14, py + 48], radius=10, fill=(15, 23, 42, 255), outline=COLOR_CARD_BORDER, width=1)
    draw.text((24, py + 6), "PASSWORD", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    draw.text((24, py + 22), "● ● ● ● ● ● ● ● ● ● ● ● ● ● ● ●", fill=COLOR_CYAN, font=font_phone_mono)
    draw_icon_eye(draw, (w - 46, py + 20), COLOR_TEXT_MUTED)
    draw_icon_copy(draw, (w - 28, py + 18), COLOR_CYAN)
    
    # PASSWORD DNA CARD (Exact representation of PasswordDnaCard.kt)
    dy = py + 56
    draw.rounded_rectangle([14, dy, w - 14, dy + 154], radius=12, fill=(14, 22, 38, 255), outline=(0, 230, 118, 140), width=1)
    
    # DNA header with green badge
    draw.ellipse([24, dy + 10, 48, dy + 34], fill=(0, 230, 118, 40))
    draw.text((28, dy + 15), "DNA", fill=COLOR_EMERALD, font=font_phone_mono)
    draw.text((54, dy + 10), "Password DNA", fill=COLOR_TEXT_WHITE, font=font_phone_sub)
    draw.text((54, dy + 24), "16 chars • 98.4 bits entropy", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    
    # Strength & Crack Time row
    draw.text((24, dy + 44), "STRENGTH", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    draw.text((24, dy + 56), "VERY STRONG", fill=COLOR_EMERALD, font=font_phone_sub)
    
    draw.text((w - 96, dy + 44), "CRACK TIME", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    draw.text((w - 96, dy + 56), "CENTURIES", fill=COLOR_EMERALD, font=font_phone_sub)
    
    # Animated Entropy Meter Bar
    draw.rounded_rectangle([24, dy + 76, w - 24, dy + 82], radius=3, fill=(28, 40, 62, 255))
    entropy_w = int((w - 48) * min(1.0, 0.85 + 0.15 * math.sin(t * 2)))
    draw.rounded_rectangle([24, dy + 76, 24 + entropy_w, dy + 82], radius=3, fill=COLOR_EMERALD)
    
    # Character Family Badges: [✓ a-z] [✓ A-Z] [✓ 0-9] [✓ !@#]
    cy_badge = dy + 92
    families = ["a-z", "A-Z", "0-9", "!@#"]
    bx = 24
    for btext in families:
        draw.rounded_rectangle([bx, cy_badge, bx + 42, cy_badge + 20], radius=4, fill=(0, 230, 118, 25), outline=COLOR_EMERALD, width=1)
        draw_icon_check(draw, (bx + 4, cy_badge + 6), COLOR_EMERALD)
        draw.text((bx + 15, cy_badge + 4), btext, fill=COLOR_EMERALD, font=font_phone_mono)
        bx += 46
        
    # Security Status Note
    draw_icon_check(draw, (24, dy + 125), COLOR_EMERALD)
    draw.text((36, dy + 123), "Unique Password • Zero Breach Hits", fill=COLOR_EMERALD, font=font_phone_mono)
    
    # TOTP 2FA Authenticator Card
    ty = dy + 162
    draw.rounded_rectangle([14, ty, w - 14, ty + 68], radius=10, fill=(15, 23, 42, 255), outline=(0, 212, 255, 120), width=1)
    draw.text((24, ty + 8), "ONE-TIME CODE (TOTP)", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    draw.text((24, ty + 26), "482  910", fill=COLOR_CYAN, font=font_phone_mono_lg)
    
    # Circular 30s Countdown Timer Ring
    draw.ellipse([w - 46, ty + 18, w - 20, ty + 44], outline=(28, 42, 68, 255), width=2)
    totp_progress = int(360 * ((t * 0.2) % 1.0))
    draw.arc([w - 46, ty + 18, w - 20, ty + 44], start=-90, end=-90 + totp_progress, fill=COLOR_CYAN, width=2)
    draw.text((w - 38, ty + 25), "24s", fill=COLOR_CYAN, font=font_phone_mono)
    
    return im

# ==============================================================================
# AUTHENTIC SCREEN 4: GeneratorScreen.kt (Modes, length slider, entropy, CSPRNG)
# ==============================================================================
def render_screen_generator(w, h, t):
    im = Image.new("RGBA", (w, h), (9, 13, 24, 255))
    draw = ImageDraw.Draw(im)
    cx = w // 2
    
    # Status bar
    draw.text((12, 6), "09:41", fill=COLOR_TEXT_WHITE, font=font_phone_mono)
    draw.text((w - 58, 6), "100% [OFF]", fill=COLOR_EMERALD, font=font_phone_mono)
    
    # Top Bar
    draw.text((16, 24), "Generator", fill=COLOR_TEXT_WHITE, font=font_phone_title)
    draw.text((w - 46, 24), "CONFIG", fill=COLOR_CYAN, font=font_phone_mono)
    
    # Mode Selector Tabs: [Password] [Passphrase] [PIN]
    my = 48
    draw.rounded_rectangle([14, my, w - 14, my + 30], radius=15, fill=(15, 23, 42, 255), outline=COLOR_CARD_BORDER, width=1)
    modes = [("Password", True), ("Passphrase", False), ("PIN", False)]
    mx_offset = 16
    tab_w = (w - 36) // 3
    for mname, is_sel in modes:
        if is_sel:
            draw.rounded_rectangle([mx_offset, my + 2, mx_offset + tab_w, my + 28], radius=13, fill=COLOR_CYAN)
            draw.text((mx_offset + 12, my + 8), mname, fill=(5, 12, 24), font=font_phone_mono)
        else:
            draw.text((mx_offset + 10, my + 8), mname, fill=COLOR_TEXT_MUTED, font=font_phone_mono)
        mx_offset += tab_w + 2
        
    # Live Credential Display Card
    gy = my + 38
    draw.rounded_rectangle([14, gy, w - 14, gy + 116], radius=14, fill=(14, 22, 38, 255), outline=(0, 212, 255, 140), width=1)
    
    # Header: VERY STRONG badge + 112 bits entropy
    draw.rounded_rectangle([22, gy + 8, 96, gy + 24], radius=4, fill=(0, 230, 118, 40), outline=COLOR_EMERALD, width=1)
    draw.text((26, gy + 11), "VERY STRONG", fill=COLOR_EMERALD, font=font_phone_mono)
    draw.text((w - 96, gy + 11), "112 bits entropy", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    
    # Generated Secret Monospace
    sample_pw = "k8#Qz!9pL$mR2@vW"
    draw.text((24, gy + 38), sample_pw, fill=COLOR_TEXT_WHITE, font=font_phone_mono_lg)
    
    # Crack Time Pill
    draw.rounded_rectangle([22, gy + 64, w - 22, gy + 82], radius=4, fill=(20, 30, 50, 255))
    draw.text((28, gy + 68), "Estimated Crack Time: Centuries", fill=COLOR_EMERALD, font=font_phone_mono)
    
    # Action Row: Regenerate and Copy
    draw.rounded_rectangle([22, gy + 88, 106, gy + 108], radius=6, fill=(24, 38, 64, 255), outline=COLOR_CARD_BORDER, width=1)
    draw_icon_refresh(draw, (28, gy + 93), COLOR_CYAN)
    draw.text((42, gy + 92), "Regenerate", fill=COLOR_CYAN, font=font_phone_mono)
    
    draw.rounded_rectangle([112, gy + 88, w - 22, gy + 108], radius=6, fill=COLOR_CYAN)
    draw_icon_copy(draw, (120, gy + 93), (5, 12, 24))
    draw.text((134, gy + 92), "Copy Credential", fill=(5, 12, 24), font=font_phone_mono)
    
    # Controls Section: Length Slider
    ly = gy + 124
    draw.rounded_rectangle([14, ly, w - 14, ly + 52], radius=10, fill=(15, 23, 42, 255), outline=COLOR_CARD_BORDER, width=1)
    draw.text((24, ly + 8), "PASSWORD LENGTH", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    draw.text((w - 42, ly + 8), "20", fill=COLOR_CYAN, font=font_phone_sub)
    # Slider track & thumb
    draw.rounded_rectangle([24, ly + 30, w - 24, ly + 34], radius=2, fill=(28, 40, 62, 255))
    thumb_x = int(24 + (w - 48) * 0.65)
    draw.rounded_rectangle([24, ly + 30, thumb_x, ly + 34], radius=2, fill=COLOR_CYAN)
    draw.ellipse([thumb_x - 6, ly + 26, thumb_x + 6, ly + 38], fill=COLOR_TEXT_WHITE, outline=COLOR_CYAN, width=2)
    
    # Character Toggles (Uppercase, Lowercase, Numbers, Symbols)
    ty = ly + 58
    toggles = [
        ("Uppercase (A-Z)", True),
        ("Lowercase (a-z)", True),
        ("Numbers (0-9)", True),
        ("Symbols (!@#$)", True),
    ]
    for tlabel, tstate in toggles:
        draw.rounded_rectangle([14, ty, w - 14, ty + 24], radius=6, fill=(15, 23, 42, 230), outline=COLOR_CARD_BORDER, width=1)
        draw.text((24, ty + 6), tlabel, fill=COLOR_TEXT_WHITE, font=font_phone_mono)
        # Switch toggle
        sw_col = COLOR_EMERALD if tstate else (60, 70, 90)
        draw.rounded_rectangle([w - 42, ty + 5, w - 22, ty + 19], radius=7, fill=sw_col)
        draw.ellipse([w - 30, ty + 6, w - 24, ty + 18], fill=COLOR_TEXT_WHITE)
        ty += 28
        
    return im

# ==============================================================================
# AUTHENTIC SCREEN 5: SecurityCenterScreen.kt (Health score ring, offline audit)
# ==============================================================================
def render_screen_security_center(w, h, t):
    im = Image.new("RGBA", (w, h), (9, 13, 24, 255))
    draw = ImageDraw.Draw(im)
    
    # Status bar
    draw.text((12, 6), "09:41", fill=COLOR_TEXT_WHITE, font=font_phone_mono)
    draw.text((w - 58, 6), "100% [OFF]", fill=COLOR_EMERALD, font=font_phone_mono)
    
    # Top Bar: Security Center + Refresh
    draw.text((16, 24), "Security Center", fill=COLOR_TEXT_WHITE, font=font_phone_title)
    draw_icon_refresh(draw, (w - 30, 26), COLOR_EMERALD)
    
    # Health Score Hero Ring (KryptxScoreRing)
    sy = 54
    draw.rounded_rectangle([14, sy, w - 14, sy + 144], radius=14, fill=(14, 22, 38, 255), outline=(0, 230, 118, 140), width=1)
    
    # Circular score ring
    cx = w // 2
    ring_cy = sy + 52
    draw.ellipse([cx - 36, ring_cy - 36, cx + 36, ring_cy + 36], outline=(28, 42, 68, 255), width=4)
    # Animated arc
    score_sweep = int(360 * 0.98)
    draw.arc([cx - 36, ring_cy - 36, cx + 36, ring_cy + 36], start=-90, end=-90 + score_sweep, fill=COLOR_EMERALD, width=4)
    draw.text((cx - 15, ring_cy - 12), "98", fill=COLOR_EMERALD, font=font_hero)
    draw.text((cx - 24, ring_cy + 14), "Grade A+", fill=COLOR_CYAN, font=font_phone_mono)
    
    draw.text((cx - 74, sy + 104), "Your vault is exceptionally secure", fill=COLOR_TEXT_WHITE, font=font_phone_sub)
    draw.text((cx - 58, sy + 120), "0 security issues detected", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    
    # Audit Stat Grid (4 interactive cards: Breached, Weak, Reused, 2FA)
    gy = sy + 154
    box_w = (w - 38) // 4
    stats = [
        ("0", "Breached", COLOR_EMERALD),
        ("0", "Weak", COLOR_EMERALD),
        ("0", "Reused", COLOR_EMERALD),
        ("12", "2FA Active", COLOR_CYAN),
    ]
    bx = 14
    for count, label, col in stats:
        draw.rounded_rectangle([bx, gy, bx + box_w, gy + 48], radius=8, fill=(15, 23, 42, 255), outline=COLOR_CARD_BORDER, width=1)
        draw.text((bx + 12, gy + 8), count, fill=col, font=font_phone_title)
        draw.text((bx + 4, gy + 28), label, fill=COLOR_TEXT_MUTED, font=font_phone_mono)
        bx += box_w + 3
        
    # Offline Audit Guarantee Card
    ay = gy + 56
    draw.rounded_rectangle([14, ay, w - 14, ay + 68], radius=10, fill=(14, 26, 46, 255), outline=(0, 230, 118, 120), width=1)
    draw.ellipse([24, ay + 14, 34, ay + 24], fill=COLOR_EMERALD)
    draw.text((42, ay + 10), "100% Offline Vault Audit", fill=COLOR_TEXT_WHITE, font=font_phone_sub)
    draw.text((42, ay + 25), "All credential entropy, breach checks, and\nhealth analyses run purely on your CPU.", fill=COLOR_TEXT_MUTED, font=font_phone_mono)
    draw.text((42, ay + 50), "Zero bytes transmitted to external APIs.", fill=COLOR_EMERALD, font=font_phone_mono)
    
    # Bottom Cryptographic Engine Pill
    by = ay + 76
    draw.rounded_rectangle([14, by, w - 14, by + 30], radius=8, fill=(15, 23, 42, 255), outline=COLOR_CARD_BORDER, width=1)
    draw.text((24, by + 8), "AES-256-GCM • Argon2id • StrongBox TEE", fill=COLOR_CYAN, font=font_phone_mono)
    
    return im

def render_left_panel_overlay(scene_id, t, enter_offset=0):
    overlay = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    lx = 48
    ly = 65 + enter_offset
    
    if scene_id == 1:
        # Unlock & Biometrics
        draw_pill(draw, "// 100% AIR-GAPPED FORTRESS", (lx, ly), COLOR_EMERALD, bg_alpha=35)
        
        draw.text((lx, ly + 36), "WHAT IF YOUR PASSWORD MANAGER", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 68), "COULD NEVER BE HACKED?", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 102), "Because it physically cannot touch the internet.", fill=COLOR_EMERALD, font=font_hero_sub)
        
        desc = ("Zero sockets. Zero telemetry. Zero servers to breach. The Android\n"
                "Linux kernel physically blocks network sockets at system level.\n"
                "StrongBox TEE biometrics and physical YubiKeys guard your vault.")
        draw.text((lx, ly + 132), desc, fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 1
        c1_y = ly + 195
        draw.rounded_rectangle([lx, c1_y, lx + 540, c1_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c1_y + 18, lx + 30, c1_y + 30], fill=COLOR_EMERALD)
        draw.text((lx + 42, c1_y + 12), "Zero Network Permissions Enforced", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c1_y + 34), "Look at AndroidManifest.xml: android.permission.INTERNET does not exist.\nAll network socket creation calls fail at the Linux kernel layer.", fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 2
        c2_y = c1_y + 80
        draw.rounded_rectangle([lx, c2_y, lx + 540, c2_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c2_y + 18, lx + 30, c2_y + 30], fill=COLOR_CYAN)
        draw.text((lx + 42, c2_y + 12), "StrongBox TEE Biometrics & Hardware YubiKey", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c2_y + 34), "Instant Class-3 fingerprint authentication with hardware-backed Keystore,\nplus physical YubiKey NFC (HMAC-SHA1 Slot 2) and FIDO2 BLE security keys.", fill=COLOR_TEXT_MUTED, font=font_card_body)

    elif scene_id == 2:
        # Sovereign Vault Dashboard
        draw_pill(draw, "// SOVEREIGN VAULT DASHBOARD", (lx, ly), COLOR_CYAN, bg_alpha=35)
        
        draw.text((lx, ly + 36), "INSTANT SUBSTRING SEARCH", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 68), "AND ZERO-LATENCY VAULT", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 102), "Effortless speed without compromising mathematics.", fill=COLOR_CYAN, font=font_hero_sub)
        
        desc = ("Access your logins, payment cards, notes, and 2FA tokens instantly.\n"
                "Real-time category filtering and encrypted SQLite indexing deliver\n"
                "silky 120 FPS Jetpack Compose interactions with zero cloud lag.")
        draw.text((lx, ly + 132), desc, fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 1
        c1_y = ly + 195
        draw.rounded_rectangle([lx, c1_y, lx + 540, c1_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c1_y + 18, lx + 30, c1_y + 30], fill=COLOR_CYAN)
        draw.text((lx + 42, c1_y + 12), "Fast Substring Search & Smart Filters", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c1_y + 34), "Search hundreds of accounts instantly by title, username, or URL.\nFilter by All, Logins, Cards, Notes, or 2FA with 1 tap.", fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 2
        c2_y = c1_y + 80
        draw.rounded_rectangle([lx, c2_y, lx + 540, c2_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c2_y + 18, lx + 30, c2_y + 30], fill=COLOR_EMERALD)
        draw.text((lx + 42, c2_y + 12), "AES-256-GCM & Argon2id Vault Storage", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c2_y + 34), "Your database is encrypted with NIST AES-256-GCM authenticated ciphers\nand Argon2id RFC 9106 memory-hard key derivation (16 MB RAM).", fill=COLOR_TEXT_MUTED, font=font_card_body)

    elif scene_id == 3:
        # Item Detail & Password DNA
        draw_pill(draw, "// REAL-TIME PASSWORD DNA", (lx, ly), COLOR_EMERALD, bg_alpha=35)
        
        draw.text((lx, ly + 36), "PASSWORD DNA & ENTROPY", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 68), "DEEP SECURITY ANALYSIS", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 102), "Understand the exact mathematical strength of every password.", fill=COLOR_EMERALD, font=font_hero_sub)
        
        desc = ("Inspect bit entropy, crack time calculations, and character families\n"
                "in real time. Built-in TOTP authenticator generates 30s tokens\n"
                "without requiring separate authenticator apps.")
        draw.text((lx, ly + 132), desc, fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 1
        c1_y = ly + 195
        draw.rounded_rectangle([lx, c1_y, lx + 540, c1_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c1_y + 18, lx + 30, c1_y + 30], fill=COLOR_EMERALD)
        draw.text((lx + 42, c1_y + 12), "Real-Time Bit Entropy & Crack Time", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c1_y + 34), "Shannon entropy scoring breaks down character diversity (a-z, A-Z, 0-9, !@#$)\nand provides realistic brute-force crack time estimates on your CPU.", fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 2
        c2_y = c1_y + 80
        draw.rounded_rectangle([lx, c2_y, lx + 540, c2_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c2_y + 18, lx + 30, c2_y + 30], fill=COLOR_CYAN)
        draw.text((lx + 42, c2_y + 12), "Integrated TOTP Authenticator (2FA)", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c2_y + 34), "RFC 6238 time-based one-time passwords with live circular countdown timer.\nNever juggle between your password manager and a separate 2FA app.", fill=COLOR_TEXT_MUTED, font=font_card_body)

    elif scene_id == 4:
        # Generator
        draw_pill(draw, "// HIGH-ENTROPY GENERATOR", (lx, ly), COLOR_CYAN, bg_alpha=35)
        
        draw.text((lx, ly + 36), "PASSWORD & PASS-PHRASE", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 68), "CSPRNG GENERATOR", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 102), "Cryptographically unpredictable passwords and Diceware phrases.", fill=COLOR_CYAN, font=font_hero_sub)
        
        desc = ("Generate high-entropy passwords, memorable Diceware multi-word\n"
                "passphrases, secure PINs, and usernames using Android's hardware\n"
                "CSPRNG (SecureRandom) entropy source.")
        draw.text((lx, ly + 132), desc, fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 1
        c1_y = ly + 195
        draw.rounded_rectangle([lx, c1_y, lx + 540, c1_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c1_y + 18, lx + 30, c1_y + 30], fill=COLOR_CYAN)
        draw.text((lx + 42, c1_y + 12), "Hardware CSPRNG & Diceware Wordlists", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c1_y + 34), "Choose between complex random passwords, 4-8 word Diceware passphrases,\nor banking PINs with interactive length and character family controls.", fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 2
        c2_y = c1_y + 80
        draw.rounded_rectangle([lx, c2_y, lx + 540, c2_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c2_y + 18, lx + 30, c2_y + 30], fill=COLOR_EMERALD)
        draw.text((lx + 42, c2_y + 12), "1-Tap Clipboard Auto-Clear", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c2_y + 34), "Copied passwords and credentials are automatically wiped from Android's\nsystem clipboard after 30 seconds, preventing background app sniffing.", fill=COLOR_TEXT_MUTED, font=font_card_body)

    elif scene_id == 5:
        # Security Center & Health Audit
        draw_pill(draw, "// 100% OFFLINE SECURITY CENTER", (lx, ly), COLOR_VIOLET, bg_alpha=35)
        
        draw.text((lx, ly + 36), "HEALTH SCORING & AUDIT", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 68), "100% OFFLINE VERIFICATION", fill=COLOR_TEXT_WHITE, font=font_hero)
        draw.text((lx, ly + 102), "Total visibility into your vault health with zero cloud leakage.", fill=COLOR_VIOLET, font=font_hero_sub)
        
        desc = ("Security score ring audits weak, reused, and missing-2FA items\n"
                "completely offline on your device. 100% Free & Open Source\n"
                "under the Apache 2.0 license, backed by 603 passing unit tests.")
        draw.text((lx, ly + 132), desc, fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 1
        c1_y = ly + 195
        draw.rounded_rectangle([lx, c1_y, lx + 540, c1_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c1_y + 18, lx + 30, c1_y + 30], fill=COLOR_EMERALD)
        draw.text((lx + 42, c1_y + 12), "603 Unit Tests Passing (100% Pass Rate)", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c1_y + 34), "Every cryptographic routine, state machine, and biometric path is rigorously\nverified across Android unit, crypto roundtrip, and security test suites.", fill=COLOR_TEXT_MUTED, font=font_card_body)
        
        # Spec Card 2
        c2_y = c1_y + 80
        draw.rounded_rectangle([lx, c2_y, lx + 540, c2_y + 68], radius=10, fill=COLOR_CARD_BG, outline=COLOR_CARD_BORDER, width=1)
        draw.ellipse([lx + 18, c2_y + 18, lx + 30, c2_y + 30], fill=COLOR_CYAN)
        draw.text((lx + 42, c2_y + 12), "Standalone Signed APK & Google Play", fill=COLOR_TEXT_WHITE, font=font_card_title)
        draw.text((lx + 42, c2_y + 34), "Install directly via official Play Store or side-load signed offline APKs\nfor true air-gapped hardware devices with verifiable SHA-256 checksums.", fill=COLOR_TEXT_MUTED, font=font_card_body)
        
    return overlay

def render_frame(scene_id, t, progress_val, enter_offset=0):
    accent = COLOR_EMERALD if scene_id in [1, 3] else (COLOR_CYAN if scene_id in [2, 4] else COLOR_VIOLET)
    base_im = create_base_canvas(t, progress_val, accent_color=accent)
    
    # Composite Left Panel
    left_overlay = render_left_panel_overlay(scene_id, t, enter_offset=enter_offset)
    base_im = Image.alpha_composite(base_im, left_overlay)
    
    # Render Corresponding Phone Screen
    sw, sh = 234, 454
    if scene_id == 1:
        s_img = render_screen_unlock(sw, sh, t)
    elif scene_id == 2:
        s_img = render_screen_vault(sw, sh, t)
    elif scene_id == 3:
        s_img = render_screen_detail_dna(sw, sh, t)
    elif scene_id == 4:
        s_img = render_screen_generator(sw, sh, t)
    else:
        s_img = render_screen_security_center(sw, sh, t)
        
    phone = render_phone(s_img, t)
    
    # Phone position
    px = 645
    py = 50
    base_im.paste(phone, (px, py), phone)
    
    return base_im.convert("RGB")

print("Rendering preview frames for all 5 authentic scenes...")
for s in [1, 2, 3, 4, 5]:
    preview = render_frame(s, 0.5, (s - 1) / 5.0)
    p_path = os.path.join(OUTPUT_DIR, f"scene_{s}_preview.jpg")
    preview.save(p_path, quality=95)
    print(f"Saved: {p_path}")

print("Rendering animated showcase GIF sequence (14 frames per scene, 5 scenes)...")
scenes = [1, 2, 3, 4, 5]
frames_per_scene = 14
total_frames = len(scenes) * frames_per_scene
frame_duration_ms = 95  # ~10.5 FPS, silky smooth and fast

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
