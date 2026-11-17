
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
            RideTrackingService trackingService) {
        this.trackingService = trackingService;
    }

    // Driver starts GPS sharing before boarding.
    @PostMapping("/{rideId}/location-sharing/start")
    public ResponseEntity<Void> startSharing(
            @PathVariable Long rideId,
            Authentication authentication) {

        trackingService.startSharing(
                rideId, authentication.getName());

        return ResponseEntity.ok().build();
    }

    // Driver stops GPS sharing.
    @PostMapping("/{rideId}/location-sharing/stop")
    public ResponseEntity<Void> stopSharing(
            @PathVariable Long rideId,
            Authentication authentication) {

        trackingService.stopSharing(
                rideId, authentication.getName());

        return ResponseEntity.ok().build();
    }

    // Driver uploads GPS.
    @PutMapping("/{rideId}/location")
    public ResponseEntity<RideLocationResponse> updateLocation(
            @PathVariable Long rideId,
            @RequestBody UpdateRideLocationRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                trackingService.updateLocation(
                        rideId,
                        authentication.getName(),
                        request
                )
        );
    }

    // Confirmed or boarded passenger retrieves GPS.
    @GetMapping("/{rideId}/location")
    public ResponseEntity<RideLocationResponse> getLocation(
            @PathVariable Long rideId,
            Authentication authentication) {

        return ResponseEntity.ok(
                trackingService.getPassengerLocation(
                        rideId,
                        authentication.getName()
                )
        );
    }
}
