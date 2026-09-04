uniffi::setup_scaffolding!();

use aes_gcm::{
    aead::{Aead, KeyInit, Payload},
    Aes256Gcm, Nonce,
};
use argon2::Argon2;
use chacha20poly1305::{
    aead::Aead as ChaChaAead,
    aead::KeyInit as ChaChaKeyInit,
    XChaCha20Poly1305,
};
use rand::Rng;
use std::fmt;

#[derive(uniffi::Object)]
pub struct NativeCryptoEngine;

#[uniffi::export]
impl NativeCryptoEngine {
    #[uniffi::constructor]
    pub fn new() -> Self {
        NativeCryptoEngine
    }

    pub fn generate_salt(&self, length: u32) -> Vec<u8> {
        let mut salt = vec![0u8; length as usize];
        rand::rng().fill_bytes(&mut salt[..]);
        salt
    }

    pub fn encrypt(&self, plaintext: Vec<u8>, key: Vec<u8>) -> Result<Vec<u8>, NativeCryptoError> {
        let cipher = XChaCha20Poly1305::new_from_slice(&key).map_err(|_| NativeCryptoError::InvalidKeyLength)?;
        let mut nonce_bytes = [0u8; 24];
        rand::rng().fill_bytes(&mut nonce_bytes);
        let nonce = chacha20poly1305::XNonce::from(nonce_bytes);
        
        let mut ciphertext = cipher.encrypt(&nonce, plaintext.as_ref())
            .map_err(|_| NativeCryptoError::EncryptionFailed)?;
        
        let mut result = nonce_bytes.to_vec();
        result.append(&mut ciphertext);
        Ok(result)
    }

    pub fn decrypt(&self, payload: Vec<u8>, key: Vec<u8>) -> Result<Vec<u8>, NativeCryptoError> {
        if payload.len() < 24 {
            return Err(NativeCryptoError::InvalidPayloadLength);
        }
        let cipher = XChaCha20Poly1305::new_from_slice(&key).map_err(|_| NativeCryptoError::InvalidKeyLength)?;
        
        let nonce_bytes: [u8; 24] = payload[..24].try_into().map_err(|_| NativeCryptoError::InvalidPayloadLength)?;
        let nonce = chacha20poly1305::XNonce::from(nonce_bytes);
        let ciphertext = &payload[24..];
        
        cipher.decrypt(&nonce, ciphertext).map_err(|_| NativeCryptoError::DecryptionFailed)
    }

    pub fn encrypt_aes_gcm(
        &self,
        plaintext: Vec<u8>,
        key: Vec<u8>,
        aad: Option<Vec<u8>>,
    ) -> Result<Vec<u8>, NativeCryptoError> {
        if key.len() != 32 {
            return Err(NativeCryptoError::InvalidKeyLength);
        }
        let cipher = Aes256Gcm::new_from_slice(&key).map_err(|_| NativeCryptoError::InvalidKeyLength)?;
        let mut nonce_bytes = [0u8; 12];
        rand::rng().fill_bytes(&mut nonce_bytes);
        let nonce = Nonce::from_slice(&nonce_bytes);

        let ciphertext = match &aad {
            Some(aad_bytes) => cipher
                .encrypt(nonce, Payload { msg: &plaintext, aad: aad_bytes })
                .map_err(|_| NativeCryptoError::EncryptionFailed)?,
            None => cipher
                .encrypt(nonce, plaintext.as_ref())
                .map_err(|_| NativeCryptoError::EncryptionFailed)?,
        };

        let mut result = nonce_bytes.to_vec();
        result.extend(ciphertext);
        Ok(result)
    }

