
package com.irshad.trustvault.controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.irshad.trustvault.dto.DocumentResponse;
import com.irshad.trustvault.entity.Document;
import com.irshad.trustvault.entity.DocumentPermission;
import com.irshad.trustvault.entity.User;
import com.irshad.trustvault.repository.DocumentPermissionRepository;
import com.irshad.trustvault.service.AuditLogService;
import com.irshad.trustvault.service.DocumentPermissionService;
import com.irshad.trustvault.service.DocumentService;
import com.irshad.trustvault.service.UserService;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
private static final Logger logger =
        LoggerFactory.getLogger(DocumentController.class);
    private final DocumentService documentService;
    private final UserService userService;
    private final DocumentPermissionService permissionService;
    private final DocumentPermissionRepository permissionRepository;
    private final AuditLogService auditLogService;

    public DocumentController(
            DocumentService documentService,
            UserService userService,
            DocumentPermissionService permissionService,
            DocumentPermissionRepository permissionRepository,
            AuditLogService auditLogService) {

        this.documentService = documentService;
        this.userService = userService;
        this.permissionService = permissionService;
        this.permissionRepository = permissionRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<?> getMyDocuments(
            Authentication authentication) {

        User currentUser = userService
                .findByEmail(authentication.getName())
                .orElse(null);

        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Authenticated user not found.");
        }

        List<Document> ownedDocuments =
                documentService.findByOwnerEmail(authentication.getName());

        List<Document> sharedDocuments = permissionRepository
                .findByUser_Email(authentication.getName())
                .stream()
                .filter(permission -> permissionService.canRead(
                        permission.getDocument(), currentUser))
                .map(DocumentPermission::getDocument)
                .toList();

        List<DocumentResponse> responses = Stream
                .concat(ownedDocuments.stream(), sharedDocuments.stream())
                .distinct()
                .map(document -> new DocumentResponse(
                        document.getId(),
                        document.getFileName(),
                        document.getContentType(),
                        document.getFileSize(),
                        document.getUploadedAt()))
                .toList();

        return ResponseEntity.ok(responses);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        User owner = userService
                .findByEmail(authentication.getName())
                .orElse(null);

        if (owner == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Authenticated user not found.");
        }

        try {
            Document document = documentService.upload(file, owner);

            DocumentResponse response = new DocumentResponse(
                    document.getId(),
                    document.getFileName(),
                    document.getContentType(),
                    document.getFileSize(),
                    document.getUploadedAt());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(response);

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(exception.getMessage());

        } catch (IOException exception) {
    logger.error("Document upload failed while storing the file.", exception);

    return ResponseEntity.status(
            HttpStatus.INTERNAL_SERVER_ERROR)
            .body("The document could not be stored.");
}
    }

    @PutMapping(
            value = "/{documentId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> replaceDocument(
            @PathVariable Long documentId,
            @RequestParam("file") MultipartFile replacementFile,
            Authentication authentication) {

        String userEmail = authentication.getName();

        User currentUser = userService
                .findByEmail(userEmail)
                .orElse(null);

        if (currentUser == null) {
            auditLogService.logEvent(
                    userEmail, "DOCUMENT_EDIT", documentId,
                    "DENIED", "Authenticated user not found.");

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Authenticated user not found.");
        }

        Document document = documentService.findById(documentId)
                .orElse(null);

        if (document == null) {
            auditLogService.logEvent(
                    userEmail, "DOCUMENT_EDIT", documentId,
                    "DENIED", "Document not found.");

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Document not found.");
        }

        if (!permissionService.canEdit(document, currentUser)) {
            auditLogService.logEvent(
                    userEmail, "DOCUMENT_EDIT", documentId,
                    "DENIED", "User does not have edit permission.");

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You do not have permission to edit this document.");
        }

        try {
            Document updatedDocument = documentService.replaceDocument(
                    document, replacementFile);

            DocumentResponse response = new DocumentResponse(
                    updatedDocument.getId(),
                    updatedDocument.getFileName(),
                    updatedDocument.getContentType(),
                    updatedDocument.getFileSize(),
                    updatedDocument.getUploadedAt());

            auditLogService.logEvent(
                    userEmail, "DOCUMENT_EDIT", documentId,
                    "ALLOWED", "Document replaced successfully.");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException exception) {
            auditLogService.logEvent(
                    userEmail, "DOCUMENT_EDIT", documentId,
                    "FAILED", "Replacement file failed validation.");

            return ResponseEntity.badRequest()
                    .body(exception.getMessage());

        } catch (IOException exception) {
            auditLogService.logEvent(
                    userEmail, "DOCUMENT_EDIT", documentId,
                    "FAILED", "An I/O error occurred during replacement.");

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("The document could not be replaced.");
        }
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable Long documentId,
            Authentication authentication) {

        String userEmail = authentication.getName();

        User currentUser = userService
                .findByEmail(userEmail)
                .orElse(null);

        if (currentUser == null) {
            auditLogService.logEvent(
                    userEmail, "DOCUMENT_DELETE", documentId,
                    "DENIED", "Authenticated user not found.");

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Authenticated user not found.");
        }

        Document document = documentService.findById(documentId)
                .orElse(null);

        if (document == null) {
            auditLogService.logEvent(
                    userEmail, "DOCUMENT_DELETE", documentId,
                    "DENIED", "Document not found.");

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Document not found.");
        }

        // Only the document owner may delete it.
        if (!permissionService.isOwner(document, currentUser)) {
            auditLogService.logEvent(
                    userEmail, "DOCUMENT_DELETE", documentId,
                    "DENIED", "Only the document owner can delete it.");

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Only the document owner can delete this document.");
        }

        try {
            documentService.deleteDocument(document);

            auditLogService.logEvent(
                    userEmail, "DOCUMENT_DELETE", documentId,
                    "ALLOWED", "Document deleted by its owner.");

            return ResponseEntity.ok()
                    .body("Document deleted successfully.");

        } catch (IOException exception) {
            auditLogService.logEvent(
                    userEmail, "DOCUMENT_DELETE", documentId,
                    "FAILED", "An I/O error occurred during deletion.");

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("The document could not be deleted.");
        }
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<?> downloadDocument(
            @PathVariable Long documentId,
            Authentication authentication) {

        String userEmail = authentication.getName();

        User currentUser = userService
                .findByEmail(userEmail)
                .orElse(null);

        if (currentUser == null) {
            auditLogService.logEvent(
                    userEmail, "DOCUMENT_DOWNLOAD", documentId,
                    "DENIED", "Authenticated user not found.");

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Authenticated user not found.");
        }

        Document document = documentService.findById(documentId)
                .orElse(null);

        if (document == null) {
            auditLogService.logEvent(
                    userEmail, "DOCUMENT_DOWNLOAD", documentId,
                    "DENIED", "Document not found.");

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Document not found.");
        }

        if (!permissionService.canRead(document, currentUser)) {
            auditLogService.logEvent(
                    userEmail, "DOCUMENT_DOWNLOAD", documentId,
                    "DENIED", "User does not have read permission.");

            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You do not have permission to download this document.");
        }

        try {
            Resource resource = documentService.downloadFile(document);

            String contentType = document.getContentType();
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;

            if (contentType != null) {
                try {
                    mediaType = MediaType.parseMediaType(contentType);
                } catch (IllegalArgumentException ignored) {
                    // Use a generic binary content type if invalid.
                }
            }

            String safeFileName = document.getFileName() == null
                    ? "document"
                    : document.getFileName()
                            .replaceAll("[\\r\\n\"\\\\/]", "_");

            ResponseEntity<Resource> response = ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment()
                                    .filename(safeFileName)
                                    .build()
                                    .toString())
                    .body(resource);

            auditLogService.logEvent(
                    userEmail, "DOCUMENT_DOWNLOAD", documentId,
                    "ALLOWED", "Download request authorized.");

            return response;

        } catch (IOException exception) {
            auditLogService.logEvent(
                    userEmail, "DOCUMENT_DOWNLOAD", documentId,
                    "DENIED", "Stored document could not be retrieved.");

            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("The document could not be retrieved.");
        }
    }
}
