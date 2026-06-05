package com.bhagwat.auth.authService.controller;

import com.bhagwat.auth.authService.config.CryptoService;
import com.bhagwat.auth.authService.dpop.ActorTokenService;
import com.bhagwat.auth.authService.dpop.DPoPBindingStore;
import com.bhagwat.auth.authService.dpop.DPoPService;
import com.bhagwat.auth.authService.dpop.DPoPValidationException;
import com.bhagwat.auth.authService.dto.AuthRequest;
import com.bhagwat.auth.authService.dto.AuthResponse;
import com.bhagwat.auth.authService.services.KeycloakAuthService;
import com.bhagwat.auth.authService.services.LoginAuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final KeycloakAuthService keycloakAuthService;
    private final CryptoService cryptoService;
    private final LoginAuditService loginAuditService;
    private final DPoPService dpopService;
    private final ActorTokenService actorTokenService;
    private final DPoPBindingStore dpopBindingStore;

    public AuthController(KeycloakAuthService keycloakAuthService, CryptoService cryptoService,
                          LoginAuditService loginAuditService, DPoPService dpopService,
                          ActorTokenService actorTokenService, DPoPBindingStore dpopBindingStore) {
        this.keycloakAuthService = keycloakAuthService;
        this.cryptoService = cryptoService;
        this.loginAuditService = loginAuditService;
        this.dpopService = dpopService;
        this.actorTokenService = actorTokenService;
        this.dpopBindingStore = dpopBindingStore;
    }

    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> publicKey() {
        return ResponseEntity.ok(Map.of("publicKey", cryptoService.getPublicKeyBase64()));
    }

    /**
     * Authenticates user via Keycloak and returns JWT with claims:
     * tenantId, domainType, roleType, subscriptionType.
     * If DPoP header present, validates proof and binds token to client key.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest authRequest,
                                               @RequestHeader(value = "DPoP", required = false) String dpopProof,
                                               HttpServletRequest httpRequest) {
        String password;
        try {
            String enc = authRequest.getEncryptedPassword();
            password = (enc != null && !enc.isBlank())
                    ? cryptoService.decrypt(enc)
                    : authRequest.getPassword();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        // Validate DPoP proof if present
        String dpopThumbprint = null;
        if (dpopProof != null && !dpopProof.isBlank()) {
            try {
                String requestUri = "http://localhost:8097/auth/login";
                dpopThumbprint = dpopService.validateProof(dpopProof, "POST", requestUri, null);
            } catch (DPoPValidationException e) {
                // DPoP validation failed — log but don't block login (DPoP is an enhancement, not a gate)
                System.err.println("DPoP validation failed (non-blocking): " + e.getMessage());
                dpopThumbprint = null;
            }
        }

        try {
            AuthResponse response = keycloakAuthService.login(authRequest.getUsername(), password);

            // Bind DPoP thumbprint to refresh token
            if (dpopThumbprint != null && response.getRefreshToken() != null) {
                dpopBindingStore.bind(response.getRefreshToken(), dpopThumbprint);
                response.setTokenType("DPoP"); // Signal to client that token is DPoP-bound
                response.setDpopThumbprint(dpopThumbprint);
            }

            loginAuditService.logLogin(response.getUserId(), authRequest.getUsername(), response.getOrgId());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            loginAuditService.logLoginFailed(authRequest.getUsername(), e.getMessage());
            throw e;
        }
    }

    /**
     * Refresh token endpoint. If DPoP header present, validates that the same
     * key is used as at login time. Creates an Actor Token for Keycloak interaction.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> body,
                                                 @RequestHeader(value = "DPoP", required = false) String dpopProof,
                                                 HttpServletRequest httpRequest) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // Validate DPoP proof if present
        String dpopThumbprint = null;
        if (dpopProof != null && !dpopProof.isBlank()) {
            try {
                String requestUri = "http://localhost:8097/auth/refresh";
                String accessToken = body.get("accessToken"); // optional for ath validation
                dpopThumbprint = dpopService.validateProof(dpopProof, "POST", requestUri, accessToken);
            } catch (DPoPValidationException e) {
                return ResponseEntity.status(401).body(
                        AuthResponse.builder().tokenType("error").username(e.getMessage()).build());
            }

            // Verify DPoP thumbprint matches the one bound at login
            if (!dpopBindingStore.verify(refreshToken, dpopThumbprint)) {
                return ResponseEntity.status(401).body(
                        AuthResponse.builder().tokenType("error")
                                .username("DPoP key mismatch — token was bound to a different key").build());
            }
        }

        try {
            // Create Actor Token for Keycloak interaction
            String actorToken = actorTokenService.createActorTokenForKeycloak("auth-service-refresh");

            AuthResponse response = keycloakAuthService.refresh(refreshToken);

            // Rebind DPoP to new refresh token
            if (dpopThumbprint != null && response.getRefreshToken() != null) {
                dpopBindingStore.remove(refreshToken); // Remove old binding
                dpopBindingStore.bind(response.getRefreshToken(), dpopThumbprint);
                response.setTokenType("DPoP");
                response.setDpopThumbprint(dpopThumbprint);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }

    /**
     * Returns claims from the validated Keycloak JWT.
     * Token is validated automatically by Spring's oauth2 resource server.
     * Downstream services call this to verify a Bearer token.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(Map.of(
                "username",         jwt.getSubject(),
                "tenantId",         nullSafe(jwt.getClaimAsString("tenantId")),
                "domainType",       nullSafe(jwt.getClaimAsString("domainType")),
                "roleType",         nullSafe(jwt.getClaimAsString("roleType")),
                "subscriptionType", nullSafe(jwt.getClaimAsString("subscriptionType"))
        ));
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