    pub fn decrypt_aes_gcm(
        &self,
        payload: Vec<u8>,
        key: Vec<u8>,
        aad: Option<Vec<u8>>,
    ) -> Result<Vec<u8>, NativeCryptoError> {
        if payload.len() < 12 {
            return Err(NativeCryptoError::InvalidPayloadLength);
        }
        if key.len() != 32 {
            return Err(NativeCryptoError::InvalidKeyLength);
        }
        let cipher = Aes256Gcm::new_from_slice(&key).map_err(|_| NativeCryptoError::InvalidKeyLength)?;
        let nonce = Nonce::from_slice(&payload[..12]);
        let ciphertext = &payload[12..];

        let plaintext = match &aad {
            Some(aad_bytes) => cipher
                .decrypt(nonce, Payload { msg: ciphertext, aad: aad_bytes })
                .map_err(|_| NativeCryptoError::DecryptionFailed)?,
            None => cipher
                .decrypt(nonce, ciphertext)
                .map_err(|_| NativeCryptoError::DecryptionFailed)?,
        };

        Ok(plaintext)
    }

    pub fn derive_key(&self, password: &str, salt: Vec<u8>) -> Result<Vec<u8>, NativeCryptoError> {
        let argon2 = Argon2::default();
        let mut output = vec![0u8; 32];
        argon2.hash_password_into(password.as_bytes(), &salt, &mut output)
            .map_err(|_| NativeCryptoError::KeyDerivationFailed)?;
        Ok(output)
    }
}

#[derive(Debug, uniffi::Error)]
pub enum NativeCryptoError {
    InvalidKeyLength,
    InvalidPayloadLength,
    EncryptionFailed,
    DecryptionFailed,
    KeyDerivationFailed,
}

impl fmt::Display for NativeCryptoError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{:?}", self)
    }
}
impl std::error::Error for NativeCryptoError {}

use jni::JNIEnv;
use jni::objects::{JClass, JObject};
use jni::sys::jboolean;

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kryptx_app_core_crypto_SecureMemory_mlockBuffer<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    buffer: JObject<'local>,
) -> jboolean {
    if let Ok(_address) = env.get_direct_buffer_address((&buffer).into()) {
        if let Ok(_capacity) = env.get_direct_buffer_capacity((&buffer).into()) {
            #[cfg(target_family = "unix")]
            unsafe {
                libc::mlock(_address as *mut libc::c_void, _capacity);
                libc::madvise(_address as *mut libc::c_void, _capacity, libc::MADV_DONTDUMP);
            }
            return 1;
        }
    }
    0
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_kryptx_app_core_crypto_SecureMemory_munlockBuffer<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    buffer: JObject<'local>,
) -> jboolean {
    if let Ok(_address) = env.get_direct_buffer_address((&buffer).into()) {
        if let Ok(_capacity) = env.get_direct_buffer_capacity((&buffer).into()) {
            #[cfg(target_family = "unix")]
            unsafe {
                libc::munlock(_address as *mut libc::c_void, _capacity);
            }
            return 1;
        }
    }
    0
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_xchacha_roundtrip() {
        let engine = NativeCryptoEngine::new();
        let key = engine.generate_salt(32);
        let plaintext = b"Hello, Kryptx Native Rust Fortress!".to_vec();
        let ciphertext = engine.encrypt(plaintext.clone(), key.clone()).unwrap();
        assert_ne!(ciphertext, plaintext);
        let decrypted = engine.decrypt(ciphertext, key).unwrap();
        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn test_aes_gcm_roundtrip() {
        let engine = NativeCryptoEngine::new();
        let key = engine.generate_salt(32);
        let plaintext = b"Vault Secret Data".to_vec();
        let aad = Some(b"item-aad-meta".to_vec());
        let ciphertext = engine.encrypt_aes_gcm(plaintext.clone(), key.clone(), aad.clone()).unwrap();
        assert_ne!(ciphertext, plaintext);
        let decrypted = engine.decrypt_aes_gcm(ciphertext, key, aad).unwrap();
        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn test_argon2_derivation() {
        let engine = NativeCryptoEngine::new();
        let salt = engine.generate_salt(16);
        let key = engine.derive_key("SuperSecretMasterPassword#2026", salt).unwrap();
        assert_eq!(key.len(), 32);
    }
}
