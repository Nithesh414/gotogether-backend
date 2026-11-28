package com.gotogether.backend.repository;

import com.gotogether.backend.entity.DriverDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DriverDocumentRepository
        extends JpaRepository<DriverDocument, Long> {

    List<DriverDocument> findByDriver_Id(Long driverId);

    Optional<DriverDocument> findByIdAndDriver_Id(
            Long documentId,
            Long driverId
    );

    Optional<DriverDocument> findByDriver_IdAndDocumentTypeAndDocumentSide(
            Long driverId,
            String documentType,
            String documentSide
    );

    List<DriverDocument> findByDriver_IdAndReviewStatusIgnoreCase(
            Long driverId,
            String reviewStatus
    );

    List<DriverDocument> findByDriver_IdInAndDocumentTypeIgnoreCaseAndDocumentSideIgnoreCase(
            List<Long> driverIds,
            String documentType,
            String documentSide
    );
}
