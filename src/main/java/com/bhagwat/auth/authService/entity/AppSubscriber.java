package com.bhagwat.auth.authService.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "app_subscribers")
public class AppSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_id")
    private String appId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "is_admin")
    private Boolean isAdmin;

    @Column(name = "user_group")
    private Long userGroupId;

    // Getters and Setters
}

