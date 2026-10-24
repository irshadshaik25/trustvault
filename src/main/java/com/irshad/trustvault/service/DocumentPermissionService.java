
package com.irshad.trustvault.service;

import com.irshad.trustvault.entity.Document;
import com.irshad.trustvault.entity.DocumentPermission;
import com.irshad.trustvault.entity.User;
import com.irshad.trustvault.repository.DocumentPermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentPermissionService {

    private final DocumentPermissionRepository permissionRepository;

    public DocumentPermissionService(
            DocumentPermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    public boolean canRead(Document document, User user) {
        if (isOwner(document, user)) {
            return true;
        }

        return permissionRepository
                .findByDocument_IdAndUser_Id(
                        document.getId(), user.getId())
                .map(permission ->
                        permission.getPermission().equals("VIEWER")
                        || permission.getPermission().equals("EDITOR"))
                .orElse(false);
    }

    public boolean canEdit(Document document, User user) {
        if (isOwner(document, user)) {
            return true;
        }

        return permissionRepository
                .findByDocument_IdAndUser_Id(
                        document.getId(), user.getId())
                .map(permission ->
                        permission.getPermission().equals("EDITOR"))
                .orElse(false);
    }

    public boolean isOwner(Document document, User user) {
        return document.getOwner().getId().equals(user.getId());
    }

    @Transactional
    public DocumentPermission grantPermission(
            Document document, User user, String permissionType) {

        if (!permissionType.equals("VIEWER")
                && !permissionType.equals("EDITOR")) {
            throw new IllegalArgumentException(
                    "Permission must be VIEWER or EDITOR.");
        }

        DocumentPermission permission = permissionRepository
                .findByDocument_IdAndUser_Id(
                        document.getId(), user.getId())
                .orElseGet(DocumentPermission::new);

        permission.setDocument(document);
        permission.setUser(user);
        permission.setPermission(permissionType);

        return permissionRepository.save(permission);
    }

    @Transactional
    public void revokePermission(Long documentId, Long userId) {
        permissionRepository.deleteByDocument_IdAndUser_Id(
                documentId, userId);
    }
}
