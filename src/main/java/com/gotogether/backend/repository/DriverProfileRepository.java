
package com.gotogether.backend.repository;

import com.gotogether.backend.entity.DriverProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DriverProfileRepository
        extends JpaRepository<DriverProfile, Long> {

    Optional<DriverProfile> findByUser_Id(Long userId);

    boolean existsByVehicleNumberIgnoreCase(String vehicleNumber);

    List<DriverProfile> findByVerificationStatusIgnoreCase(String status);
}
