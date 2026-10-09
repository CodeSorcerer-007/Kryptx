#![no_main]

use libfuzzer_sys::fuzz_target;
use kryptx_crypto::NativeCryptoEngine;

fuzz_target!(|data: &[u8]| {
    if data.len() < 32 {
        return;
    }
    let engine = NativeCryptoEngine::new();
    let key = data[..32].to_vec();
    let plaintext = data[32..].to_vec();

    if let Ok(ciphertext) = engine.encrypt(plaintext.clone(), key.clone()) {
        // Roundtrip decrypt
        let decrypted = engine.decrypt(ciphertext.clone(), key.clone()).expect("Decryption must succeed");
        assert_eq!(decrypted, plaintext, "Decrypted data must match original plaintext");

        // Bit flip integrity verification
        if !ciphertext.is_empty() {
            let mut corrupted = ciphertext;
            let flip_idx = (data.len() % corrupted.len().max(1)).min(corrupted.len() - 1);
            corrupted[flip_idx] ^= 0x01;
            assert!(engine.decrypt(corrupted, key).is_err(), "Bit-flipped ciphertext must fail decryption");
        }
    }
});
