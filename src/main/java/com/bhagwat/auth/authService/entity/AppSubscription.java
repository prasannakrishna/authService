package com.bhagwat.auth.authService.entity;
import jakarta.persistence.*;

import java.sql.Timestamp;

@Entity
@Table(name = "app_subscription")
public class AppSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // For auto-incrementing primary key
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_group_id")
    private Long userGroupId;

    @Column(name = "subscription_key")
    private String subscriptionKey;

    @Column(name = "sync_user_job_trigger", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean syncUserJobTrigger = false;

    @Enumerated(EnumType.STRING) // Specifies that the field should be stored as a string in the DB
    @Column(name = "sync_frequency", columnDefinition = "ENUM('monthly', 'quarterly', 'annually')")
    private SyncFrequency syncFrequency;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private Timestamp updatedAt;

    // Enum for sync frequency
    public enum SyncFrequency {
        monthly, quarterly, annually
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getUserGroupId() {
        return userGroupId;
    }

    public void setUserGroupId(Long userGroupId) {
        this.userGroupId = userGroupId;
    }

    public String getSubscriptionKey() {
        return subscriptionKey;
    }

    public void setSubscriptionKey(String subscriptionKey) {
        this.subscriptionKey = subscriptionKey;
    }

    public Boolean getSyncUserJobTrigger() {
        return syncUserJobTrigger;
    }

    public void setSyncUserJobTrigger(Boolean syncUserJobTrigger) {
        this.syncUserJobTrigger = syncUserJobTrigger;
    }

    public SyncFrequency getSyncFrequency() {
        return syncFrequency;
    }

    public void setSyncFrequency(SyncFrequency syncFrequency) {
        this.syncFrequency = syncFrequency;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}