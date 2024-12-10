package com.bhagwat.auth.authService.aspect;


import com.bhagwat.auth.authService.common.ValidateFull;
import com.bhagwat.auth.authService.services.AuthService;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuthAspect {

    private final AuthService authService;

    public AuthAspect(AuthService authService) {
        this.authService = authService;
    }

    @Before("@annotation(validateFull)")
    public void validateFullAccess(ValidateFull validateFull) throws Throwable {

        if (validateFull.appId().isEmpty()) {
            throw new IllegalArgumentException("Application ID is mandatory for validation");
        }

        if (!validateFull.username().isEmpty() && !validateFull.password().isEmpty()) {
            // Validate using Username + Password + AppId
            boolean isAuthorized = authService.validateAppSubscription(
                    validateFull.username(), validateFull.password(), validateFull.appId());
            if (!isAuthorized) {
                throw new RuntimeException("Invalid Username/Password or App Subscription");
            }
        } else if (!validateFull.token().isEmpty()) {
            // Validate using JWT Token + AppId
            boolean isAuthorized = authService.validateJwtTokenAndSubscription(validateFull.token(), validateFull.appId());
            if (!isAuthorized) {
                throw new RuntimeException("Invalid JWT Token or App Subscription");
            }
        } else {
            throw new RuntimeException("Either Username/Password or JWT Token must be provided");
        }
    }
}

