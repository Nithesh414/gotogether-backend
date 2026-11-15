package com.gotogether.backend.controller;

import com.gotogether.backend.dto.AdminLoginRequest;
import com.gotogether.backend.entity.Admin;
import com.gotogether.backend.repository.AdminRepository;
import com.gotogether.backend.service.JwtService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AdminAuthController(
            AdminRepository adminRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AdminLoginRequest request) {

        if (request.email() == null || request.email().isBlank()
                || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email and password are required"));
        }

        Admin admin = adminRepository
                .findByEmailIgnoreCase(request.email().trim())
                .orElse(null);

        if (admin == null ||
                !passwordEncoder.matches(request.password(), admin.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid admin credentials"));
        }

        String token = jwtService.generateToken(admin.getEmail(), "ADMIN");

        return ResponseEntity.ok(Map.of(
                "message", "Admin login successful",
                "adminId", admin.getId(),
                "fullName", admin.getFullName(),
                "email", admin.getEmail(),
                "role", "ADMIN",
                "token", token
        ));
    }
}