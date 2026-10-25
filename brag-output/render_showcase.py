import os
import math
from PIL import Image, ImageDraw, ImageFont

WIDTH, HEIGHT = 900, 506
OUTPUT_DIR = "brag-output"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Load fonts
try:
    font_large_bold = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 30)
    font_title_bold = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 24)
    font_sub_bold = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 19)
    font_body = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 16)
    font_mono = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 15)
    font_badge = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 13)
except Exception:
    font_large_bold = ImageFont.load_default()
    font_title_bold = ImageFont.load_default()
    font_sub_bold = ImageFont.load_default()
    font_body = ImageFont.load_default()
    font_mono = ImageFont.load_default()
    font_badge = ImageFont.load_default()

# Load logo
logo_img = None
if os.path.exists("Logo.png"):
    try:
        logo_raw = Image.open("Logo.png").convert("RGBA")
        logo_img = logo_raw.resize((120, 120), Image.Resampling.LANCZOS)
    except Exception as e:
        print("Logo error:", e)

# Load mockup
mockup_img = None
if os.path.exists("docs/assets/images/hero-mockup.webp"):
    try:
        mockup_raw = Image.open("docs/assets/images/hero-mockup.webp").convert("RGBA")
        aspect = mockup_raw.width / mockup_raw.height
        new_h = 360
        new_w = int(new_h * aspect)
        mockup_img = mockup_raw.resize((new_w, new_h), Image.Resampling.LANCZOS)
    except Exception as e:
        print("Mockup error:", e)

def create_base_canvas(t, progress_val):
    im = Image.new("RGBA", (WIDTH, HEIGHT), (11, 15, 25, 255))
    draw = ImageDraw.Draw(im)
    
    # Subtle cyber grid
    grid_spacing = 40
    for x in range(0, WIDTH, grid_spacing):
        draw.line([(x, 0), (x, HEIGHT)], fill=(20, 28, 45, 120), width=1)
    for y in range(0, HEIGHT, grid_spacing):
        draw.line([(0, y), (WIDTH, y)], fill=(20, 28, 45, 120), width=1)
        
    # Glow orb
    glow_x = int(WIDTH * 0.5 + 40 * math.sin(t * 0.8))
    glow_y = int(HEIGHT * 0.5 + 30 * math.cos(t * 0.8))
    for r in range(160, 40, -25):
        alpha = int(18 * (1.0 - r / 160))
        draw.ellipse([glow_x - r, glow_y - r, glow_x + r, glow_y + r], fill=(0, 230, 118, alpha))
        
    # Top navigation bar mockup
    draw.rectangle([0, 0, WIDTH, 36], fill=(15, 22, 38, 240))
    draw.line([(0, 36), (WIDTH, 36)], fill=(30, 45, 70, 255), width=1)
    
    # Left brand pill in top bar
    draw.ellipse([16, 12, 28, 24], fill=(0, 230, 118, 255))
    draw.text((36, 9), "KRYPTX AIR-GAPPED FORTRESS v2.2", fill=(240, 245, 255), font=font_badge)
    
    # Right status badges
    draw.text((WIDTH - 290, 9), "AIR-GAP: 100% OFFLINE", fill=(0, 230, 118), font=font_badge)
    draw.text((WIDTH - 120, 9), "TESTS: 570/570", fill=(0, 212, 255), font=font_badge)
    
    # Bottom progress bar
    draw.line([(0, HEIGHT - 3), (WIDTH, HEIGHT - 3)], fill=(25, 35, 55, 255), width=3)
    draw.line([(0, HEIGHT - 3), (int(WIDTH * progress_val), HEIGHT - 3)], fill=(0, 230, 118, 255), width=3)
    
    return im, draw

def draw_pill(draw, text, xy, bg_color=(22, 34, 55, 240), border_color=(0, 230, 118, 200), text_color=(0, 230, 118)):
    x, y = xy
    bbox = font_badge.getbbox(text)
    w = bbox[2] - bbox[0] + 24
    h = bbox[3] - bbox[1] + 14
    draw.rounded_rectangle([x, y, x + w, y + h], radius=7, fill=bg_color, outline=border_color, width=1)
    draw.text((x + 12, y + 6), text, fill=text_color, font=font_badge)
    return w, h

