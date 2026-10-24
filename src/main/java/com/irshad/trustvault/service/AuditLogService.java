
package com.irshad.trustvault.service;

import com.irshad.trustvault.entity.AuditLog;
import com.irshad.trustvault.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLog logEvent(
            String userEmail,
            String action,
            Long documentId,
            String outcome,
            String details) {

        AuditLog auditLog = new AuditLog();
        auditLog.setUserEmail(
                userEmail == null ? "UNKNOWN" : userEmail);
        auditLog.setAction(action);
        auditLog.setDocumentId(documentId);
        auditLog.setOutcome(outcome);
        auditLog.setDetails(details);

        return auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAllLogs() {
        return auditLogRepository.findAllByOrderByTimestampDesc();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getLogsByUser(String userEmail) {
        return auditLogRepository
                .findByUserEmailOrderByTimestampDesc(userEmail);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getLogsByDocument(Long documentId) {
        return auditLogRepository
                .findByDocumentIdOrderByTimestampDesc(documentId);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getLogsByOutcome(String outcome) {
        return auditLogRepository
                .findByOutcomeOrderByTimestampDesc(outcome);
    }
}
