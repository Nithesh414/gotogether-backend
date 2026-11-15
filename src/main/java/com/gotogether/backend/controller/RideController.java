
package com.gotogether.backend.controller;

import com.gotogether.backend.dto.CreateRideRequest;
import com.gotogether.backend.dto.RideResponse;
import com.gotogether.backend.dto.BookingResponse;
import com.gotogether.backend.dto.VerifyBoardingOtpRequest;
import com.gotogether.backend.dto.DriverContactResponse;

import com.gotogether.backend.service.RideService;
import com.gotogether.backend.service.BookingService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rides")
public class RideController {

    private final RideService rideService;
    private final BookingService bookingService;

    public RideController(
            RideService rideService,
            BookingService bookingService) {

        this.rideService = rideService;
        this.bookingService = bookingService;
    }

    // 1. CREATE RIDE

    @PostMapping
    public ResponseEntity<?> createRide(
            @RequestBody CreateRideRequest request,
            Authentication authentication) {

        try {
            RideResponse response = rideService.createRide(
                    authentication.getName(), request);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", ex.getMessage()));
        }
    }

    // 2. GET MY RIDES

    @GetMapping("/my")
    public ResponseEntity<?> getMyRides(
            Authentication authentication) {

        try {
            return ResponseEntity.ok(
                    rideService.getMyRides(authentication.getName()));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", ex.getMessage()));
        }
    }

    // 3. FILTER MY RIDES

    @GetMapping("/my/filter")
    public List<RideResponse> getMyRidesByStatus(
            @RequestParam(required = false) String status,
            Authentication authentication) {

        return rideService.getMyRidesByStatus(
                authentication.getName(), status);
    }

    // 4. SEARCH RIDES

    @GetMapping("/search")
    public ResponseEntity<List<RideResponse>> searchRides(
            @RequestParam String from,
            @RequestParam String to) {

        return ResponseEntity.ok(
                rideService.searchRides(from, to));
    }

    // 5. GET PASSENGERS FOR A RIDE

    @GetMapping("/{rideId}/passengers")
    public List<BookingResponse> getRidePassengers(
            @PathVariable Long rideId,
            Authentication authentication) {

        return bookingService.getPassengersForDriver(
                rideId, authentication.getName());
    }

    // 6. START BOARDING AND SEND PASSENGER OTPs

    @PatchMapping("/{rideId}/start")
    public ResponseEntity<?> startBoarding(
            @PathVariable Long rideId,
            Authentication authentication) {

        try {
            return ResponseEntity.ok(
                    bookingService.startBoarding(
                            rideId, authentication.getName()));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", ex.getMessage()));
        }
    }

    // 7. RESEND PASSENGER OTP

    @PostMapping("/{rideId}/bookings/{bookingId}/resend-boarding-otp")
    public ResponseEntity<?> resendBoardingOtp(
            @PathVariable Long rideId,
            @PathVariable Long bookingId,
            Authentication authentication) {

        try {
            return ResponseEntity.ok(
                    bookingService.resendBoardingOtp(
                            rideId,
                            bookingId,
                            authentication.getName()));

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", ex.getMessage()));
        }
    }

    // 8. VERIFY INDIVIDUAL PASSENGER OTP

    @PostMapping("/{rideId}/bookings/{bookingId}/verify-boarding-otp")
    public Map<String, Object> verifyBoardingOtp(
            @PathVariable Long rideId,
            @PathVariable Long bookingId,
            @RequestBody VerifyBoardingOtpRequest request,
            Authentication authentication) {

        return bookingService.verifyBoardingOtp(
                rideId,
                bookingId,
                authentication.getName(),
                request.getOtp());
    }

    // 9. CONFIRM ACTUAL RIDE START

    @PatchMapping("/{rideId}/confirm-start")
    public ResponseEntity<?> confirmStartTrip(
            @PathVariable Long rideId,
            Authentication authentication) {

        try {
            RideResponse response = rideService.confirmStartTrip(
                    authentication.getName(), rideId);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", ex.getMessage()));
        }
    }

    // 10. GET DRIVER CONTACT

    @GetMapping("/{rideId}/driver-contact")
    public DriverContactResponse getDriverContact(
            @PathVariable Long rideId,
            Authentication authentication) {

        return bookingService.getDriverContact(
                rideId, authentication.getName());
    }

    // 11. COMPLETE TRIP

    @PatchMapping("/{rideId}/complete")
    public ResponseEntity<?> completeTrip(
            @PathVariable Long rideId,
            Authentication authentication) {

        try {
            RideResponse response = rideService.completeTrip(
                    authentication.getName(), rideId);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", ex.getMessage()));
        }
    }
}
