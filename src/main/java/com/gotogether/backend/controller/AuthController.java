
package com.gotogether.backend.controller;

import com.gotogether.backend.dto.RegisterRequest;
import com.gotogether.backend.dto.LoginRequest;
import com.gotogether.backend.dto.VerifyEmailOtpRequest;
import com.gotogether.backend.entity.User;
import com.gotogether.backend.service.AuthService;
import com.gotogether.backend.service.EmailOtpService;
import com.gotogether.backend.service.JwtService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final EmailOtpService emailOtpService;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            EmailOtpService emailOtpService) {

        this.authService = authService;
        this.jwtService = jwtService;
        this.emailOtpService = emailOtpService;
    }

    @PostMapping("/otp/request")
    public ResponseEntity<?> requestOtp(
            @RequestBody RegisterRequest request) {

        try {
            emailOtpService.requestOtp(request);

            return ResponseEntity.ok(Map.of(
                "message",
                "OTP sent successfully. Please check your email.",
                "expiresInSeconds", 300,
                "resendAfterSeconds", 60
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<?> verifyOtp(
            @RequestBody VerifyEmailOtpRequest request) {

        try {
            if (request.getEmail() == null ||
                request.getOtp() == null) {

                return ResponseEntity.badRequest().body(
                    Map.of("message", "Email and OTP are required"));
            }

            User user = emailOtpService.verifyOtp(
                request.getEmail(),
                request.getOtp()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of(
                    "message", "Email verified. Registration successful.",
                    "userId", user.getId(),
                    "fullName", user.getFullName(),
                    "email", user.getEmail(),
                    "mobileNumber", user.getMobileNumber(),
                    "role", user.getRole()
                )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody LoginRequest request) {

        try {
            if (request.getIdentifier() == null ||
                request.getPassword() == null ||
                request.getIdentifier().isBlank() ||
                request.getPassword().isBlank()) {

                return ResponseEntity.badRequest().body(
                    Map.of("message",
                        "Email/mobile and password are required"));
            }

            User user = authService.login(request);

            String token = jwtService.generateToken(
                user.getEmail(), user.getRole());

            return ResponseEntity.ok(Map.of(
                "message", "Login successful",
                "userId", user.getId(),
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "role", user.getRole(),
                "token", token
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                Map.of("message", e.getMessage()));
        }
    }
}
