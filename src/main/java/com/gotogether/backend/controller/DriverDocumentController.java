package com.gotogether.backend.controller;

import com.gotogether.backend.dto.DriverDocumentResponse;
import com.gotogether.backend.service.DriverDocumentService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/drivers/me/documents")
public class DriverDocumentController {

    private final DriverDocumentService documentService;

    public DriverDocumentController(
        DriverDocumentService documentService
    ) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(
        @RequestParam("file") MultipartFile file,
        @RequestParam("documentType") String documentType,
        @RequestParam(value = "documentSide", defaultValue = "SINGLE")
            String documentSide,
        @RequestParam(value = "expiryDate", required = false)
            String expiryDate,
        Authentication authentication
    ) {
        DriverDocumentResponse response =
            documentService.uploadDocument(
                authentication.getName(),
                file,
                documentType,
                documentSide,
                expiryDate
            );

        return ResponseEntity.ok(Map.of(
            "message", "Document uploaded successfully",
            "document", response
        ));
    }

    @GetMapping
    public ResponseEntity<List<DriverDocumentResponse>> getMyDocuments(
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            documentService.getMyDocuments(authentication.getName())
        );
    }
}