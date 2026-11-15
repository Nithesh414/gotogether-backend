package com.gotogether.backend.repository;

import com.gotogether.backend.entity.Ride;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RideRepository extends JpaRepository<Ride, Long> {

    List<Ride> findByStatusIgnoreCase(String status);

    List<Ride> findByFromLocationContainingIgnoreCaseAndToLocationContainingIgnoreCaseAndStatusIgnoreCase(
        String fromLocation,
        String toLocation,
        String status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Ride r WHERE r.id = :rideId")
    Optional<Ride> findByIdForUpdate(@Param("rideId") Long rideId);

    // Get all rides created by a particular driver.
    List<Ride> findByDriver_IdOrderByCreatedAtDesc(Long driverId);


    List<Ride> findByDriver_IdAndStatusIgnoreCaseOrderByCreatedAtDesc(
    Long driverId,
    String status
);
}