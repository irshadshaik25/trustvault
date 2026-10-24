
package com.irshad.trustvault.controller;

import com.irshad.trustvault.dto.ShareDocumentRequest;
import com.irshad.trustvault.entity.Document;
import com.irshad.trustvault.entity.DocumentPermission;
import com.irshad.trustvault.entity.User;
import com.irshad.trustvault.repository.DocumentRepository;
import com.irshad.trustvault.service.AuditLogService;
import com.irshad.trustvault.service.DocumentPermissionService;
import com.irshad.trustvault.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class SharingController {

    private final DocumentRepository documentRepository;
    private final UserService userService;
    private final DocumentPermissionService permissionService;
    private final AuditLogService auditLogService;

    public SharingController(
            DocumentRepository documentRepository,
            UserService userService,
            DocumentPermissionService permissionService,
            AuditLogService auditLogService) {
        this.documentRepository = documentRepository;
        this.userService = userService;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/{documentId}/share")
    public ResponseEntity<?> shareDocument(
            @PathVariable Long documentId,
            @Valid @RequestBody ShareDocumentRequest request,
            Authentication authentication) {

        Document document = documentRepository.findById(documentId)
                .orElse(null);

        if (document == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Document not found."));
        }

        String ownerEmail = document.getOwner().getEmail();

        if (!ownerEmail.equalsIgnoreCase(authentication.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "error",
                            "Only the document owner can share it."
                    ));
        }

        String recipientEmail = request.getEmail()
                .trim().toLowerCase();

        if (recipientEmail.equalsIgnoreCase(
                authentication.getName().trim())) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error",
                            "You already own this document."
                    ));
        }

        User recipient = userService.findByEmail(recipientEmail)
                .orElse(null);

        if (recipient == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error",
                            "Recipient account not found."
                    ));
        }

        DocumentPermission permission =
                permissionService.grantPermission(
                        document,
                        recipient,
                        request.getPermission()
                );

        auditLogService.logEvent(
                authentication.getName(),
                "DOCUMENT_SHARE",
                documentId,
                "ALLOWED",
                "Document shared with " + recipientEmail
                        + " using permission "
                        + permission.getPermission() + "."
        );

        return ResponseEntity.ok(Map.of(
                "documentId", documentId,
                "recipientEmail", recipientEmail,
                "permission", permission.getPermission(),
                "message", "Document access granted."
        ));
    }

    @DeleteMapping("/{documentId}/share/{userId}")
    public ResponseEntity<?> revokePermission(
            @PathVariable Long documentId,
            @PathVariable Long userId,
            Authentication authentication) {

        Document document = documentRepository.findById(documentId)
                .orElse(null);

        if (document == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Document not found."));
        }

        if (!document.getOwner().getEmail()
                .equalsIgnoreCase(authentication.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "error",
                            "Only the document owner can revoke access."
                    ));
        }

        permissionService.revokePermission(documentId, userId);

        auditLogService.logEvent(
                authentication.getName(),
                "DOCUMENT_REVOKE",
                documentId,
                "ALLOWED",
                "Document access revoked for user ID "
                        + userId + "."
        );

        return ResponseEntity.ok(Map.of(
                "message", "Document access revoked."
        ));
    }
}
