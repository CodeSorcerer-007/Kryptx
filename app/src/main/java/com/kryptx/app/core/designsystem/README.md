# Kryptx Design System — Component Catalog

The Kryptx Design System provides a cohesive set of Jetpack Compose components, theme tokens, and motion primitives that deliver a premium, glassmorphic, security-first user experience.

---

## 🎨 Theme Foundation

| File | Purpose |
|:---|:---|
| [`KryptxTheme.kt`](theme/KryptxTheme.kt) | Top-level Material 3 theme provider with dark/light mode support |
| [`KryptxColors.kt`](theme/KryptxColors.kt) | Curated HSL color palette with semantic security tokens (danger, success, warning) |
| [`KryptxTypography.kt`](theme/KryptxTypography.kt) | Typography scale using Inter/Roboto with monospace variants for codes and keys |
| [`KryptxShapes.kt`](theme/KryptxShapes.kt) | Centralized corner radius tokens (small, medium, large, pill) |
| [`KryptxMotion.kt`](theme/KryptxMotion.kt) | Spring physics, easing curves, and duration tokens for consistent animation |
| [`KryptxLocals.kt`](theme/KryptxLocals.kt) | CompositionLocal providers for haptics, audio, and sensory DI |

---

## 🧱 Component Library

### Core Input & Display

| Component | File | Description |
|:---|:---|:---|
| **KryptxTextField** | [`KryptxTextField.kt`](components/KryptxTextField.kt) | Secure text field with password masking, copy protection, and reveal toggle |
| **KryptxButton** | [`KryptxButton.kt`](components/KryptxButton.kt) | Primary/secondary/danger button variants with loading states and haptic feedback |
| **KryptxCard** | [`KryptxCard.kt`](components/KryptxCard.kt) | Frosted glassmorphism card with elevation, blur, and press animations |
| **KryptxBadge** | [`KryptxBadge.kt`](components/KryptxBadge.kt) | Semantic status badges (secure, warning, breached, expired) |
| **KryptxSnackbar** | [`KryptxSnackbar.kt`](components/KryptxSnackbar.kt) | Themed snackbar with auto-dismiss and action support |

### Navigation & Layout

| Component | File | Description |
|:---|:---|:---|
| **KryptxTopBar** | [`KryptxTopBar.kt`](components/KryptxTopBar.kt) | Collapsing app bar with search integration and action overflow |
| **KryptxBottomNavBar** | [`KryptxBottomNavBar.kt`](components/KryptxBottomNavBar.kt) | Material 3 bottom navigation with badge indicators and haptic tab switching |
| **KryptxEmptyState** | [`KryptxEmptyState.kt`](components/KryptxEmptyState.kt) | Illustrated empty state with call-to-action for empty vaults/lists |
| **KryptxFolderCard** | [`KryptxFolderCard.kt`](components/KryptxFolderCard.kt) | Category folder tile with item count and icon theming |

### Security & Visualization

| Component | File | Description |
|:---|:---|:---|
| **KryptxScoreRing** | [`KryptxScoreRing.kt`](components/KryptxScoreRing.kt) | Animated circular progress ring for vault security audit scores (0–100) |
| **KryptxLogo** | [`KryptxLogo.kt`](components/KryptxLogo.kt) | Animated vector logo with spring physics entrance |
| **KryptxParticles** | [`KryptxParticles.kt`](components/KryptxParticles.kt) | Ambient floating particle background for unlock/splash screens |
| **OfflineIdenticonGenerator** | [`OfflineIdenticonGenerator.kt`](components/OfflineIdenticonGenerator.kt) | Deterministic visual hash identicons for vault items (offline, no Gravatar) |

### Camera & QR

| Component | File | Description |
|:---|:---|:---|
| **AnimatedQrScanner** | [`AnimatedQrScanner.kt`](components/AnimatedQrScanner.kt) | Animated QR scan viewfinder with corner bracket animations |
| **QrCodeScannerDialog** | [`QrCodeScannerDialog.kt`](components/QrCodeScannerDialog.kt) | Full-screen CameraX QR scanner dialog with permission handling |
| **QrScannerOverlay** | [`QrScannerOverlay.kt`](components/QrScannerOverlay.kt) | Canvas overlay with scan region highlight and guidance text |
| **QrDecoderUtils** | [`QrDecoderUtils.kt`](components/QrDecoderUtils.kt) | ZXing bitmap decoding utilities for gallery QR import |

### Onboarding & Education

| Component | File | Description |
|:---|:---|:---|
| **FeatureIntroSheet** | [`FeatureIntroSheet.kt`](components/FeatureIntroSheet.kt) | Guided bottom sheet walkthroughs for first-time feature discovery |
| **SecurityExplainerSheet** | [`SecurityExplainerSheet.kt`](components/SecurityExplainerSheet.kt) | Contextual security education sheets explaining "why" before asking for permissions |
| **KryptxPermissionRationaleDialog** | [`KryptxPermissionRationaleDialog.kt`](components/KryptxPermissionRationaleDialog.kt) | Pre-prompt permission rationale with clear explanations |

### Sensory Feedback

| Component | File | Description |
|:---|:---|:---|
| **KryptxHaptics** | [`KryptxHaptics.kt`](components/KryptxHaptics.kt) | Haptic feedback profiles (tap, success, error, panic shake) |
| **KryptxAudio** | [`KryptxAudio.kt`](components/KryptxAudio.kt) | Procedural sine wave audio synthesizer for lock/unlock feedback |
| **KryptxAnimations** | [`KryptxAnimations.kt`](components/KryptxAnimations.kt) | Shared animation specs, spring configs, and transition definitions |

---

## 🎬 Motion System

All animations use the centralized `KryptxMotion` system:

| Token | Value | Usage |
|:---|:---|:---|
| `KryptxMotion.SpringStiffness.gentle` | 200f | Card reveals, sheet expansions |
| `KryptxMotion.SpringStiffness.snappy` | 800f | Button presses, tab switches |
| `KryptxMotion.SpringDamping.bouncy` | 0.6f | Playful entrance animations |
| `KryptxMotion.SpringDamping.smooth` | 0.8f | Navigation transitions |
| `KryptxMotion.Duration.fast` | 150ms | Micro-interactions, highlights |
| `KryptxMotion.Duration.medium` | 300ms | Standard transitions |
| `KryptxMotion.Duration.slow` | 500ms | Full-screen reveals |

---

## 🔐 Security-Aware Design Principles

1. **Password fields default to masked** — `VisualTransformation.passwordDots` is always applied; explicit user action is required to reveal
2. **`FLAG_SECURE` awareness** — Components that display sensitive data check the global FLAG_SECURE state
3. **Clipboard sensitivity** — Copy actions tag clipboard with `ClipDescription.EXTRA_IS_SENSITIVE`
4. **Accessibility** — All interactive elements include content descriptions; TOTP countdowns use `LiveRegionMode.Polite`
5. **No external network resources** — No CDN fonts, no remote images, no analytics pixels. Everything is bundled.
