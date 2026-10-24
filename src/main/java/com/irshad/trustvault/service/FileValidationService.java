
package com.irshad.trustvault.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Service
public class FileValidationService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please select a non-empty file.");
        }

        if (file.getSize() > 10L * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "File size must not exceed 10 MB.");
        }

        String contentType = file.getContentType();

        if (contentType == null ||
                !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "This file type is not allowed.");
        }

        String originalName = file.getOriginalFilename();

        if (originalName == null ||
                originalName.isBlank() ||
                originalName.contains("/") ||
                originalName.contains("\\") ||
                originalName.equals(".") ||
                originalName.equals("..")) {
            throw new IllegalArgumentException(
                    "Invalid file name.");
        }
    }
}