# Define scenes with (scene_id, pulse, progress, duration_ms)
# Total duration = 20,000 ms = 20.0s
sequence = [
    # Scene 1: Hook (3500ms)
    ("hook", 0.95, 0.05, 300),
    ("hook", 1.05, 0.10, 400),
    ("hook", 1.00, 0.17, 2800),
    
    # Scene 2: Hardware & StrongBox (4000ms)
    ("hardware", 0.95, 0.22, 300),
    ("hardware", 1.03, 0.28, 400),
    ("hardware", 1.00, 0.37, 3300),
    
    # Scene 3: Post-Quantum & Dual Ciphers (4000ms)
    ("crypto", 0.95, 0.42, 300),
    ("crypto", 1.03, 0.48, 400),
    ("crypto", 1.00, 0.57, 3300),
    
    # Scene 4: Autofill & Offline Audit (4000ms)
    ("autofill", 0.95, 0.62, 300),
    ("autofill", 1.03, 0.68, 400),
    ("autofill", 1.00, 0.77, 3300),
    
    # Scene 5: Duress & Panic (2000ms)
    ("panic", 0.95, 0.81, 250),
    ("panic", 1.03, 0.84, 350),
    ("panic", 1.00, 0.87, 1400),
    
    # Scene 6: Outro (2500ms)
    ("outro", 0.95, 0.91, 300),
    ("outro", 1.06, 0.95, 400),
    ("outro", 1.00, 1.00, 1800),
]

