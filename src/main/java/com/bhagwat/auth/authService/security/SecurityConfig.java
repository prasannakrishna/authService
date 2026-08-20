package com.bhagwat.auth.authService.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] SWAGGER_WHITELIST = {
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/swagger-resources/**"
    };

    private static final String[] PUBLIC_WHITELIST = {
            "/auth/login",
            "/auth/refresh",
            "/auth/public-key",
            "/.well-known/jwks.json",
            "/.well-known/openid-configuration",
            "/actuator/**",
            "/actuator/prometheus"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> {
                    registry.requestMatchers(SWAGGER_WHITELIST).permitAll();
                    registry.requestMatchers(PUBLIC_WHITELIST).permitAll();
                    registry.anyRequest().authenticated();
                })
                // Validate Keycloak-issued JWTs — but skip for public paths
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {})
                        .bearerTokenResolver(request -> {
                            // Don't attempt JWT validation on public endpoints
                            String path = request.getServletPath();
                            for (String pub : PUBLIC_WHITELIST) {
                                if (path.equals(pub) || path.matches(pub.replace("**", ".*"))) {
                                    return null; // skip token extraction → no auth attempted
                                }
                            }
                            String header = request.getHeader("Authorization");
                            if (header != null && header.startsWith("Bearer ")) {
                                return header.substring(7);
                            }
                            return null;
                        }))
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
