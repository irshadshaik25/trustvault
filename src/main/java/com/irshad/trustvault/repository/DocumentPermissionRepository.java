
package com.irshad.trustvault.repository;

import com.irshad.trustvault.entity.DocumentPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentPermissionRepository
        extends JpaRepository<DocumentPermission, Long> {

    List<DocumentPermission> findByDocument_Id(Long documentId);

    Optional<DocumentPermission> findByDocument_IdAndUser_Id(
            Long documentId, Long userId);

    boolean existsByDocument_IdAndUser_Id(
            Long documentId, Long userId);

    void deleteByDocument_IdAndUser_Id(
            Long documentId, Long userId);

    // Find all documents shared with a user by their email
    List<DocumentPermission> findByUser_Email(String email);
    void deleteByDocument_Id(Long documentId);
}
