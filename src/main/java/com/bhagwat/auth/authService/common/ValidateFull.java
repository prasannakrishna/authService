package com.bhagwat.auth.authService.common;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidateFull {
    String username() default ""; // Optional: To check using username and password
    String password() default ""; // Optional: To check using username and password
    String token() default "";    // Optional: JWT Token
    String appId();               // Mandatory: Application ID
}

