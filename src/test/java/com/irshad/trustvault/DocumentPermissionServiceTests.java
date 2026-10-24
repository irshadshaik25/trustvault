
package com.irshad.trustvault;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import com.irshad.trustvault.entity.Document;
import com.irshad.trustvault.entity.DocumentPermission;
import com.irshad.trustvault.entity.User;
import com.irshad.trustvault.repository.DocumentPermissionRepository;
import com.irshad.trustvault.service.DocumentPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentPermissionServiceTests {

    private DocumentPermissionRepository repository;
    private DocumentPermissionService service;
    private Document document;
    private User owner;
    private User recipient;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentPermissionRepository.class);
        service = new DocumentPermissionService(repository);

        document = mock(Document.class);
        owner = mock(User.class);
        recipient = mock(User.class);

        when(document.getId()).thenReturn(10L);
        when(owner.getId()).thenReturn(1L);
        when(recipient.getId()).thenReturn(2L);
        when(document.getOwner()).thenReturn(owner);
    }

    @Test
    void ownerCanReadAndEditDocument() {
        assertTrue(service.canRead(document, owner));
        assertTrue(service.canEdit(document, owner));
        verifyNoInteractions(repository);
    }

    @Test
    void viewerCanReadButCannotEdit() {
        DocumentPermission permission = permission("VIEWER");
        when(repository.findByDocument_IdAndUser_Id(10L, 2L))
                .thenReturn(Optional.of(permission));

        assertTrue(service.canRead(document, recipient));
        assertFalse(service.canEdit(document, recipient));
    }

    @Test
    void editorCanReadAndEdit() {
        DocumentPermission permission = permission("EDITOR");
        when(repository.findByDocument_IdAndUser_Id(10L, 2L))
                .thenReturn(Optional.of(permission));

        assertTrue(service.canRead(document, recipient));
        assertTrue(service.canEdit(document, recipient));
    }

    @Test
    void userWithoutPermissionCannotReadOrEdit() {
        when(repository.findByDocument_IdAndUser_Id(10L, 2L))
                .thenReturn(Optional.empty());

        assertFalse(service.canRead(document, recipient));
        assertFalse(service.canEdit(document, recipient));
    }

    @Test
    void invalidPermissionTypeIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.grantPermission(document, recipient, "ADMIN")
        );

        verifyNoInteractions(repository);
    }

    @Test
    void grantPermissionSavesViewerPermission() {
        when(repository.findByDocument_IdAndUser_Id(10L, 2L))
                .thenReturn(Optional.empty());

        DocumentPermission savedPermission = new DocumentPermission();
        when(repository.save(any(DocumentPermission.class)))
                .thenReturn(savedPermission);

        DocumentPermission result =
                service.grantPermission(document, recipient, "VIEWER");

        assertSame(savedPermission, result);
        verify(repository).save(argThat(permission ->
                permission.getDocument() == document
                && permission.getUser() == recipient
                && permission.getPermission().equals("VIEWER")
        ));
    }

    @Test
    void revokePermissionDeletesDocumentUserPermission() {
        service.revokePermission(10L, 2L);

        verify(repository).deleteByDocument_IdAndUser_Id(10L, 2L);
    }


    private DocumentPermission permission(String type) {
        DocumentPermission permission = new DocumentPermission();
        permission.setDocument(document);
        permission.setUser(recipient);
        permission.setPermission(type);
        return permission;
    }
}
