package com.bhagwat.auth.authService.dpop;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

/**
 * Manages the JWKS key pair for signing Actor Tokens.
 * Actor Tokens are JWTs signed by this service's private key,
 * presented to Keycloak to prove service identity.
 *
 * Keycloak validates actor tokens by fetching this service's JWKS endpoint.
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

    @PostConstruct
    public void init() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        this.jwksKeyPair = kpg.generateKeyPair();
        log.info("JWKS RSA key pair generated for Actor Token signing (kid={})", KID);
    }

    /**
     * Create an Actor Token JWT signed by this service's JWKS private key.
     * Used for:
     * - Authenticating with Keycloak for token exchange
     * - Authorizing user sync operations
     *
     * @param subject who the token represents (e.g., username or "auth-service")
     * @param audience who the token is for (e.g., Keycloak realm URL)
     * @return signed JWT
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
     * Create an Actor Token for Keycloak interactions.
     */
    public String createActorTokenForKeycloak(String subject) {
        String audience = keycloakServerUrl + "/realms/" + realm;
        return createActorToken(subject, audience);
    }

    /**
     * Get the JWKS JSON (public keys) for external validation.
     * Exposed at /.well-known/jwks.json
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

    /**
     * Get the public key for direct verification (used internally).
     */
    public PublicKey getPublicKey() {
        return jwksKeyPair.getPublic();
    }

    public String getKid() {
        return KID;
    }

    public String getIssuer() {
        return ISSUER;
    }

    private byte[] toUnsignedBytes(java.math.BigInteger bigInt) {
        byte[] bytes = bigInt.toByteArray();
        // Remove leading zero byte if present (sign byte for positive numbers)
        if (bytes.length > 1 && bytes[0] == 0) {
            return Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }
}
