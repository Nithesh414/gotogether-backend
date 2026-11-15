package com.gotogether.backend.repository;

import com.gotogether.backend.entity.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByPassenger_EmailIgnoreCaseOrderByCreatedAtDesc(
        String email
    );

    Optional<Booking> findByIdAndPassenger_EmailIgnoreCase(
        Long id,
        String email
    );

    boolean existsByRide_IdAndPassenger_IdAndStatusIgnoreCase(
        Long rideId,
        Long passengerId,
        String status
    );


    boolean existsByRide_IdAndPassenger_EmailIgnoreCaseAndStatusIgnoreCase(
    Long rideId,
    String email,
    String status
);

    @Query("""
        SELECT b.ride.id
        FROM Booking b
        WHERE b.id = :bookingId
        AND LOWER(b.passenger.email) = LOWER(:email)
    """)
    Optional<Long> findRideIdForPassenger(
        @Param("bookingId") Long bookingId,
        @Param("email") String email
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :bookingId")
    Optional<Booking> findByIdForUpdate(
        @Param("bookingId") Long bookingId
    );

    List<Booking> findByPassenger_EmailIgnoreCaseAndStatusIgnoreCaseOrderByCreatedAtDesc(
    String email,
    String status
);

List<Booking> findByRide_IdOrderByCreatedAtDesc(Long rideId);


@Query("""
    SELECT b.ride.id
    FROM Booking b
    WHERE b.id = :bookingId
""")
Optional<Long> findRideIdByBookingId(
    @Param("bookingId") Long bookingId
);


boolean existsByRide_IdAndStatusIgnoreCase(
    Long rideId,
    String status
);



}