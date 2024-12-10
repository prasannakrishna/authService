package com.bhagwat.auth.authService.controller;

import com.bhagwat.auth.authService.entity.AppSubscriber;
import com.bhagwat.auth.authService.services.AppSubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/app-subscribers")
public class AppSubscriberController {

    @Autowired
    private AppSubscriptionService appSubscriptionService;

    @PostMapping
    public ResponseEntity<AppSubscriber> addAppSubscriber(@RequestBody AppSubscriber appSubscriber) {
        AppSubscriber createdSubscriber = appSubscriptionService.addAppSubscriber(appSubscriber);
        return new ResponseEntity<>(createdSubscriber, HttpStatus.CREATED);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AppSubscriber>> getSubscribersByUserId(@PathVariable Long userId) {
        List<AppSubscriber> subscribers = appSubscriptionService.getSubscribersByUserId(userId);
        if (subscribers.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(subscribers, HttpStatus.OK);
    }
}

