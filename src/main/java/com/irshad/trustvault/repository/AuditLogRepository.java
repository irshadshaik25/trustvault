
package com.irshad.trustvault.repository;

import com.irshad.trustvault.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByOrderByTimestampDesc();

    List<AuditLog> findByUserEmailOrderByTimestampDesc(
            String userEmail);

    List<AuditLog> findByDocumentIdOrderByTimestampDesc(
            Long documentId);

    List<AuditLog> findByOutcomeOrderByTimestampDesc(
            String outcome);
}
