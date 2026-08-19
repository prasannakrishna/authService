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
import java.util.List;
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

    @Value("${keycloak.admin.username:admin}")
    private String adminUsername;

    @Value("${keycloak.admin.password:admin123}")
    private String adminPassword;

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

        // Step 2: Pre-fetch user attributes (used for provisioning + response enrichment)
        Map<String, String> kcAttrs = null;
        Map<String, Object> userDoc = null;
        try {
            ResponseEntity<Map> attrsResp = restTemplate.getForEntity(
                    userServiceBaseUrl + "/api/users/by-username/" + username + "/keycloak-attrs", Map.class);
            if (attrsResp.getStatusCode().is2xxSuccessful() && attrsResp.getBody() != null) {
                kcAttrs = (Map<String, String>) attrsResp.getBody();
            }
        } catch (Exception e) {
            log.warn("Could not pre-fetch keycloak-attrs for '{}' (non-fatal): {}", username, e.getMessage());
        }
        try {
            ResponseEntity<Map> userResp = restTemplate.getForEntity(
                    userServiceBaseUrl + "/api/users/by-username/" + username, Map.class);
            if (userResp.getStatusCode().is2xxSuccessful() && userResp.getBody() != null) {
                userDoc = userResp.getBody();
            }
        } catch (Exception e) {
            log.warn("Could not pre-fetch user profile for '{}' (non-fatal): {}", username, e.getMessage());
        }
        final Map<String, String> cachedKcAttrs = kcAttrs;
        final Map<String, Object> cachedUserDoc = userDoc;

        // Step 3: Credentials verified — authenticate with Keycloak to obtain JWT
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
            if (e.getStatusCode().value() == 401) {
                // User validated by userService but missing/stale in Keycloak — provision with full attributes and retry
                log.warn("User '{}' not in Keycloak (401), auto-provisioning with validated credentials", username);
                provisionOrUpdateKeycloakUser(username, password, cachedKcAttrs);
                try {
                    tokenResponse = restTemplate.postForEntity(tokenUrl, request, KeycloakTokenResponse.class).getBody();
                } catch (HttpClientErrorException retryEx) {
                    log.error("Keycloak retry failed for '{}': {}", username, retryEx.getResponseBodyAsString());
                    throw new RuntimeException("Authentication failed: invalid username or password");
                }
            } else {
                log.error("Keycloak authentication failed for user '{}': status={}, body={}",
                        username, e.getStatusCode(), e.getResponseBodyAsString());
                throw new RuntimeException("Authentication failed: invalid username or password");
            }
        }

        if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
            throw new RuntimeException("No token received from Keycloak");
        }

        Map<String, Object> claims = extractClaims(tokenResponse.getAccessToken());

        // Resolve attributes: JWT claims take priority, fall back to pre-fetched attrs
        String resolvedTenantId = firstNonNull(getClaimAsString(claims, "tenantId"), attr(cachedKcAttrs, "tenantId"));
        String resolvedOrgId = firstNonNull(getClaimAsString(claims, "orgId"), attr(cachedKcAttrs, "orgId"));
        String resolvedDomainType = firstNonNull(getClaimAsString(claims, "domainType"), attr(cachedKcAttrs, "domainType"));
        String resolvedRoleType = firstNonNull(getClaimAsString(claims, "roleType"), attr(cachedKcAttrs, "roleType"));
        String resolvedSubType = firstNonNull(getClaimAsString(claims, "subscriptionType"), attr(cachedKcAttrs, "subscriptionType"));

        AuthResponse.AuthResponseBuilder builder = AuthResponse.builder()
                .accessToken(tokenResponse.getAccessToken())
                .refreshToken(tokenResponse.getRefreshToken())
                .tokenType(tokenResponse.getTokenType())
                .expiresIn(tokenResponse.getExpiresIn())
                .refreshExpiresIn(tokenResponse.getRefreshExpiresIn())
                .tenantId(resolvedTenantId)
                .orgId(resolvedOrgId)
                .domainType(resolvedDomainType)
                .roleType(resolvedRoleType)
                .subscriptionType(resolvedSubType)
                .username(username);

        // Step 4: Enrich from userService MongoDB read store (orgId from MongoDB overrides JWT if present)
        if (cachedUserDoc != null) {
            builder.userId(getStr(cachedUserDoc, "userId"))
                    .email(getStr(cachedUserDoc, "email"))
                    .orgId(firstNonNull(getStr(cachedUserDoc, "orgId"), resolvedOrgId))
                    .orgName(getStr(cachedUserDoc, "orgName"))
                    .divisionId(getStr(cachedUserDoc, "divisionId"))
                    .divisionName(getStr(cachedUserDoc, "divisionName"));
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

    private void provisionOrUpdateKeycloakUser(String username, String password, Map<String, String> userAttrs) {
        try {
            // Get admin token from master realm
            String adminTokenUrl = serverUrl + "/realms/master/protocol/openid-connect/token";
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> adminForm = new LinkedMultiValueMap<>();
            adminForm.add("grant_type", "password");
            adminForm.add("client_id", "admin-cli");
            adminForm.add("username", adminUsername);
            adminForm.add("password", adminPassword);
            Map<?, ?> adminToken = restTemplate.postForEntity(adminTokenUrl, new HttpEntity<>(adminForm, h), Map.class).getBody();
            String token = (String) adminToken.get("access_token");

            HttpHeaders authH = new HttpHeaders();
            authH.setContentType(MediaType.APPLICATION_JSON);
            authH.setBearerAuth(token);

            String usersUrl = serverUrl + "/admin/realms/" + realm + "/users";
            Map<String, Object> cred = Map.of("type", "password", "value", password, "temporary", false);

            // Build attributes for Keycloak user (tenantId, orgId, domainType, roleType, subscriptionType)
            java.util.Map<String, java.util.List<String>> kcAttributes = new java.util.LinkedHashMap<>();
            if (userAttrs != null) {
                String[] attrKeys = {"tenantId", "orgId", "domainType", "roleType", "subscriptionType"};
                for (String key : attrKeys) {
                    String val = userAttrs.get(key);
                    if (val != null && !val.isBlank()) {
                        kcAttributes.put(key, List.of(val));
                    }
                }
            }

            String firstName = (userAttrs != null && userAttrs.get("name") != null) ? userAttrs.get("name") : username;
            String email = (userAttrs != null) ? userAttrs.get("email") : null;

            // Check if user already exists
            String searchUrl = usersUrl + "?username=" + username + "&exact=true";
            ResponseEntity<List> existing = restTemplate.exchange(searchUrl, HttpMethod.GET, new HttpEntity<>(authH), List.class);
            if (existing.getBody() != null && !existing.getBody().isEmpty()) {
                Map<?, ?> existingUser = (Map<?, ?>) existing.getBody().get(0);
                String userId = (String) existingUser.get("id");
                // Update: set firstName, email, emailVerified, clear requiredActions, set attributes
                java.util.Map<String, Object> userUpdate = new java.util.LinkedHashMap<>();
                userUpdate.put("firstName", firstName);
                if (email != null) { userUpdate.put("email", email); userUpdate.put("emailVerified", true); }
                userUpdate.put("requiredActions", List.of());
                if (!kcAttributes.isEmpty()) userUpdate.put("attributes", kcAttributes);
                restTemplate.put(usersUrl + "/" + userId, new HttpEntity<>(userUpdate, authH));
                restTemplate.put(usersUrl + "/" + userId + "/reset-password", new HttpEntity<>(cred, authH));
                log.info("Keycloak user updated with password + attributes for '{}'", username);
            } else {
                // Create new user with all attributes
                java.util.Map<String, Object> userRep = new java.util.LinkedHashMap<>();
                userRep.put("username", username);
                userRep.put("enabled", true);
                userRep.put("emailVerified", true);
                userRep.put("firstName", firstName);
                if (email != null) userRep.put("email", email);
                userRep.put("requiredActions", List.of());
                userRep.put("credentials", List.of(cred));
                if (!kcAttributes.isEmpty()) userRep.put("attributes", kcAttributes);
                restTemplate.postForEntity(usersUrl, new HttpEntity<>(userRep, authH), Void.class);
                log.info("Keycloak user provisioned with attributes: '{}'", username);
            }
        } catch (Exception e) {
            log.error("Failed to provision Keycloak user '{}': {}", username, e.getMessage());
            throw new RuntimeException("Authentication failed: could not provision user");
        }
    }

    private String firstNonNull(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private String attr(Map<String, String> map, String key) {
        return map != null ? map.get(key) : null;
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
