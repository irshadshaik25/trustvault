package com.irshad.trustvault;

import com.irshad.trustvault.controller.DocumentController;
import com.irshad.trustvault.entity.Document;
import com.irshad.trustvault.entity.User;
import com.irshad.trustvault.repository.DocumentPermissionRepository;
import com.irshad.trustvault.service.AuditLogService;
import com.irshad.trustvault.service.DocumentPermissionService;
import com.irshad.trustvault.service.DocumentService;
import com.irshad.trustvault.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentControllerTests {

    private DocumentService documentService;
    private UserService userService;
    private DocumentPermissionService permissionService;
    private DocumentPermissionRepository permissionRepository;
    private AuditLogService auditLogService;
    private DocumentController controller;

    private User owner;
    private User viewer;
    private Document document;
    private Authentication viewerAuthentication;

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentService.class);
        userService = mock(UserService.class);
        permissionService = mock(DocumentPermissionService.class);
        permissionRepository = mock(DocumentPermissionRepository.class);
        auditLogService = mock(AuditLogService.class);

        controller = new DocumentController(
                documentService,
                userService,
                permissionService,
                permissionRepository,
                auditLogService
        );

        owner = mock(User.class);
        viewer = mock(User.class);
        document = mock(Document.class);

        when(owner.getId()).thenReturn(1L);
        when(viewer.getId()).thenReturn(2L);
        when(document.getId()).thenReturn(10L);
        when(document.getOwner()).thenReturn(owner);

        viewerAuthentication = new UsernamePasswordAuthenticationToken(
                "viewer@example.com", "N/A"
        );

        when(userService.findByEmail("viewer@example.com"))
                .thenReturn(Optional.of(viewer));
        when(documentService.findById(10L))
                .thenReturn(Optional.of(document));
    }

    @Test
    void viewerCannotReplaceDocument() throws Exception {
        when(permissionService.canEdit(document, viewer))
                .thenReturn(false);

        MockMultipartFile replacement = new MockMultipartFile(
                "file", "replacement.txt", "text/plain",
                "replacement".getBytes()
        );

        ResponseEntity<?> response = controller.replaceDocument(
                10L, replacement, viewerAuthentication
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(documentService, never())
                .replaceDocument(any(), any());
    }

    @Test
    void editorCannotDeleteDocumentOwnedBySomeoneElse() throws Exception {
        when(permissionService.isOwner(document, viewer))
                .thenReturn(false);

        ResponseEntity<?> response = controller.deleteDocument(
                10L, viewerAuthentication
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(documentService, never()).deleteDocument(any());
    }

    @Test
    void userWithoutReadPermissionCannotDownloadDocument() throws Exception {
        when(permissionService.canRead(document, viewer))
                .thenReturn(false);

        ResponseEntity<?> response = controller.downloadDocument(
                10L, viewerAuthentication
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(documentService, never()).downloadFile(any());
    }

    @Test
    void replacementReturnsNotFoundForMissingDocument() throws Exception {
        when(documentService.findById(99L)).thenReturn(Optional.empty());

        MockMultipartFile replacement = new MockMultipartFile(
                "file", "replacement.txt", "text/plain",
                "replacement".getBytes()
        );

        ResponseEntity<?> response = controller.replaceDocument(
                99L, replacement, viewerAuthentication
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(documentService, never()).replaceDocument(any(), any());
    }

    @Test
    void deletionReturnsNotFoundForMissingDocument() throws Exception {
        when(documentService.findById(99L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.deleteDocument(
                99L, viewerAuthentication
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(documentService, never()).deleteDocument(any());
    }
}