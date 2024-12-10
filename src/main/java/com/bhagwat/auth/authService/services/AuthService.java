package com.bhagwat.auth.authService.services;

import com.bhagwat.auth.authService.entity.AppSubscriber;
import com.bhagwat.auth.authService.entity.User;
import com.bhagwat.auth.authService.repository.AppSubscriberRepository;
import com.bhagwat.auth.authService.repository.UserRepository;
import com.bhagwat.auth.authService.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppSubscriberRepository appSubscriberRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // Method to validate username/password along with app subscription
    public boolean validateAppSubscription(String username, String password, String appId) {
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            return false;
        }

        AppSubscriber subscriber = appSubscriberRepository.findByAppIdAndUserId(appId, user.getUserId())
                .orElse(null);

        return subscriber != null;
    }

    // Method to validate JWT token along with app subscription
    public boolean validateJwtTokenAndSubscription(String token, String appId) {
        String username = jwtUtil.validateTokenAndRetrieveSubject(token);
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        AppSubscriber subscriber = appSubscriberRepository.findByAppIdAndUserId(appId, user.getUserId())
                .orElse(null);

        return subscriber != null;
    }
}

