#![no_main]

use libfuzzer_sys::fuzz_target;
use kryptx_crypto::NativeCryptoEngine;

fuzz_target!(|data: &[u8]| {
    if data.len() < 16 {
        return;
    }
    let engine = NativeCryptoEngine::new();
    let salt = data[..16].to_vec();
    let password = data[16..].to_vec();

    // Key derivation with fast parameters must never panic on arbitrary inputs
    let _ = engine.derive_argon2id(password, salt, 64, 1, 1, 32);
});