def render_scene(scene_id, pulse, progress):
    t = progress * 20.0
    im, draw = create_base_canvas(t, progress)
    
    if scene_id == "hook":
        lw, lh = int(120 * pulse), int(120 * pulse)
        lx = (WIDTH - lw) // 2
        ly = 65
        ring_r = int(75 * pulse)
        draw.ellipse([WIDTH//2 - ring_r, 65 + 60 - ring_r, WIDTH//2 + ring_r, 65 + 60 + ring_r], outline=(0, 230, 118, 140), width=2)
        ring_r2 = int(90 * pulse)
        draw.ellipse([WIDTH//2 - ring_r2, 65 + 60 - ring_r2, WIDTH//2 + ring_r2, 65 + 60 + ring_r2], outline=(0, 212, 255, 80), width=1)
        
        if logo_img:
            l_scaled = logo_img.resize((lw, lh), Image.Resampling.LANCZOS)
            im.paste(l_scaled, (lx, ly), l_scaled)
            
        head = "WHAT IF YOUR PASSWORD MANAGER COULD NEVER BE HACKED?"
        bbox = font_large_bold.getbbox(head)
        hw = bbox[2] - bbox[0]
        draw.text(((WIDTH - hw) // 2, 220), head, fill=(255, 255, 255), font=font_large_bold)
        
        sub = "Because it physically cannot touch the internet."
        bbox2 = font_title_bold.getbbox(sub)
        sw = bbox2[2] - bbox2[0]
        draw.text(((WIDTH - sw) // 2, 275), sub, fill=(0, 230, 118), font=font_title_bold)
        
        desc = "Zero sockets. Zero telemetry. Zero cloud anxiety. 100% Android Kernel Isolation."
        bbox3 = font_body.getbbox(desc)
        dw = bbox3[2] - bbox3[0]
        draw.text(((WIDTH - dw) // 2, 330), desc, fill=(160, 180, 210), font=font_body)
        
        start_x = (WIDTH - 640) // 2
        draw_pill(draw, "0 INTERNET PERMISSIONS", (start_x, 395), border_color=(0, 230, 118), text_color=(0, 230, 118))
        draw_pill(draw, "AIR-GAPPED KERNEL ENFORCED", (start_x + 210, 395), border_color=(0, 212, 255), text_color=(0, 212, 255))
        draw_pill(draw, "NO SERVERS TO BREACH", (start_x + 470, 395), border_color=(124, 77, 255), text_color=(160, 130, 255))

    elif scene_id == "hardware":
        draw.text((60, 58), "HARDWARE-BACKED SOVEREIGNTY", fill=(0, 212, 255), font=font_badge)
        draw.text((60, 82), "StrongBox TEE + Physical YubiKey", fill=(255, 255, 255), font=font_large_bold)
        draw.text((60, 126), "Zero-trust physical challenge-response & biometric key wrapping.", fill=(160, 180, 210), font=font_body)
        
        bullets = [
            ("NFC & USB OTG Security Keys", "HMAC-SHA1 Slot 2 challenge-response (Instruction 0x38).", (0, 230, 118)),
            ("Class-3 Hardware Biometrics", "Isolated StrongBox Keystore with ECDH P-256 forward key agreement.", (0, 212, 255)),
            ("Wireless BLE FIDO2 / CTAP2", "Device-bound wireless authenticator support.", (124, 77, 255)),
            ("Immortal CrashDefense Sentinel", "Zero UI thread termination under Compose recomposition flings.", (255, 180, 0))
        ]
        
        by = 170
        for title, detail, col in bullets:
            draw.rounded_rectangle([60, by, 530, by + 56], radius=8, fill=(18, 26, 42, 200), outline=(35, 50, 75, 255), width=1)
            draw.ellipse([76, by + 18, 88, by + 30], fill=col)
            draw.text((102, by + 10), title, fill=(255, 255, 255), font=font_sub_bold)
            draw.text((102, by + 32), detail, fill=(140, 160, 190), font=font_body)
            by += 68
            
        if mockup_img:
            im.paste(mockup_img, (570, 80), mockup_img)

    elif scene_id == "crypto":
        draw.text((60, 58), "MATHEMATICALLY IMPENETRABLE", fill=(124, 77, 255), font=font_badge)
        draw.text((60, 82), "Post-Quantum Fortress & Dual Ciphers", fill=(255, 255, 255), font=font_large_bold)
        draw.text((60, 126), "Bare-metal Rust 2024 JNI engine across arm64, armeabi-v7a, x86, x86_64.", fill=(160, 180, 210), font=font_body)
        
        draw.rounded_rectangle([60, 172, 430, 372], radius=10, fill=(16, 24, 40, 230), outline=(0, 212, 255, 180), width=1)
        draw.text((80, 192), "DUAL NATIVE CIPHERS", fill=(0, 212, 255), font=font_sub_bold)
        draw.text((80, 226), "• Rust XChaCha20-Poly1305", fill=(255, 255, 255), font=font_body)
        draw.text((95, 250), "Authenticated length-prefixed AAD framing", fill=(140, 160, 190), font=font_mono)
        draw.text((80, 280), "• NIST AES-256-GCM", fill=(255, 255, 255), font=font_body)
        draw.text((95, 304), "NIST SP 800-38D §8.2.1 deterministic nonces", fill=(140, 160, 190), font=font_mono)
        draw.text((80, 334), "• Adaptive Argon2id (Rust Bare-Metal)", fill=(0, 230, 118), font=font_body)
        
        draw.rounded_rectangle([470, 172, 840, 372], radius=10, fill=(16, 24, 40, 230), outline=(124, 77, 255, 180), width=1)
        draw.text((490, 192), "POST-QUANTUM & RAM SHIELD", fill=(124, 77, 255), font=font_sub_bold)
        draw.text((490, 226), "• ML-KEM-768 (FIPS 203)", fill=(255, 255, 255), font=font_body)
        draw.text((505, 250), "Post-quantum key encapsulation mechanism", fill=(140, 160, 190), font=font_mono)
        draw.text((490, 280), "• ML-DSA-65 (FIPS 204)", fill=(255, 255, 255), font=font_body)
        draw.text((505, 304), "Post-quantum digital signature verification", fill=(140, 160, 190), font=font_mono)
        draw.text((490, 334), "• Linux mlock() & MADV_DONTDUMP", fill=(0, 230, 118), font=font_body)
        
        draw.rounded_rectangle([60, 395, 840, 445], radius=8, fill=(18, 28, 48, 200), outline=(0, 230, 118, 120), width=1)
        draw.text((80, 410), "🔒 MEMORY CONTRACT: Keys locked in physical RAM. Never written to flash. Zeroized on close.", fill=(0, 230, 118), font=font_sub_bold)

    elif scene_id == "autofill":
        draw.text((60, 58), "HUMAN-CENTRIC & PHISHING-RESISTANT", fill=(0, 230, 118), font=font_badge)
        draw.text((60, 82), "1-Tap Autofill & Offline Security Suite", fill=(255, 255, 255), font=font_large_bold)
        draw.text((60, 126), "Effortless usability without sacrificing mathematical sovereign guarantees.", fill=(160, 180, 210), font=font_body)
        
        cards = [
            ("Anti-Phishing DomainMatcher", "Strict terminal-segment host validation. Rejects 'evil-paypal.com' or lookalike phishing attacks.", (0, 230, 118)),
            ("100% Offline Breach Checker", "In-memory Bloom filter k-Anonymity checks against billions of breached credentials.", (0, 212, 255)),
            ("Guided Password Assistant", "Never blindly batch-overwrites. Generates high-entropy keys with direct service links.", (124, 77, 255)),
            ("FLAG_SECURE Screen Privacy", "Enforced on Vault & Autofill dialogs. Blocks Android task snapshots & recording.", (255, 64, 129))
        ]
        
        y = 170
        for title, desc, col in cards:
            draw.rounded_rectangle([60, y, 840, y + 58], radius=8, fill=(18, 26, 42, 200), outline=(35, 50, 75, 255), width=1)
            draw.ellipse([80, y + 20, 92, y + 32], fill=col)
            draw.text((110, y + 10), title, fill=(255, 255, 255), font=font_sub_bold)
            draw.text((110, y + 32), desc, fill=(140, 160, 190), font=font_body)
            y += 70

    elif scene_id == "panic":
        draw.text((60, 58), "ANTI-COERCION UNDER DURESS", fill=(255, 64, 129), font=font_badge)
        draw.text((60, 82), "Duress Decoy Vault & Panic Destruction", fill=(255, 255, 255), font=font_large_bold)
        draw.text((60, 126), "Protection when you are physically forced to unlock your phone.", fill=(160, 180, 210), font=font_body)
        
        draw.rounded_rectangle([60, 180, 430, 420], radius=10, fill=(20, 18, 32, 240), outline=(124, 77, 255, 180), width=1)
        draw.text((80, 200), "DURESS DECOY VAULT", fill=(160, 130, 255), font=font_sub_bold)
        draw.text((80, 235), "Enter a secondary Duress PIN to open", fill=(255, 255, 255), font=font_body)
        draw.text((80, 260), "a believable decoy vault with real-looking", fill=(255, 255, 255), font=font_body)
        draw.text((80, 285), "accounts and safe balances.", fill=(255, 255, 255), font=font_body)
        draw.text((80, 325), "• 100% Isolated Decoy SQLite DB", fill=(0, 230, 118), font=font_mono)
        draw.text((80, 355), "• Zero leakage of primary keys", fill=(0, 230, 118), font=font_mono)
        draw.text((80, 385), "• Satisfies attackers under threat", fill=(0, 230, 118), font=font_mono)
        
        draw.rounded_rectangle([470, 180, 840, 420], radius=10, fill=(26, 16, 24, 240), outline=(255, 64, 129, 180), width=1)
        draw.text((490, 200), "FORENSIC DISK SHREDDER", fill=(255, 80, 140), font=font_sub_bold)
        draw.text((490, 235), "Trigger instant cryptographic wipe.", fill=(255, 255, 255), font=font_body)
        draw.text((490, 260), "Multi-pass CSPRNG random sector overwrite", fill=(255, 255, 255), font=font_body)
        draw.text((490, 285), "directly on existing flash blocks.", fill=(255, 255, 255), font=font_body)
        draw.text((490, 325), "• Synchronous fd.sync() flush", fill=(255, 64, 129), font=font_mono)
        draw.text((490, 355), "• Purges -wal and -shm transaction logs", fill=(255, 64, 129), font=font_mono)
        draw.text((490, 385), "• Zero forensic data recoverable", fill=(255, 64, 129), font=font_mono)

    elif scene_id == "outro":
        lw, lh = int(130 * pulse), int(130 * pulse)
        lx = (WIDTH - lw) // 2
        ly = 65
        ring_r = int(80 * pulse)
        draw.ellipse([WIDTH//2 - ring_r, 65 + 65 - ring_r, WIDTH//2 + ring_r, 65 + 65 + ring_r], outline=(0, 230, 118, 160), width=2)
        ring_r2 = int(105 * pulse)
        draw.ellipse([WIDTH//2 - ring_r2, 65 + 65 - ring_r2, WIDTH//2 + ring_r2, 65 + 65 + ring_r2], outline=(0, 212, 255, 90), width=1)
        
        if logo_img:
            l_scaled = logo_img.resize((lw, lh), Image.Resampling.LANCZOS)
            im.paste(l_scaled, (lx, ly), l_scaled)
            
        head = "KRYPTX: SIMPLE. HUMAN. SOVEREIGN."
        bbox = font_large_bold.getbbox(head)
        hw = bbox[2] - bbox[0]
        draw.text(((WIDTH - hw) // 2, 225), head, fill=(255, 255, 255), font=font_large_bold)
        
        sub = "The Zero-Network Post-Quantum Password Fortress for Android"
        bbox2 = font_sub_bold.getbbox(sub)
        sw = bbox2[2] - bbox2[0]
        draw.text(((WIDTH - sw) // 2, 275), sub, fill=(0, 230, 118), font=font_sub_bold)
        
        stats = "570 Unit Tests Passed (100%)  •  120 FPS Fluid Compose  •  100% Free & Open Source"
        bbox3 = font_body.getbbox(stats)
        stw = bbox3[2] - bbox3[0]
        draw.text(((WIDTH - stw) // 2, 325), stats, fill=(160, 180, 210), font=font_body)
        
        start_x = (WIDTH - 640) // 2
        draw_pill(draw, "GOOGLE PLAY STORE", (start_x, 385), border_color=(0, 230, 118), text_color=(0, 230, 118))
        draw_pill(draw, "STANDALONE APK ON GITHUB", (start_x + 195, 385), border_color=(0, 212, 255), text_color=(0, 212, 255))
        draw_pill(draw, "APACHE 2.0 LICENSE", (start_x + 460, 385), border_color=(124, 77, 255), text_color=(160, 130, 255))
        
    return im.convert("RGB")

print("Rendering storyboard keyframes...")
frames = []
durations = []

for scene_id, pulse, progress, dur in sequence:
    f = render_scene(scene_id, pulse, progress)
    frames.append(f)
    durations.append(dur)

# Save poster frame (from hook settled state)
poster_frame = frames[2]
poster_frame.save(os.path.join(OUTPUT_DIR, "brag.jpg"), quality=92)
print("Saved poster frame: brag-output/brag.jpg")

# Quantize and save GIF with single unified palette
print(f"Compiling {len(frames)} frames with dynamic durations (Total: {sum(durations)/1000:.1f}s)...")
first_frame = frames[0].quantize(colors=128, method=Image.Resampling.LANCZOS)
quantized_frames = [f.quantize(palette=first_frame) for f in frames[1:]]

gif_path = os.path.join(OUTPUT_DIR, "kryptx-showcase.gif")
first_frame.save(
    gif_path,
    save_all=True,
    append_images=quantized_frames,
    duration=durations,
    loop=0,
    optimize=True
)

gif_size = os.path.getsize(gif_path)
print(f"Saved showcase GIF: {gif_path} ({gif_size / 1024:.1f} KB)")
