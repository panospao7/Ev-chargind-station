package com.evplatform.bff.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The ev-bff OAuth2 client signing key for {@code private_key_jwt} client
 * authentication (SEC-001 §4.2/§5.1). The key path arrives via environment
 * ({@code BFF_OAUTH_CLIENT_PRIVATE_KEY_PATH} → {@code bff.oauth.client-private-key-path});
 * the committed default is EMPTY and the bean fails fast at startup when the
 * path is unset, blank, unreadable, or not a valid PKCS#8 RSA private key —
 * mirroring the {@code SessionKeyRing} fail-fast pattern (key material is
 * never committed, AGENTS.md §4 / SEC-001 §5.3).
 *
 * <p><b>Conditional-bean decision (task A2):</b> the conditional approach
 * ({@code @ConditionalOnProperty(name = "bff.oauth.client-private-key-path")})
 * was rejected after verification: the property has an EMPTY committed
 * default in application.yml, and an empty string still satisfies
 * {@code @ConditionalOnProperty} (only {@code matchIfMissing} controls the
 * missing case), so the bean would silently start with a blank path —
 * exactly the failure mode fail-fast exists to prevent. Unconditional
 * fail-fast also means the smoke tests must supply a generated test key;
 * they do (see the test cluster).</p>
 *
 * <p>{@code kid} derivation: RFC 7638 SHA-256 thumbprint of the public JWK
 * ({@code RSAKey.Builder#keyIDFromThumbprint("SHA-256")} — the builder
 * computes the thumbprint over its public key; the {@code JWK.computeThumbprint}
 * form applies to a materialized JWK), matching the JWKS published for the
 * ev-bff client in the ev-local realm import.</p>
 *
 * <p>PEM format: PKCS#8 ({@code -----BEGIN PRIVATE KEY-----}); the loader
 * strips the BEGIN/END markers and all whitespace before Base64-decoding,
 * so header/comment variants do not break parsing. PKCS#1
 * ({@code RSA PRIVATE KEY}) fails in {@link KeyFactory} with a clear
 * error — converting it is out of scope.</p>
 */
@Configuration
public class ClientKeyConfig {

    /** Bean name referenced from the SecurityConfig wiring. */
    public static final String BFF_CLIENT_JWK_BEAN = "bffClientJwk";

    @Bean(name = BFF_CLIENT_JWK_BEAN)
    public RSAKey bffClientJwk(
            @Value("${bff.oauth.client-private-key-path:}") String privateKeyPath) {
        if (privateKeyPath == null || privateKeyPath.isBlank()) {
            throw new IllegalStateException(
                    "No BFF OAuth client signing key configured (bff.oauth.client-private-key-path). "
                            + "Set BFF_OAUTH_CLIENT_PRIVATE_KEY_PATH to a PKCS#8 PEM file path. "
                            + "Refusing to start without private_key_jwt client authentication "
                            + "(SEC-001 §4.2/§5.1).");
        }
        Path path = Path.of(privateKeyPath);
        final String pem;
        try {
            pem = Files.readString(path, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "BFF OAuth client signing key not readable at " + path, e);
        }
        RSAPrivateKey privateKey = parsePkcs8RsaPrivateKey(pem);
        if (!(privateKey instanceof java.security.interfaces.RSAPrivateCrtKey crtKey)) {
            throw new IllegalStateException(
                    "BFF OAuth client signing key is not a CRT RSA private key "
                            + "(cannot derive the public key without the public exponent)");
        }
        RSAPublicKey publicKey;
        try {
            // A PKCS#8 RSAPrivateCrtKeySpec carries the modulus and public
            // exponent; the public key is derived from them without a
            // separate public-key file.
            java.security.KeyFactory factory = KeyFactory.getInstance("RSA");
            PublicKey derived = factory.generatePublic(
                    new java.security.spec.RSAPublicKeySpec(
                            crtKey.getModulus(), crtKey.getPublicExponent()));
            publicKey = (RSAPublicKey) derived;
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(
                    "BFF OAuth client signing key could not derive its public key", e);
        }
        try {
            // kid = RFC 7638 SHA-256 thumbprint of the public JWK
            // (keyIDFromThumbprint computes it on the builder's key).
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyIDFromThumbprint("SHA-256")
                    .build();
        } catch (JOSEException e) {
            throw new IllegalStateException(
                    "BFF OAuth client signing key thumbprint derivation failed", e);
        }
    }

    private static RSAPrivateKey parsePkcs8RsaPrivateKey(String pem) {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        final byte[] der;
        try {
            der = Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "BFF OAuth client signing key is not valid Base64", e);
        }
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PrivateKey key = factory.generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(key instanceof RSAPrivateKey rsaKey)) {
                throw new IllegalStateException(
                        "BFF OAuth client signing key is not an RSA private key");
            }
            return rsaKey;
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(
                    "BFF OAuth client signing key is not a PKCS#8 RSA private key "
                            + "(expected -----BEGIN PRIVATE KEY-----)", e);
        }
    }
}
