#![no_main]

use libfuzzer_sys::fuzz_target;
use kryptx_crypto::NativeCryptoEngine;

fuzz_target!(|data: &[u8]| {
    if data.len() < 32 {
        return;
    }
    let engine = NativeCryptoEngine::new();
    let key = data[..32].to_vec();
    let ciphertext = data[32..].to_vec();

    // Must never panic or trigger undefined behavior on arbitrary bytes, only return Ok or Err
    let _ = engine.decrypt(ciphertext.clone(), key.clone());
    let _ = engine.decrypt_aes_gcm(ciphertext, key, None);
});
