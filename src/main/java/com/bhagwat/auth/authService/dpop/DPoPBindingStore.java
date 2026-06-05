package com.bhagwat.auth.authService.dpop;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores the binding between DPoP thumbprints and refresh tokens.
 * On refresh, we verify that the same DPoP key is used as at login time.
 *
 * In production, this should be backed by Redis for distributed deployments.
 * For now, in-memory ConcurrentHashMap suffices for single-instance.
 */
@Component
@Slf4j
public class DPoPBindingStore {

    // Key: refresh token hash, Value: DPoP JWK Thumbprint
    private final Map<String, String> bindings = new ConcurrentHashMap<>();

    /**
     * Bind a DPoP thumbprint to a refresh token (called at login).
     */
    public void bind(String refreshToken, String dpopThumbprint) {
        String key = hash(refreshToken);
        bindings.put(key, dpopThumbprint);
        log.debug("DPoP binding stored: refreshToken hash={}, thumbprint={}", key.substring(0, 8), dpopThumbprint.substring(0, 8));
    }

    /**
     * Verify that the DPoP thumbprint matches the one bound at login.
     * Returns true if no binding exists (DPoP was optional at login).
     */
    public boolean verify(String refreshToken, String dpopThumbprint) {
        String key = hash(refreshToken);
        String storedThumbprint = bindings.get(key);
        if (storedThumbprint == null) {
            // No DPoP binding for this token — allow (backwards compatible)
            return true;
        }
        return storedThumbprint.equals(dpopThumbprint);
    }

    /**
     * Remove binding when refresh token is revoked or expired.
     */
    public void remove(String refreshToken) {
        bindings.remove(hash(refreshToken));
    }

    private String hash(String token) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            return token; // fallback — shouldn't happen
        }
    }
}
