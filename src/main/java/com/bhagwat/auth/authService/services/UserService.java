package com.bhagwat.auth.authService.services;

import com.bhagwat.auth.authService.entity.User;
import com.bhagwat.auth.authService.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    public User createUser(User user) {
        // Here you might want to encrypt the password before saving
        System.out.println("before encrypt"+user.getPassword());
         user.setPassword(passwordEncoder.encode(user.getPassword()));
        System.out.println("after encrypt"+user.getPassword());
        //clearEntityManager();
        System.out.println("user going to be saved is "+user);
        return userRepository.save(user);
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUserName(username);
    }


    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void clearEntityManager() {
        entityManager.flush();
        entityManager.clear();  // Detaches all entities from the current persistence context

    }
}