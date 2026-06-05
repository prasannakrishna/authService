package com.bhagwat.auth.authService.dto;

import java.time.LocalDateTime;

public class AppInfo {
    private String appId;
    private String appName;
    private LocalDateTime subscriptionExpiry;
    private String tenantId;
    private String accessToken;
    private String link;

    public AppInfo(String appId, String appName, LocalDateTime subscriptionExpiry, String tenantId, String baseUrl, String accessToken) {
        this.appId = appId;
        this.appName = appName;
        this.subscriptionExpiry = subscriptionExpiry;
        this.tenantId = tenantId;
        this.accessToken = accessToken;
        this.link = generateLink(baseUrl);
    }

    private String generateLink(String baseUrl) {
        return String.format("%s?tenantId=%s&appId=%s&token=%s", baseUrl, tenantId, appId, accessToken);
    }

    // Getters and setters (or use Lombok)
}
