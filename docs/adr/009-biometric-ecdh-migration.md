# ADR 009: Biometric Key Wrapping and Key Agreement Migration (RSA-2048 to ECDH P-256)

## Status
Accepted

## Context
Android Keystore biometric authentication historically used RSA-2048 key pairs with OAEP padding (`RSA/ECB/OAEPWithSHA-256AndMGF1Padding`) to wrap the volatile vault database master key. While widely supported across legacy Android devices:
1. RSA-2048 operations incur noticeable latency during private key decryption on entry-level secure hardware.
2. RSA keys provide ~112 bits of security margin, which does not match the 256-bit security tier of XChaCha20-Poly1305 and AES-256-GCM.
3. Modern NIST and BSI guidelines recommend Elliptic Curve Cryptography (ECC) for long-term security and performance.

## Decision
Kryptx implements a phased dual-support migration from RSA-2048 to Elliptic Curve Diffie-Hellman over NIST P-256 (`secp256r1`):
1. **ECDH Key Agreement via Keystore**:
   New biometric enrollments generate an ephemeral EC key pair and derive the vault wrapping key via hardware-enforced ECDH key agreement with biometric user authentication authorization (`setUserAuthenticationRequired(true)`).
2. **Backward Compatibility**:
   Existing vaults enrolled with RSA-2048 continue to unlock transparently using their legacy Keystore wrapping key until re-enrollment or password rotation occurs.
3. **Automatic Upgrade on Re-enrollment**:
   When a user toggles biometrics or changes their master password, Kryptx automatically purges the legacy RSA key and provisions an ECDH P-256 key pair.

## Consequences
### Positive
- **Higher Security Margin**: Upgrades biometric key exchange to 128-bit quantum-equivalent / 256-bit classical cryptographic security.
- **Improved Performance**: ECDH operations in Android TEE / StrongBox are significantly faster than 2048-bit RSA modular exponentiation.
- **Seamless Upgrade**: Zero user friction or data loss for existing vault installations.

### Negative
- **Dual Verification Code Paths**: KeystoreManager must maintain both RSA and ECDH handling until legacy vault formats are fully phased out.
