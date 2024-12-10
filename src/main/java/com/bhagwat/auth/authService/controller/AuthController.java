package com.bhagwat.auth.authService.controller;


import com.bhagwat.auth.authService.common.ValidateFull;
import com.bhagwat.auth.authService.dto.AuthRequest;
import com.bhagwat.auth.authService.services.AppSubscriptionService;
import com.bhagwat.auth.authService.services.CustomUserDetailsService;
import com.bhagwat.auth.authService.services.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Authenticator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {


    @Autowired
    private JwtService jwtService;
    @Autowired
    private AuthenticationManager authenticationmanager;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private AppSubscriptionService appSubscriptionService;

    @PostMapping("/login")
    public String login(@RequestBody AuthRequest authRequest, @RequestHeader Map<String, String> headers ) {
        Authentication authentication =
                authenticationmanager.authenticate(new UsernamePasswordAuthenticationToken(authRequest.getUsername(), authRequest.getPassword()));
        System.out.print("authentication done"+headers);
        if (authentication.isAuthenticated()) {
            return jwtService.generateToken(authRequest.getUsername());

        } else {
            throw new UsernameNotFoundException("invalid user request !");
        }
    }

    @GetMapping("/validate")
    public boolean validateTokenAndAuthorize(@RequestParam String username, @RequestHeader Map<String, String> header) {
        if (jwtService.validateToken("token", userDetailsService.loadUserByUsername(username))){
            long appId =0L;
            String appKey  = "";
            long userId = 0L;
            if(!header.get("appid").isEmpty() && header.get("appid") !=null)
                appId = Long.parseLong(header.get("appid"));
            if(!header.get("appkey").isEmpty() && header.get("appkey") !=null)
                appKey = header.get("appkey");
            if(!header.get("userid").isEmpty() && header.get("userid") !=null)
                userId = Long.parseLong(header.get("userid"));

            // Perform the authorization check
            //response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403 Forbidden
            //response.getWriter().write("User is not authorized for this module.");
            return appSubscriptionService.isUserAuthorized(appId, appKey, userId); // Stop processing the request if not authorized
        }
        return false;
    }


    @GetMapping("/auth/validate")
    @ValidateFull(username = "#{username}", password = "#{password}", token = "#{token}", appId = "#{appId}")
    public String validateAccess(
            @RequestHeader(required = false) String username,
            @RequestHeader(required = false) String password,
            @RequestHeader(required = false) String token,
            @RequestParam String appId) {
        return "User has valid access";
    }
}
