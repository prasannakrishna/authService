package com.bhagwat.auth.authService.controller;

import com.bhagwat.auth.authService.dpop.ActorTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes JWKS endpoint for Keycloak and other services to validate Actor Tokens.
 * Keycloak fetches this to verify tokens signed by this authService.
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final ActorTokenService actorTokenService;

    /**
     * JWKS endpoint — returns public keys used for Actor Token signing.
     * Keycloak should be configured to fetch this URL for token validation.
     */
    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok(actorTokenService.getJwks());
    }

    /**
     * OpenID Connect discovery document (minimal).
     * Helps Keycloak auto-discover our JWKS endpoint.
     */
    @GetMapping("/.well-known/openid-configuration")
    public ResponseEntity<Map<String, Object>> openIdConfiguration() {
        return ResponseEntity.ok(Map.of(
                "issuer", actorTokenService.getIssuer(),
                "jwks_uri", "http://localhost:8097/.well-known/jwks.json",
                "token_endpoint", "http://localhost:8097/auth/login",
                "grant_types_supported", java.util.List.of("password", "refresh_token"),
                "subject_types_supported", java.util.List.of("public"),
                "id_token_signing_alg_values_supported", java.util.List.of("RS256")
        ));
    }
}
