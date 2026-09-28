# ADR 005: Post-Quantum Readiness via NIST FIPS 203 (ML-KEM) and FIPS 204 (ML-DSA)

## Status
Accepted

## Context
Adversaries and nation-state threat actors are actively pursuing "Harvest Now, Decrypt Later" (HNDL) strategies, archiving encrypted communications and database backups today with the expectation of decrypting them once cryptographically relevant quantum computers (CRQCs) become viable.
1. Traditional public-key algorithms (RSA, ECC P-256, Ed25519) will be fully broken by Shor's algorithm on a quantum computer.
2. In August 2024, NIST finalized the official post-quantum standards: FIPS 203 (ML-KEM / Kyber) and FIPS 204 (ML-DSA / Dilithium).
3. Vault sharing, device-to-device credential pairing, and identity verification in long-term credential managers must resist both classical and post-quantum attacks.

## Decision
Kryptx adopts proactive post-quantum hybrid protection via `PostQuantumEngine`:
- Implements NIST FIPS 203 ML-KEM-768 for quantum-safe key encapsulation.
- Implements NIST FIPS 204 ML-DSA-65 for quantum-safe digital signatures.
- Where asymmetric key exchanges or identity verification are performed (e.g., future offline vault sharing or backup attestation), Kryptx utilizes a hybrid construction combining classical P-256 / X25519 with ML-KEM-768. An attacker must break *both* the classical and post-quantum algorithms to compromise the session key.
- Symmetric encryption retains 256-bit key lengths (AES-256 and XChaCha20), which already provide 128-bit security against Grover's quantum search algorithm, well above the threshold of practical computability.

## Consequences
### Positive
- **Future-Proof Security**: Vault backups and hybrid exchanges remain confidential even against future quantum adversaries.
- **NIST Standard Compliance**: Direct adoption of ratified FIPS 203 / 204 standards rather than provisional or experimental algorithms.

### Negative
- **Public Key & Ciphertext Size**: ML-KEM-768 keys (1,184 bytes public key, 1,088 bytes ciphertext) and ML-DSA-65 signatures (~3,309 bytes) are substantially larger than classical ECC keys (~64-96 bytes).
- **Computational Overhead**: Polynomial ring multiplication introduces additional CPU cycles during key encapsulation, though well within acceptable tolerances for offline mobile devices.
