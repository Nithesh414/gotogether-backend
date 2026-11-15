package com.gotogether.backend.service;

import com.gotogether.backend.dto.RideLocationResponse;
import com.gotogether.backend.dto.UpdateRideLocationRequest;
import com.gotogether.backend.entity.Ride;
import com.gotogether.backend.entity.RideLocation;
import com.gotogether.backend.repository.BookingRepository;
import com.gotogether.backend.repository.RideLocationRepository;
import com.gotogether.backend.repository.RideRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class RideTrackingService {

    private final RideRepository rideRepository;
    private final RideLocationRepository locationRepository;
    private final BookingRepository bookingRepository;

    public RideTrackingService(
        RideRepository rideRepository,
        RideLocationRepository locationRepository,
        BookingRepository bookingRepository
    ) {
        this.rideRepository = rideRepository;
        this.locationRepository = locationRepository;
        this.bookingRepository = bookingRepository;
    }

    // Only the ride's own driver can share GPS during an active trip.
    @Transactional
    public RideLocationResponse updateLocation(
        Long rideId,
        String driverEmail,
        UpdateRideLocationRequest request
    ) {
        if (request == null ||
            request.latitude() == null ||
            request.longitude() == null ||
            !Double.isFinite(request.latitude()) ||
            !Double.isFinite(request.longitude()) ||
            request.latitude() < -90 ||
            request.latitude() > 90 ||
            request.longitude() < -180 ||
            request.longitude() > 180) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid GPS coordinates"
            );
        }

        if (request.accuracyMeters() != null &&
            (!Double.isFinite(request.accuracyMeters()) ||
             request.accuracyMeters() < 0)) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid GPS accuracy"
            );
        }

        if (request.speedMetersPerSecond() != null &&
            (!Double.isFinite(request.speedMetersPerSecond()) ||
             request.speedMetersPerSecond() < 0)) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid GPS speed"
            );
        }

        if (request.headingDegrees() != null &&
            (!Double.isFinite(request.headingDegrees()) ||
             request.headingDegrees() < 0 ||
             request.headingDegrees() >= 360)) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid GPS heading"
            );
        }

        Ride ride = rideRepository.findById(rideId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Ride not found"
            ));

        if (ride.getDriver() == null ||
            ride.getDriver().getUser() == null ||
            ride.getDriver().getUser().getEmail() == null ||
            !ride.getDriver().getUser().getEmail()
                .equalsIgnoreCase(driverEmail)) {

            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You are not authorized to share this ride location"
            );
        }

        // GPS sharing is allowed ONLY after the trip starts.
        if (!"IN_PROGRESS".equalsIgnoreCase(ride.getStatus())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "GPS sharing is available only during an active trip"
            );
        }

        RideLocation location = locationRepository
            .findByRide_Id(rideId)
            .orElseGet(() -> {
                RideLocation newLocation = new RideLocation();
                newLocation.setRide(ride);
                return newLocation;
            });

        location.setLatitude(request.latitude());
        location.setLongitude(request.longitude());
        location.setAccuracyMeters(request.accuracyMeters());
        location.setSpeedMetersPerSecond(request.speedMetersPerSecond());
        location.setHeadingDegrees(request.headingDegrees());
        location.setUpdatedAt(LocalDateTime.now());

        return toResponse(locationRepository.save(location));
    }

    // Only verified BOARDED passengers can view GPS during an active trip.
    @Transactional(readOnly = true)
    public RideLocationResponse getPassengerLocation(
        Long rideId,
        String passengerEmail
    ) {
        Ride ride = rideRepository.findById(rideId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Ride not found"
            ));

        // Passenger access is restricted to an active trip.
        if (!"IN_PROGRESS".equalsIgnoreCase(ride.getStatus())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Live location is available only during an active trip"
            );
        }

        boolean hasBoardedBooking =
            bookingRepository
                .existsByRide_IdAndPassenger_EmailIgnoreCaseAndStatusIgnoreCase(
                    rideId,
                    passengerEmail,
                    "BOARDED"
                );

        if (!hasBoardedBooking) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only verified boarded passengers can view driver location"
            );
        }

        RideLocation location = locationRepository
            .findByRide_Id(rideId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Driver location is not available yet"
            ));

        return toResponse(location);
    }

    private RideLocationResponse toResponse(RideLocation location) {
        return new RideLocationResponse(
            location.getRide().getId(),
            location.getLatitude(),
            location.getLongitude(),
            location.getAccuracyMeters(),
            location.getSpeedMetersPerSecond(),
            location.getHeadingDegrees(),
            location.getUpdatedAt()
        );
    }
}
