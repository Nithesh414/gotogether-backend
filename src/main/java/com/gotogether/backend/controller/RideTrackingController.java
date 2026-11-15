package com.gotogether.backend.controller;

import com.gotogether.backend.dto.RideLocationResponse;
import com.gotogether.backend.dto.UpdateRideLocationRequest;
import com.gotogether.backend.service.RideTrackingService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rides")
public class RideTrackingController {

    private final RideTrackingService trackingService;

    public RideTrackingController(
        RideTrackingService trackingService
    ) {
        this.trackingService = trackingService;
    }

    // Driver updates GPS location.
    @PutMapping("/{rideId}/location")
    public ResponseEntity<RideLocationResponse> updateLocation(
        @PathVariable Long rideId,
        @RequestBody UpdateRideLocationRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            trackingService.updateLocation(
                rideId,
                authentication.getName(),
                request
            )
        );
    }

    // Passenger retrieves the driver's latest location.
    @GetMapping("/{rideId}/location")
    public ResponseEntity<RideLocationResponse> getLocation(
        @PathVariable Long rideId,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            trackingService.getPassengerLocation(
                rideId,
                authentication.getName()
            )
        );
    }
}