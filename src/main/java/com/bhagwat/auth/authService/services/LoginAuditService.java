package com.bhagwat.auth.authService.services;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Logs login/logout/failed-login events to userService audit endpoint.
 */
@Service
@Slf4j
public class LoginAuditService {

    private final RestTemplate restTemplate;
    private final HttpServletRequest request;

    @Value("${userservice.base-url:http://localhost:8087}")
    private String userServiceBaseUrl;

    public LoginAuditService(RestTemplate restTemplate, HttpServletRequest request) {
        this.restTemplate = restTemplate;
        this.request = request;
    }

    @Async
    public void logLogin(String userId, String username, String orgId) {
        send("LOGIN", userId, username, orgId, "SUCCESS", null);
    }

    @Async
    public void logLoginFailed(String username, String reason) {
        send("LOGIN_FAILED", null, username, null, "FAILED", reason);
    }

    @Async
    public void logLogout(String userId, String username) {
        send("LOGOUT", userId, username, null, "SUCCESS", null);
    }

    private void send(String eventType, String userId, String username, String orgId, String status, String details) {
        try {
            String ip = request.getRemoteAddr();
            Map<String, Object> event = Map.of(
                    "eventType", eventType,
                    "userId", userId != null ? userId : "",
                    "username", username != null ? username : "",
                    "orgId", orgId != null ? orgId : "",
                    "resource", "/auth/login",
                    "action", "POST",
                    "ipAddress", ip != null ? ip : "",
                    "status", status,
                    "details", details != null ? details : ""
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(userServiceBaseUrl + "/api/audit", new HttpEntity<>(event, headers), Void.class);
        } catch (Exception e) {
            log.debug("Audit send failed (non-fatal): {}", e.getMessage());
        }
    }
}
