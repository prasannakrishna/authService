package com.bhagwat.auth.authService.repository;

import com.bhagwat.auth.authService.entity.AppSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppSubscriberRepository extends JpaRepository<AppSubscriber, Long> {
    Optional<AppSubscriber> findByAppIdAndUserId(String appId, Long userId);

    List<AppSubscriber> findByUserId(Long userId);

}
