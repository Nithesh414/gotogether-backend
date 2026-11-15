package com.gotogether.backend.repository;

import com.gotogether.backend.entity.RideLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RideLocationRepository
        extends JpaRepository<RideLocation, Long> {

    Optional<RideLocation> findByRide_Id(Long rideId);
}