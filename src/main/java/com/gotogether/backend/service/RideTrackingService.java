
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
            BookingRepository bookingRepository) {
        this.rideRepository = rideRepository;
        this.locationRepository = locationRepository;
        this.bookingRepository = bookingRepository;
    }

    // DRIVER: Start sharing before boarding.
    @Transactional
    public void startSharing(Long rideId, String driverEmail) {

        Ride ride = getRide(rideId);
        verifyDriver(ride, driverEmail);

        String status = ride.getStatus();

        if (!"SCHEDULED".equalsIgnoreCase(status)
                && !"BOARDING".equalsIgnoreCase(status)
                && !"IN_PROGRESS".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "GPS sharing cannot be started for this ride"
            );
        }

        ride.setGpsSharingActive(true);
        rideRepository.save(ride);
    }

    // DRIVER: Stop sharing manually.
    @Transactional
    public void stopSharing(Long rideId, String driverEmail) {

        Ride ride = getRide(rideId);
        verifyDriver(ride, driverEmail);

        ride.setGpsSharingActive(false);
        rideRepository.save(ride);
    }

    // DRIVER: Upload latest GPS coordinates.
    @Transactional
    public RideLocationResponse updateLocation(
            Long rideId,
            String driverEmail,
            UpdateRideLocationRequest request) {

        validateCoordinates(request);

        Ride ride = getRide(rideId);
        verifyDriver(ride, driverEmail);

        String status = ride.getStatus();

        if (!ride.isGpsSharingActive()
                || (!"SCHEDULED".equalsIgnoreCase(status)
                && !"BOARDING".equalsIgnoreCase(status)
                && !"IN_PROGRESS".equalsIgnoreCase(status))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Start GPS sharing before uploading your location"
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
        location.setSpeedMetersPerSecond(
                request.speedMetersPerSecond());
        location.setHeadingDegrees(request.headingDegrees());
        location.setUpdatedAt(LocalDateTime.now());

        return toResponse(locationRepository.save(location));
    }

    // PASSENGER: View GPS before boarding and during the trip.
    @Transactional(readOnly = true)
    public RideLocationResponse getPassengerLocation(
            Long rideId,
            String passengerEmail) {

        Ride ride = getRide(rideId);

        if (!ride.isGpsSharingActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "The driver has not started GPS sharing"
            );
        }

        String status = ride.getStatus();

        if (!"SCHEDULED".equalsIgnoreCase(status)
                && !"BOARDING".equalsIgnoreCase(status)
                && !"IN_PROGRESS".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Live location is unavailable for this ride"
            );
        }

        boolean hasConfirmedBooking =
                bookingRepository
                        .existsByRide_IdAndPassenger_EmailIgnoreCaseAndStatusIgnoreCase(
                                rideId, passengerEmail, "CONFIRMED");

        boolean hasBoardedBooking =
                bookingRepository
                        .existsByRide_IdAndPassenger_EmailIgnoreCaseAndStatusIgnoreCase(
                                rideId, passengerEmail, "BOARDED");

        if (!hasConfirmedBooking && !hasBoardedBooking) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only passengers with confirmed bookings can view driver location"
            );
        }

        RideLocation location = locationRepository
                .findByRide_Id(rideId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Driver location is not available yet"
                ));

        // Do not show an old/stale GPS position.
        if (location.getUpdatedAt() == null
                || location.getUpdatedAt().isBefore(
                        LocalDateTime.now().minusSeconds(60))) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Waiting for the driver's latest GPS location"
            );
        }

        return toResponse(location);
    }

    private Ride getRide(Long rideId) {
        return rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Ride not found"
                ));
    }

    private void verifyDriver(Ride ride, String driverEmail) {
        if (ride.getDriver() == null
                || ride.getDriver().getUser() == null
                || ride.getDriver().getUser().getEmail() == null
                || !ride.getDriver().getUser().getEmail()
                        .equalsIgnoreCase(driverEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not authorized to manage this ride's GPS"
            );
        }
    }

    private void validateCoordinates(
            UpdateRideLocationRequest request) {

        if (request == null
                || request.latitude() == null
                || request.longitude() == null
                || !Double.isFinite(request.latitude())
                || !Double.isFinite(request.longitude())
                || request.latitude() < -90
                || request.latitude() > 90
                || request.longitude() < -180
                || request.longitude() > 180) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid GPS coordinates"
            );
        }

        if (request.accuracyMeters() != null
                && (!Double.isFinite(request.accuracyMeters())
                || request.accuracyMeters() < 0)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid GPS accuracy"
            );
        }

        if (request.speedMetersPerSecond() != null
                && (!Double.isFinite(request.speedMetersPerSecond())
                || request.speedMetersPerSecond() < 0)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid GPS speed"
            );
        }

        if (request.headingDegrees() != null
                && (!Double.isFinite(request.headingDegrees())
                || request.headingDegrees() < 0
                || request.headingDegrees() >= 360)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid GPS heading"
            );
        }
    }

    private RideLocationResponse toResponse(
            RideLocation location) {

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
