
package com.irshad.trustvault.controller;

import com.irshad.trustvault.entity.AuditLog;
import com.irshad.trustvault.service.AuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<List<AuditLog>> getAuditLogs(
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) Long documentId,
            @RequestParam(required = false) String outcome) {

        List<AuditLog> logs;

        if (userEmail != null && !userEmail.isBlank()) {
            logs = auditLogService.getLogsByUser(userEmail);
        } else if (documentId != null) {
            logs = auditLogService.getLogsByDocument(documentId);
        } else if (outcome != null && !outcome.isBlank()) {
            if (!outcome.equals("ALLOWED")
                    && !outcome.equals("DENIED")) {
                return ResponseEntity.badRequest().build();
            }
            logs = auditLogService.getLogsByOutcome(outcome);
        } else {
            logs = auditLogService.getAllLogs();
        }

        return ResponseEntity.ok(logs);
    }
}
