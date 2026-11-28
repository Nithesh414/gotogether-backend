package com.gotogether.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.gotogether.backend.dto.DriverDocumentResponse;
import com.gotogether.backend.entity.DriverDocument;
import com.gotogether.backend.entity.DriverProfile;
import com.gotogether.backend.entity.User;
import com.gotogether.backend.repository.DriverDocumentRepository;
import com.gotogether.backend.repository.DriverProfileRepository;
import com.gotogether.backend.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DriverDocumentService {

    private static final long MAX_FILE_SIZE = 1L * 1024 * 1024;

    private final Cloudinary cloudinary;
    private final UserRepository userRepository;
    private final DriverProfileRepository driverRepository;
    private final DriverDocumentRepository documentRepository;

    public DriverDocumentService(
        Cloudinary cloudinary,
        UserRepository userRepository,
        DriverProfileRepository driverRepository,
        DriverDocumentRepository documentRepository
    ) {
        this.cloudinary = cloudinary;
        this.userRepository = userRepository;
        this.driverRepository = driverRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional
    public DriverDocumentResponse uploadDocument(
        String email,
        MultipartFile file,
        String documentTypeInput,
        String documentSideInput,
        String expiryDateInput
    ) {
        if (file == null || file.isEmpty()) {
            throw badRequest("Please select a document");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw badRequest("Document size must not exceed 1 MB");
        }

        if (documentTypeInput == null || documentTypeInput.isBlank()) {
            throw badRequest("Document type is required");
        }

        String documentType = documentTypeInput.trim().toUpperCase();
        String documentSide = documentSideInput == null
            ? "SINGLE"
            : documentSideInput.trim().toUpperCase();

        Set<String> allowedTypes = Set.of(
            "DRIVING_LICENCE",
            "VEHICLE_RC",
            "INSURANCE",
            "PUC",
            "ADDRESS_PROOF",
            "DRIVER_PHOTO",
            "VEHICLE_PERMIT",
            "FITNESS_CERTIFICATE"
        );

        if (!allowedTypes.contains(documentType)) {
            throw badRequest("Unsupported document type");
        }

        boolean frontBackDocument =
            documentType.equals("DRIVING_LICENCE") ||
            documentType.equals("VEHICLE_RC");

        if (frontBackDocument) {
            if (!Set.of("FRONT", "BACK").contains(documentSide)) {
                throw badRequest(
                    "Driving licence and RC require FRONT or BACK"
                );
            }
        } else if (!documentSide.equals("SINGLE")) {
            throw badRequest(
                "This document type must use SINGLE"
            );
        }

        LocalDate expiryDate = null;

        if (expiryDateInput != null && !expiryDateInput.isBlank()) {
            try {
                expiryDate = LocalDate.parse(expiryDateInput.trim());
            } catch (DateTimeParseException ex) {
                throw badRequest("Expiry date must use YYYY-MM-DD");
            }
        }

        boolean expiryRequired = Set.of(
            "DRIVING_LICENCE",
            "INSURANCE",
            "PUC",
            "VEHICLE_PERMIT",
            "FITNESS_CERTIFICATE"
        ).contains(documentType);

        if (expiryRequired && expiryDate == null) {
            throw badRequest("Expiry date is required for this document");
        }

        if (expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
            throw badRequest("Expired documents cannot be uploaded");
        }

        byte[] bytes;

        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw badRequest("Unable to read the uploaded document");
        }

        String contentType = detectContentType(bytes);

        if (contentType == null) {
            throw badRequest(
                "Only valid JPG, PNG and PDF files are accepted"
            );
        }

        if (documentType.equals("DRIVER_PHOTO") &&
            contentType.equals("application/pdf")) {
            throw badRequest("Driver photo must be a JPG or PNG image");
        }

        if (contentType.startsWith("image/")) {
            try {
                if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
                    throw badRequest("Invalid image file");
                }
            } catch (IOException ex) {
                throw badRequest("Unable to validate image");
            }
        }

        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "User not found"
            ));

        DriverProfile driver = driverRepository
            .findByUser_Id(user.getId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Driver profile not found"
            ));

        DriverDocument document = documentRepository
            .findByDriver_IdAndDocumentTypeAndDocumentSide(
                driver.getId(),
                documentType,
                documentSide
            )
            .orElseGet(DriverDocument::new);

        String publicId = document.getCloudinaryPublicId();

        if (publicId == null || publicId.isBlank()) {
            publicId = "driver_" + driver.getId()
                + "_" + documentType.toLowerCase()
                + "_" + documentSide.toLowerCase()
                + "_" + UUID.randomUUID().toString().replace("-", "");
        }

        try {
            Map uploadResult = cloudinary.uploader().upload(
                bytes,
                ObjectUtils.asMap(
                    "folder", "gotogether/driver-verification",
                    "public_id", publicId,
                    "overwrite", true,
                    "invalidate", true,
                    "type", "authenticated",
                    "resource_type", "image"
                )
            );

            Object uploadedPublicId = uploadResult.get("public_id");

            if (uploadedPublicId == null) {
                throw new IllegalStateException(
                    "Cloudinary did not return an asset identifier"
                );
            }

            document.setDriver(driver);
            document.setDocumentType(documentType);
            document.setDocumentSide(documentSide);
            document.setCloudinaryPublicId(uploadedPublicId.toString());
            document.setCloudinaryAssetId(
                uploadResult.get("asset_id") == null
                    ? null
                    : uploadResult.get("asset_id").toString()
            );
            document.setResourceType("image");
            document.setDeliveryType("authenticated");
            document.setFileFormat(
                uploadResult.get("format") == null
                    ? null
                    : uploadResult.get("format").toString()
            );
            document.setContentType(contentType);
            document.setFileSize(
                uploadResult.get("bytes") instanceof Number size
                    ? size.longValue()
                    : (long) bytes.length
            );
            document.setExpiryDate(expiryDate);
            document.setReviewStatus("PENDING");
            document.setRejectionReason(null);
            document.setReviewedByAdminId(null);
            document.setReviewedAt(null);

            DriverDocument saved = documentRepository.save(document);

            driver.setVerificationStatus("PENDING");
            driver.setRejectionReason(null);
            driverRepository.save(driver);

            return toResponse(saved);

        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Unable to upload document securely"
            );
        }
    }

    @Transactional(readOnly = true)
    public List<DriverDocumentResponse> getMyDocuments(String email) {

        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "User not found"
            ));

        DriverProfile driver = driverRepository
            .findByUser_Id(user.getId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Driver profile not found"
            ));

        return documentRepository.findByDriver_Id(driver.getId())
            .stream()
            .sorted(Comparator.comparing(
                DriverDocument::getUploadedAt,
                Comparator.nullsLast(Comparator.reverseOrder())
            ))
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    private String detectContentType(byte[] bytes) {

        if (bytes.length >= 3 &&
            (bytes[0] & 0xFF) == 0xFF &&
            (bytes[1] & 0xFF) == 0xD8 &&
            (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }

        if (bytes.length >= 8 &&
            (bytes[0] & 0xFF) == 0x89 &&
            bytes[1] == 0x50 &&
            bytes[2] == 0x4E &&
            bytes[3] == 0x47 &&
            bytes[4] == 0x0D &&
            bytes[5] == 0x0A &&
            bytes[6] == 0x1A &&
            bytes[7] == 0x0A) {
            return "image/png";
        }

        if (bytes.length >= 5 &&
            bytes[0] == '%' &&
            bytes[1] == 'P' &&
            bytes[2] == 'D' &&
            bytes[3] == 'F' &&
            bytes[4] == '-') {
            return "application/pdf";
        }

        return null;
    }

    private DriverDocumentResponse toResponse(DriverDocument document) {
        return new DriverDocumentResponse(
            document.getId(),
            document.getDocumentType(),
            document.getDocumentSide(),
            document.getFileFormat(),
            document.getContentType(),
            document.getFileSize(),
            document.getExpiryDate(),
            document.getReviewStatus(),
            document.getRejectionReason(),
            document.getUploadedAt(),
            document.getReviewedAt()
        );
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            message
        );
    }
}