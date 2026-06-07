package com.bhagwat.auth.authService.dpop;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/**
 * Manages the JWKS key pair for signing Actor Tokens.
 *
 * KEY PERSISTENCE: The RSA key pair is stored in a file on disk so that it
 * survives service restarts. This ensures Keycloak's cached JWKS remains valid.
 * If the key file doesn't exist (first startup), a new pair is generated.
 *
 * Keycloak validates actor tokens by fetching /.well-known/jwks.json from this service.
 */
@Service
@Slf4j
public class ActorTokenService {

    private static final String KID = "commart-auth-service-key-1";
    private static final String ISSUER = "commart-auth-service";

    private KeyPair jwksKeyPair;

    @Value("${keycloak.server-url:http://localhost:8181}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm:scm}")
    private String realm;

    @Value("${auth.jwks.key-store-path:${user.home}/.scm/auth-jwks-keypair}")
    private String keyStorePath;

    @PostConstruct
    public void init() throws Exception {
        File privateKeyFile = new File(keyStorePath + ".private");
        File publicKeyFile = new File(keyStorePath + ".public");

        if (privateKeyFile.exists() && publicKeyFile.exists()) {
            // Load persisted key pair
            this.jwksKeyPair = loadKeyPair(privateKeyFile, publicKeyFile);
            log.info("JWKS RSA key pair loaded from disk (kid={})", KID);
        } else {
            // Generate new key pair and persist
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            this.jwksKeyPair = kpg.generateKeyPair();
            saveKeyPair(this.jwksKeyPair, privateKeyFile, publicKeyFile);
            log.info("JWKS RSA key pair generated and saved to disk (kid={})", KID);
        }
    }

    /**
     * Create an Actor Token JWT signed by this service's JWKS private key.
     * Used for Keycloak interactions (token exchange, user sync).
     */
    public String createActorToken(String subject, String audience) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 300_000); // 5 minutes

        return Jwts.builder()
                .setHeaderParam("kid", KID)
                .setHeaderParam("typ", "JWT")
                .setIssuer(ISSUER)
                .setSubject(subject)
                .setAudience(audience)
                .claim("act", Map.of("sub", ISSUER, "iss", ISSUER))
                .setIssuedAt(now)
                .setExpiration(expiry)
                .setId(UUID.randomUUID().toString())
                .signWith(jwksKeyPair.getPrivate(), SignatureAlgorithm.RS256)
                .compact();
    }

    /**
     * Create an Actor Token for Keycloak interactions (refresh, user sync).
     */
    public String createActorTokenForKeycloak(String subject) {
        String audience = keycloakServerUrl + "/realms/" + realm;
        return createActorToken(subject, audience);
    }

    /**
     * Get the JWKS JSON (public keys) for external validation.
     * Exposed at /.well-known/jwks.json — Keycloak fetches this to verify actor tokens.
     */
    public Map<String, Object> getJwks() {
        RSAPublicKey publicKey = (RSAPublicKey) jwksKeyPair.getPublic();

        Map<String, Object> key = new LinkedHashMap<>();
        key.put("kty", "RSA");
        key.put("kid", KID);
        key.put("use", "sig");
        key.put("alg", "RS256");
        key.put("n", Base64.getUrlEncoder().withoutPadding().encodeToString(
                toUnsignedBytes(publicKey.getModulus())));
        key.put("e", Base64.getUrlEncoder().withoutPadding().encodeToString(
                toUnsignedBytes(publicKey.getPublicExponent())));

        return Map.of("keys", List.of(key));
    }

    public PublicKey getPublicKey() {
        return jwksKeyPair.getPublic();
    }

    public String getKid() {
        return KID;
    }

    public String getIssuer() {
        return ISSUER;
    }

    // ─── Key persistence ────────────────────────────────────────────────────

    private void saveKeyPair(KeyPair keyPair, File privateFile, File publicFile) throws Exception {
        privateFile.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(privateFile)) {
            fos.write(keyPair.getPrivate().getEncoded());
        }
        try (FileOutputStream fos = new FileOutputStream(publicFile)) {
            fos.write(keyPair.getPublic().getEncoded());
        }

        // Restrict permissions on private key
        privateFile.setReadable(false, false);
        privateFile.setReadable(true, true);
        privateFile.setWritable(false, false);
        privateFile.setWritable(true, true);
    }

    private KeyPair loadKeyPair(File privateFile, File publicFile) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        byte[] privateBytes;
        try (FileInputStream fis = new FileInputStream(privateFile)) {
            privateBytes = fis.readAllBytes();
        }
        PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));

        byte[] publicBytes;
        try (FileInputStream fis = new FileInputStream(publicFile)) {
            publicBytes = fis.readAllBytes();
        }
        PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicBytes));

        return new KeyPair(publicKey, privateKey);
    }

    private byte[] toUnsignedBytes(java.math.BigInteger bigInt) {
        byte[] bytes = bigInt.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            return Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }
}
