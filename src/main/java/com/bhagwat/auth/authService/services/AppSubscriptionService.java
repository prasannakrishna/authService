package com.bhagwat.auth.authService.services;

import com.bhagwat.auth.authService.entity.AppSubscriber;
import com.bhagwat.auth.authService.entity.AppSubscription;
import com.bhagwat.auth.authService.repository.AppSubscriberRepository;
import com.bhagwat.auth.authService.repository.AppSubscriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AppSubscriptionService {

    @Autowired
    private AppSubscriptionRepository appSubscriptionRepository;

    @Autowired
    private AppSubscriberRepository appSubscriberRepository;

    public AppSubscriber addAppSubscriber(AppSubscriber appSubscriber) {
        return appSubscriberRepository.save(appSubscriber);
    }

    public List<AppSubscriber> getSubscribersByUserId(Long userId) {
        return appSubscriberRepository.findByUserId(userId);
    }
    public boolean isUserAuthorized(Long appId, String appKey, Long userId) {
        Optional<AppSubscription> subscription = appSubscriptionRepository
                .findByAppIdAndSubscriptionKeyAndUserId(appId, appKey, userId);

        return subscription.isPresent(); // Returns true if the user has an active subscription
    }
}
