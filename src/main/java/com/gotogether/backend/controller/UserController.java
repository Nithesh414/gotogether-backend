package com.gotogether.backend.controller;

import com.gotogether.backend.dto.UserProfileResponse;
import com.gotogether.backend.dto.UpdateProfileRequest;
import com.gotogether.backend.entity.User;
import com.gotogether.backend.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public UserProfileResponse getMyProfile(
            Authentication authentication) {

        User user = getLoggedInUser(authentication);

        return convertToResponse(user);
    }

    @PutMapping("/me")
    public UserProfileResponse updateMyProfile(
            Authentication authentication,
            @RequestBody UpdateProfileRequest request) {

        User user = getLoggedInUser(authentication);

        if (request.fullName() == null ||
            request.fullName().isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Name is required"
            );
        }

        if (request.mobileNumber() == null ||
            !request.mobileNumber().matches("^\\+?[0-9]{10,15}$")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Invalid mobile number"
            );
        }

        if (request.email() == null ||
            !request.email().matches(
                "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Invalid email"
            );
        }

        String newEmail = request.email().trim().toLowerCase();
        String newMobile = request.mobileNumber().trim();

        userRepository.findByEmailIgnoreCase(newEmail)
            .filter(existing -> !existing.getId().equals(user.getId()))
            .ifPresent(existing -> {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Email already exists"
                );
            });

        userRepository.findByMobileNumber(newMobile)
            .filter(existing -> !existing.getId().equals(user.getId()))
            .ifPresent(existing -> {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Mobile number already exists"
                );
            });

        user.setFullName(request.fullName().trim());
        user.setMobileNumber(newMobile);
        user.setEmail(newEmail);

        User updatedUser = userRepository.save(user);

        return convertToResponse(updatedUser);
    }

    private User getLoggedInUser(Authentication authentication) {

        return userRepository.findByEmailIgnoreCase(
            authentication.getName()
        ).orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "User not found"
        ));
    }

    private UserProfileResponse convertToResponse(User user) {

        return new UserProfileResponse(
            user.getId(),
            user.getFullName(),
            user.getMobileNumber(),
            user.getEmail(),
            user.getRole(),
            user.getCreatedAt()
        );
    }
}