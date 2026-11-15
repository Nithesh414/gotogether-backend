package com.gotogether.backend.controller;

import com.gotogether.backend.dto.DriverOnboardingRequest;
import com.gotogether.backend.dto.DriverProfileResponse;
import com.gotogether.backend.entity.DriverProfile;
import com.gotogether.backend.entity.User;
import com.gotogether.backend.repository.DriverProfileRepository;
import com.gotogether.backend.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverProfileRepository driverRepository;
    private final UserRepository userRepository;

    public DriverController(
            DriverProfileRepository driverRepository,
            UserRepository userRepository) {
        this.driverRepository = driverRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/onboard")
    public DriverProfileResponse onboard(
            Authentication authentication,
            @RequestBody DriverOnboardingRequest request) {

        User user = userRepository
            .findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "User not found"
            ));

        if (driverRepository.findByUser_Id(user.getId()).isPresent()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Driver profile already exists"
            );
        }

        if (request.vehicleNumber() == null ||
            request.vehicleNumber().isBlank() ||
            request.vehicleType() == null ||
            request.vehicleType().isBlank() ||
            request.licenseNumber() == null ||
            request.licenseNumber().isBlank() ||
            request.availableSeats() == null ||
            request.availableSeats() < 1 ||
            request.availableSeats() > 8) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Please provide valid driver details"
            );
        }

        String vehicleNumber =
            request.vehicleNumber().trim().toUpperCase();

        if (driverRepository.existsByVehicleNumberIgnoreCase(vehicleNumber)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Vehicle is already registered"
            );
        }

        DriverProfile driver = new DriverProfile();
        driver.setUser(user);
        driver.setVehicleNumber(vehicleNumber);
        driver.setVehicleType(request.vehicleType().trim());
        driver.setVehicleModel(
            request.vehicleModel() == null
                ? null : request.vehicleModel().trim()
        );
        driver.setLicenseNumber(request.licenseNumber().trim());
        driver.setAvailableSeats(request.availableSeats());

        DriverProfile saved = driverRepository.save(driver);

        return convertToResponse(saved);
    }

    @GetMapping("/me")
    public DriverProfileResponse getMyDriverProfile(
            Authentication authentication) {

        User user = userRepository
            .findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "User not found"
            ));

        DriverProfile driver = driverRepository
            .findByUser_Id(user.getId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Driver profile not found"
            ));

        return convertToResponse(driver);
    }

    private DriverProfileResponse convertToResponse(
            DriverProfile driver) {

        return new DriverProfileResponse(
    driver.getId(),
    driver.getVehicleNumber(),
    driver.getVehicleType(),
    driver.getVehicleModel(),
    driver.getAvailableSeats(),
    driver.getVerificationStatus(),
    driver.getCreatedAt(),
    driver.getPhotoUrl()
);
    }
}