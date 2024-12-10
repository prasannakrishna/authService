package com.bhagwat.auth.authService.services;

import com.bhagwat.auth.authService.entity.User;
import com.bhagwat.auth.authService.repository.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    //private static final Logger logger = (Logger) LogManager.getLogger(CustomUserDetailsService.class);
    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<User> user = userRepository.findByUserName(username);

        if (user.isEmpty()) {
            throw new UsernameNotFoundException("User not found");
        }
        //logger.info("User '{}' successfully authenticated", username);
        User appUser = user.get();
        System.out.println(appUser.getPassword()+" password here");

        // Create and return a UserDetails object
        return org.springframework.security.core.userdetails.User
                .withUsername(appUser.getUserName())
                .password(appUser.getPassword())   // Password should be encoded
                .roles(appUser.getRole())          // Use roles from the database
                .build();
    }
}
