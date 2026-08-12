package com.bhagwat.auth.authService.services;

import com.bhagwat.auth.authService.dto.AuthResponse;
import com.bhagwat.auth.authService.dto.KeycloakTokenResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;

@Service
@Slf4j
public class KeycloakAuthService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret:}")
    private String clientSecret;

    @Value("${userservice.base-url:http://localhost:8087}")
    private String userServiceBaseUrl;

    public KeycloakAuthService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    public AuthResponse login(String username, String password) {
        // Step 1: Validate credentials against PostgreSQL via userService before touching Keycloak
        try {
            String validateUrl = userServiceBaseUrl + "/api/users/validate";
            HttpHeaders validateHeaders = new HttpHeaders();
            validateHeaders.setContentType(MediaType.APPLICATION_JSON);
            String validateBody = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
            HttpEntity<String> validateRequest = new HttpEntity<>(validateBody, validateHeaders);
            ResponseEntity<Map> validateResponse = restTemplate.postForEntity(validateUrl, validateRequest, Map.class);
            Map<String, Object> validateResult = validateResponse.getBody();
            if (validateResult == null || !Boolean.TRUE.equals(validateResult.get("success"))) {
                String msg = validateResult != null ? (String) validateResult.get("message") : "Invalid credentials";
                log.warn("UserService credential validation failed for '{}': {}", username, msg);
                throw new RuntimeException(msg);
            }
            log.info("UserService credential validation passed for '{}'", username);
        } catch (HttpClientErrorException e) {
            log.error("UserService validation call failed for '{}': status={}, body={}",
                    username, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Authentication failed: invalid username or password");
        }

        // Step 2: Credentials verified — authenticate with Keycloak to obtain JWT
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        form.add("username", username);
        form.add("password", password);
        log.info("Sending authentication request to Keycloak for user '{}'...", username);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        KeycloakTokenResponse tokenResponse;
        try {
            ResponseEntity<KeycloakTokenResponse> response = restTemplate.postForEntity(
                    tokenUrl, request, KeycloakTokenResponse.class);
            tokenResponse = response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Keycloak authentication failed for user '{}': status={}, body={}",
                    username, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Authentication failed: invalid username or password");
        }

        if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
            throw new RuntimeException("No token received from Keycloak");
        }

        Map<String, Object> claims = extractClaims(tokenResponse.getAccessToken());

        AuthResponse.AuthResponseBuilder builder = AuthResponse.builder()
                .accessToken(tokenResponse.getAccessToken())
                .refreshToken(tokenResponse.getRefreshToken())
                .tokenType(tokenResponse.getTokenType())
                .expiresIn(tokenResponse.getExpiresIn())
                .refreshExpiresIn(tokenResponse.getRefreshExpiresIn())
                .tenantId(getClaimAsString(claims, "tenantId"))
                .orgId(getClaimAsString(claims, "orgId"))
                .domainType(getClaimAsString(claims, "domainType"))
                .roleType(getClaimAsString(claims, "roleType"))
                .subscriptionType(getClaimAsString(claims, "subscriptionType"))
                .username(username);

        // Step 3: Enrich response with user profile from userService MongoDB read store (no password stored there)
        try {
            String userUrl = userServiceBaseUrl + "/api/users/by-username/" + username;
            ResponseEntity<Map> userResponse = restTemplate.getForEntity(userUrl, Map.class);
            if (userResponse.getStatusCode().is2xxSuccessful() && userResponse.getBody() != null) {
                Map<String, Object> userDoc = userResponse.getBody();
                builder.userId(getStr(userDoc, "userId"))
                        .email(getStr(userDoc, "email"))
                        .orgId(getStr(userDoc, "orgId"))
                        .orgName(getStr(userDoc, "orgName"))
                        .divisionId(getStr(userDoc, "divisionId"))
                        .divisionName(getStr(userDoc, "divisionName"));
            }
        } catch (Exception e) {
            // Non-fatal: userService may not have synced yet — proceed without enrichment
            System.err.println("Could not fetch user details from userService: " + e.getMessage());
        }

        return builder.build();
    }

    public AuthResponse refresh(String refreshToken) {
        return refresh(refreshToken, null);
    }

    /**
     * Refresh token using Keycloak's token endpoint.
     * If actorToken is provided, sends it as client_assertion for service identity proof.
     * Keycloak validates the actor token by fetching our JWKS endpoint.
     */
    public AuthResponse refresh(String refreshToken, String actorToken) {
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", clientId);
        form.add("refresh_token", refreshToken);

        if (actorToken != null && !actorToken.isBlank()) {
            // Use actor token as client assertion (JWT Bearer method)
            // Keycloak validates this by fetching our /.well-known/jwks.json
            form.add("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
            form.add("client_assertion", actorToken);
        } else if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        KeycloakTokenResponse tokenResponse;
        try {
            tokenResponse = restTemplate.postForEntity(
                    tokenUrl, new HttpEntity<>(form, headers), KeycloakTokenResponse.class).getBody();
        } catch (HttpClientErrorException e) {
            log.error("Keycloak refresh failed: {} — {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Refresh token expired or invalid");
        }

        if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
            throw new RuntimeException("No token received from Keycloak");
        }

        Map<String, Object> claims = extractClaims(tokenResponse.getAccessToken());
        return AuthResponse.builder()
                .accessToken(tokenResponse.getAccessToken())
                .refreshToken(tokenResponse.getRefreshToken())
                .tokenType(tokenResponse.getTokenType())
                .expiresIn(tokenResponse.getExpiresIn())
                .refreshExpiresIn(tokenResponse.getRefreshExpiresIn())
                .tenantId(getClaimAsString(claims, "tenantId"))
                .domainType(getClaimAsString(claims, "domainType"))
                .roleType(getClaimAsString(claims, "roleType"))
                .subscriptionType(getClaimAsString(claims, "subscriptionType"))
                .build();
    }

    private Map<String, Object> extractClaims(String accessToken) {
        try {
            String[] parts = accessToken.split("\\.");
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Keycloak token claims");
        }
    }

    private String getClaimAsString(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (value == null) return null;
        // Keycloak stores user attributes as List<String> — unwrap if needed
        if (value instanceof java.util.List<?> list && !list.isEmpty()) {
            return list.get(0).toString();
        }
        return value.toString();
    }

    private String getStr(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
