package com.bhagwat.auth.authService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private int expiresIn;
    private int refreshExpiresIn;
    private String tenantId;
    private String domainType;
    private String roleType;
    private String subscriptionType;
    // Enriched from userService
    private String userId;
    private String username;
    private String email;
    private String orgId;
    private String orgName;
    private String divisionId;
    private String divisionName;
    // DPoP binding
    private String dpopThumbprint;
}
