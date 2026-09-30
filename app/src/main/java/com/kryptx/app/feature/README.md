# Kryptx Feature Layer (`com.kryptx.app.feature`)

The `feature` package contains the Jetpack Compose screens, ViewModels, UI state definitions, and user interaction flows for Kryptx Password Manager.

## Feature Modules

| Feature | Screen / Destination | Responsibility |
| :--- | :--- | :--- |
| `unlock` | `UnlockScreen` | Master password entry and Biometric authentication with scrambled PIN pad fallback |
| `auth` | `SetupMasterPasswordScreen` | First-run onboarding and Argon2id master password enrollment |
| `vault` | `VaultDashboardScreen`, `AddEditItemScreen` | Encrypted vault browsing, category filtering, search, and credential editing |
| `generator` | `GeneratorScreen` | Configurable password, passphrase, and cryptographic token generator |
| `securitycenter` | `SecurityCenterScreen` | Offline credential audit, password strength assessment, reuse/pwned analysis |
| `search` | `SearchScreen` | Real-time in-memory vault filtering and fuzzy credential search |
| `settings` | `SettingsScreen` | Auto-lock timeouts, biometrics toggle, encrypted backup export/import, theme selection |
| `totp` | `TotpListScreen`, `TotpScanScreen` | 2FA TOTP authenticator codes with animated countdown timers and camera QR scanning |
| `autofill` | `KryptxAutofillService` | Android Autofill Framework integration for credential recognition in apps/browsers |
| `navigation` | `KryptxNavGraph` | Process-death-resilient navigation graph utilizing `ScreenBackStackSaver` |

## UI & Accessibility Standards

1. **Unidirectional Data Flow (UDF)**:
   - Every feature exposes an immutable `StateFlow<UiState>` from its ViewModel.
   - User interactions trigger explicit events/methods on the ViewModel.

2. **Automated Testing testTags**:
   - Every interactive element (buttons, text fields, tabs, dialogs) MUST have a dedicated `testTag` prefixed with the feature name (e.g., `"unlock:pin_button_3"`, `"vault:search_input"`, `"generator:length_slider"`).
   - All tests in `app/src/androidTest/` rely on these stable testTags.

3. **Accessibility & TalkBack Support**:
   - Dynamic counter updates (such as the TOTP expiration ticker) utilize `liveRegion = LiveRegionMode.Polite` to avoid interrupting user interactions while maintaining accessibility.
   - Custom controls (like scrambled PIN buttons and navigation items) specify `semantics { stateDescription = ... }` for clear auditory feedback.

4. **Process Death Resiliency**:
   - Navigation backstack is managed via `ScreenBackStackSaver` to guarantee seamless restoration when Android terminates the app process in background.

5. **No Direct Application Casting**:
   - Composables must NEVER cast `LocalContext.current.applicationContext` to `KryptxApplication`. Repositories and ViewModels are passed as explicit parameters or via AndroidX `viewModel()` factories.
