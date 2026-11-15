
package com.gotogether.backend.service;

import com.gotogether.backend.dto.RegisterRequest;
import com.gotogether.backend.dto.LoginRequest;
import com.gotogether.backend.entity.User;
import com.gotogether.backend.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(RegisterRequest request) {

        String email = request.getEmail().trim().toLowerCase();
        String mobile = request.getMobileNumber().trim();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        if (userRepository.existsByMobileNumber(mobile)) {
            throw new IllegalArgumentException("Mobile number already registered");
        }

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setMobileNumber(mobile);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        return userRepository.save(user);
    }

    public User login(LoginRequest request) {

        String identifier = request.getIdentifier().trim();

        User user;

        if (identifier.contains("@")) {
            user = userRepository.findByEmailIgnoreCase(identifier)
                    .orElseThrow(() ->
                        new IllegalArgumentException("Invalid credentials"));
        } else {
            user = userRepository.findByMobileNumber(identifier)
                    .orElseThrow(() ->
                        new IllegalArgumentException("Invalid credentials"));
        }

        if (!passwordEncoder.matches(
                request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        return user;
    }
}
