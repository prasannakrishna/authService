package com.bhagwat.auth.authService.repository;

import com.bhagwat.auth.authService.entity.AppSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppSubscriptionRepository extends JpaRepository<AppSubscription, Long> {
    Optional<AppSubscription> findByAppIdAndSubscriptionKeyAndUserId(Long appId, String subscriptionKey, Long userId);
}