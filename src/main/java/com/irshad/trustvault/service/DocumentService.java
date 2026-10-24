
package com.irshad.trustvault.service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Files;
import java.nio.file.Path;
import com.irshad.trustvault.entity.Document;
import com.irshad.trustvault.entity.User;
import com.irshad.trustvault.repository.DocumentPermissionRepository;
import com.irshad.trustvault.repository.DocumentRepository;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentStorageService storageService;
    private final FileValidationService validationService;
    private final DocumentPermissionRepository documentPermissionRepository;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentStorageService storageService,
            FileValidationService validationService,
            DocumentPermissionRepository documentPermissionRepository) {

        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.validationService = validationService;
        this.documentPermissionRepository = documentPermissionRepository;
    }

    public Document upload(MultipartFile file, User owner)
            throws IOException {

        validationService.validate(file);

        String storedFileName = storageService.store(file);

        Document document = new Document();
        document.setFileName(file.getOriginalFilename());
        document.setContentType(file.getContentType());
        document.setFileSize(file.getSize());
        document.setStoragePath(storedFileName);
        document.setOwner(owner);

        return documentRepository.save(document);
    }

    @Transactional
    public Document replaceDocument(
            Document document,
            MultipartFile replacementFile) throws IOException {

        validationService.validate(replacementFile);

        String oldStoredFileName = document.getStoragePath();
        String newStoredFileName =
                storageService.storeReplacement(replacementFile);

        try {
            document.setFileName(
                    replacementFile.getOriginalFilename());
            document.setContentType(
                    replacementFile.getContentType());
            document.setFileSize(replacementFile.getSize());
            document.setStoragePath(newStoredFileName);

            Document savedDocument =
                    documentRepository.saveAndFlush(document);

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                storageService.deleteStoredFile(
                                        oldStoredFileName);
                            } catch (IOException exception) {
                                logger.error(
                                        "Replacement committed, but old file "
                                                + "could not be removed: {}",
                                        oldStoredFileName,
                                        exception);
                            }
                        }

                        @Override
                        public void afterCompletion(int status) {
                            if (status != STATUS_COMMITTED) {
                                try {
                                    storageService.deleteStoredFile(
                                            newStoredFileName);
                                } catch (IOException exception) {
                                    logger.error(
                                            "Could not clean up failed replacement: {}",
                                            newStoredFileName,
                                            exception);
                                }
                            }
                        }
                    });

            return savedDocument;

        } catch (RuntimeException exception) {
            try {
                storageService.deleteStoredFile(newStoredFileName);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    public Optional<Document> findById(Long id) {
        return documentRepository.findById(id);
    }

    public List<Document> findByOwnerId(Long ownerId) {
        return documentRepository.findByOwner_Id(ownerId);
    }

    public List<Document> findByOwnerEmail(String email) {
        return documentRepository.findByOwner_Email(email);
    }

    public Resource downloadFile(Document document)
            throws IOException {

        return storageService.loadAsResource(
                document.getStoragePath());
    }

    @Transactional
public void deleteDocument(Document document) throws IOException {
    String storedFileName = document.getStoragePath();

    // Move the file to quarantine before deleting database records.
    storageService.quarantine(storedFileName);

    // Restore the file if the database transaction fails.
    TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    try {
                        storageService.purgeQuarantined(storedFileName);
                    } catch (IOException exception) {
                        logger.error(
                                "Database deletion succeeded, but the quarantined file "
                                        + "could not be permanently removed: {}",
                                storedFileName,
                                exception
                        );
                    }
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try {
                            storageService.restoreFromQuarantine(
                                    storedFileName
                            );
                        } catch (IOException exception) {
                            logger.error(
                                    "Could not restore document after transaction "
                                            + "failure: {}",
                                    storedFileName,
                                    exception
                            );
                        }
                    }
                }
            }
    );

    // Remove sharing permissions and the database record.
    documentPermissionRepository.deleteByDocument_Id(document.getId());
    documentRepository.delete(document);
}
    private static final Logger logger =
        LoggerFactory.getLogger(DocumentService.class);
}
