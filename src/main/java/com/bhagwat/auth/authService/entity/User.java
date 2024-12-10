package com.bhagwat.auth.authService.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_username", columnNames = "user_name"),
        @UniqueConstraint(name = "uk_email", columnNames = "email")
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_name", nullable = false, length = 100)
    private String userName;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "phone_number", length = 15)
    private String phoneNumber;

    @Column(name = "address_id")
    private Long addressId;

    @Column(name = "user_community_association", length = 255)
    private String userCommunityAssociation;

    @Column(name = "id")
    private Long id;

    @Column(name = "password", nullable = false, length = 64)
    private String password;

    @Column(name = "community_id")
    private Long communityId;

    @Column(name = "is_admin_flag", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isAdminFlag;

    @Column(name = "role", nullable = false, length = 64)
    private String role;

    @Column(name = "is_org_user", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isOrgUser;

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "is_app_user", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isAppUser;

    // Getters and Setters
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Long getAddressId() {
        return addressId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }

    public String getUserCommunityAssociation() {
        return userCommunityAssociation;
    }

    public void setUserCommunityAssociation(String userCommunityAssociation) {
        this.userCommunityAssociation = userCommunityAssociation;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getCommunityId() {
        return communityId;
    }

    public void setCommunityId(Long communityId) {
        this.communityId = communityId;
    }

    public Boolean getIsAdminFlag() {
        return isAdminFlag;
    }

    public void setIsAdminFlag(Boolean isAdminFlag) {
        this.isAdminFlag = isAdminFlag;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Boolean getIsOrgUser() {
        return isOrgUser;
    }

    public void setIsOrgUser(Boolean isOrgUser) {
        this.isOrgUser = isOrgUser;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public Boolean getIsAppUser() {
        return isAppUser;
    }

    public void setIsAppUser(Boolean isAppUser) {
        this.isAppUser = isAppUser;
    }
}
