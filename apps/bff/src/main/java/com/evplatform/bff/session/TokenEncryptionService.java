package com.evplatform.bff.session;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * AES-GCM authenticated encryption of OAuth token material for the BFF
 * session store (SEC-001 §5.3, SEC-P01: tokens are stored ONLY in encrypted,
 * opaque form).
 *
 * <p>Format: {@code ciphertext = IV(12) || GCM(ct)} with a random 12-byte IV
 * per encryption and the session reference bound as Additional Authenticated
 * Data (AAD). The AAD binding means a ciphertext copied onto another session
 * row fails authentication ({@link javax.crypto.AEADBadTagException}) — the
 * caller treats such a session as invalid. No Java serialization anywhere;
 * the plaintext is the token JSON produced by
 * {@link SessionLifecycleService}.</p>
 */
@Service
public class TokenEncryptionService {

    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SessionKeyRing keyRing;
    private final SecureRandom random = new SecureRandom();

    public TokenEncryptionService(SessionKeyRing keyRing) {
        this.keyRing = keyRing;
    }

    /** Encrypted result: ciphertext bytes (IV prepended) + key id used. */
    public record Encrypted(byte[] ciphertext, String keyId) {
    }

    /**
     * Encrypts {@code plaintext} for the session identified by
     * {@code sessionRef} using the current key-ring key.
     */
    public Encrypted encrypt(byte[] plaintext, String sessionRef) {
        String keyId = keyRing.currentKeyId();
        byte[] key = keyRing.keyFor(keyId);
        byte[] iv = new byte[IV_LENGTH_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            cipher.updateAAD(sessionRef.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] ct = cipher.doFinal(plaintext);
            byte[] out = new byte[IV_LENGTH_BYTES + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LENGTH_BYTES);
            System.arraycopy(ct, 0, out, IV_LENGTH_BYTES, ct.length);
            return new Encrypted(out, keyId);
        } catch (GeneralSecurityException e) {
            // Never include plaintext or key material in the exception text.
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    /**
     * Decrypts {@code ciphertext} (IV prepended) with the key identified by
     * {@code keyId}, authenticating {@code sessionRef} as AAD.
     *
     * @throws javax.crypto.AEADBadTagException on tamper/wrong-key/wrong
     *         session binding — callers treat the session as invalid
     */
    public byte[] decrypt(byte[] ciphertext, String keyId, String sessionRef)
            throws GeneralSecurityException {
        if (ciphertext == null || ciphertext.length <= IV_LENGTH_BYTES) {
            throw new GeneralSecurityException("Ciphertext too short");
        }
        byte[] key = keyRing.keyFor(keyId);
        byte[] iv = new byte[IV_LENGTH_BYTES];
        System.arraycopy(ciphertext, 0, iv, 0, IV_LENGTH_BYTES);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        cipher.updateAAD(sessionRef.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return cipher.doFinal(ciphertext, IV_LENGTH_BYTES,
                ciphertext.length - IV_LENGTH_BYTES);
    }
}
