package com.evplatform.bff.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for the AES-GCM token-material encryption (SEC-001 §5.3):
 * round-trip, wrong key fails, tampered ciphertext fails, AAD session
 * binding, key-id selection.
 */
class TokenEncryptionServiceTests {

    private static final String REF = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private static String base64Key(long seed) {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (seed + i);
        }
        return Base64.getEncoder().encodeToString(key);
    }

    private static TokenEncryptionService service(Map<String, String> keys) {
        SessionKeyRing ring = new SessionKeyRing(keys);
        ring.afterPropertiesSet();
        return new TokenEncryptionService(ring);
    }

    @Test
    void roundTripDecryptsPlaintext() throws Exception {
        TokenEncryptionService svc = service(Map.of("v1", base64Key(1)));
        byte[] plaintext = "{\"accessToken\":\"at\"}".getBytes(StandardCharsets.UTF_8);
        TokenEncryptionService.Encrypted enc = svc.encrypt(plaintext, REF);
        byte[] out = svc.decrypt(enc.ciphertext(), enc.keyId(), REF);
        assertThat(new String(out, StandardCharsets.UTF_8))
                .isEqualTo("{\"accessToken\":\"at\"}");
    }

    @Test
    void wrongKeyFailsAuthentication() {
        TokenEncryptionService encryptor = service(Map.of("v1", base64Key(1)));
        TokenEncryptionService decryptor = service(Map.of("v1", base64Key(2)));
        TokenEncryptionService.Encrypted enc =
                encryptor.encrypt("secret".getBytes(StandardCharsets.UTF_8), REF);
        assertThatThrownBy(() -> decryptor.decrypt(enc.ciphertext(), "v1", REF))
                .isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    void tamperedCiphertextFailsAuthentication() {
        TokenEncryptionService svc = service(Map.of("v1", base64Key(1)));
        TokenEncryptionService.Encrypted enc =
                svc.encrypt("secret".getBytes(StandardCharsets.UTF_8), REF);
        byte[] tampered = enc.ciphertext().clone();
        tampered[tampered.length - 1] ^= 0x01;
        assertThatThrownBy(() -> svc.decrypt(tampered, "v1", REF))
                .isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    void ciphertextBoundToSessionRefViaAad() throws Exception {
        TokenEncryptionService svc = service(Map.of("v1", base64Key(1)));
        TokenEncryptionService.Encrypted enc =
                svc.encrypt("secret".getBytes(StandardCharsets.UTF_8), "ref-one-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        // Same key, different session binding → must fail.
        assertThatThrownBy(() -> svc.decrypt(enc.ciphertext(), "v1", "ref-two-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
                .isInstanceOf(GeneralSecurityException.class);
        // Correct binding → succeeds.
        byte[] out = svc.decrypt(enc.ciphertext(), "v1", "ref-one-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        assertThat(new String(out, StandardCharsets.UTF_8)).isEqualTo("secret");
    }

    @Test
    void keyIdIsRecordedAndSelectionUsesGreatestConfiguredId() {
        TokenEncryptionService svc = service(Map.of(
                "v1", base64Key(1), "v2", base64Key(9)));
        TokenEncryptionService.Encrypted enc =
                svc.encrypt("secret".getBytes(StandardCharsets.UTF_8), REF);
        assertThat(enc.keyId()).isEqualTo("v2");
    }

    @Test
    void emptyKeyRingFailsFast() {
        SessionKeyRing ring = new SessionKeyRing(Map.of("v1", ""));
        assertThatThrownBy(ring::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No BFF session encryption keys configured");
    }

    @Test
    void wrongLengthKeyRejectedAtConstruction() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new SessionKeyRing(Map.of("v1", shortKey)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void unknownKeyIdRejected() {
        SessionKeyRing ring = new SessionKeyRing(Map.of("v1", base64Key(1)));
        ring.afterPropertiesSet();
        assertThatThrownBy(() -> ring.keyFor("v9"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown");
    }
}
