
package com.irshad.trustvault.service;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class DocumentStorageService {

    @Value("${trustvault.storage.location}")
    private String storageLocation;

    private Path storageRoot;
    private Path quarantineRoot;

    @PostConstruct
    public void initialize() throws IOException {
        storageRoot = Paths.get(storageLocation)
                .toAbsolutePath()
                .normalize();

        quarantineRoot = storageRoot.resolve(".quarantine")
                .normalize();

        if (!quarantineRoot.startsWith(storageRoot)
                || quarantineRoot.equals(storageRoot)) {
            throw new IOException("Invalid quarantine directory.");
        }

        Files.createDirectories(storageRoot);
        Files.createDirectories(quarantineRoot);
    }

    public String store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please select a non-empty file.");
        }

        String storedFileName = UUID.randomUUID().toString();

        Path destination = storageRoot.resolve(storedFileName)
                .normalize();

        if (!destination.startsWith(storageRoot)
                || destination.equals(storageRoot)) {
            throw new IOException("Invalid storage path.");
        }

        file.transferTo(destination);

        return storedFileName;
    }

    /**
     * Stores a replacement document as a new file.
     * The existing document is left untouched.
     */
    public String storeReplacement(MultipartFile file)
            throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please select a non-empty replacement file.");
        }

        String storedFileName = UUID.randomUUID().toString();

        Path destination = storageRoot.resolve(storedFileName)
                .normalize();

        if (!destination.startsWith(storageRoot)
                || destination.equals(storageRoot)) {
            throw new IOException("Invalid storage path.");
        }

        try {
            file.transferTo(destination);
            return storedFileName;
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(destination);
            throw exception;
        }
    }


    public Resource loadAsResource(String storedFileName)
            throws IOException {

        validateStoredFileName(storedFileName);

        Path filePath = storageRoot.resolve(storedFileName)
                .normalize();

        if (!filePath.startsWith(storageRoot)
                || filePath.equals(storageRoot)
                || !Files.isRegularFile(filePath)
                || !Files.isReadable(filePath)) {
            throw new IOException(
                    "Stored document is unavailable.");
        }

        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            throw new IOException(
                    "Stored document is unavailable.");
        }

        return resource;
    }

    /**
     * Moves a stored document into quarantine.
     * The file can be restored if the database transaction fails.
     */
    public void quarantine(String storedFileName)
            throws IOException {

        validateStoredFileName(storedFileName);

        Path source = storageRoot.resolve(storedFileName)
                .normalize();

        Path quarantined = quarantineRoot.resolve(storedFileName)
                .normalize();

        validatePaths(source, quarantined);

        if (!Files.isRegularFile(source)) {
            throw new IOException(
                    "Stored document is unavailable.");
        }

        if (Files.exists(quarantined)) {
            throw new IOException(
                    "A quarantined file with this identifier already exists.");
        }

        moveFile(source, quarantined);
    }

    /**
     * Restores a quarantined file after a failed transaction.
     */
    public void restoreFromQuarantine(String storedFileName)
            throws IOException {

        validateStoredFileName(storedFileName);

        Path source = quarantineRoot.resolve(storedFileName)
                .normalize();

        Path destination = storageRoot.resolve(storedFileName)
                .normalize();

        validatePaths(destination, source);

        if (!Files.exists(source)) {
            return;
        }

        if (Files.exists(destination)) {
            throw new IOException(
                    "Cannot restore file: destination already exists.");
        }

        moveFile(source, destination);
    }

    /**
     * Permanently removes a file after the database transaction commits.
     */
    public void purgeQuarantined(String storedFileName)
            throws IOException {

        validateStoredFileName(storedFileName);

        Path quarantined = quarantineRoot.resolve(storedFileName)
                .normalize();

        validatePaths(
        storageRoot.resolve(storedFileName).normalize(),
        quarantined
);

        if (Files.exists(quarantined)
                && !Files.isRegularFile(quarantined)) {
            throw new IOException(
                    "Quarantined document is not a regular file.");
        }

        Files.deleteIfExists(quarantined);
    }

    /**
     * Removes a stored file after validating its identifier and path.
     */
    public void deleteStoredFile(String storedFileName)
            throws IOException {

        validateStoredFileName(storedFileName);

        Path filePath = storageRoot.resolve(storedFileName)
                .normalize();

        if (!filePath.startsWith(storageRoot)
                || filePath.equals(storageRoot)) {
            throw new IOException("Invalid storage path.");
        }

        if (Files.exists(filePath)
                && !Files.isRegularFile(filePath)) {
            throw new IOException(
                    "Stored document is not a regular file.");
        }

        Files.deleteIfExists(filePath);
    }

    private void validateStoredFileName(String storedFileName)
            throws IOException {

        if (storedFileName == null
                || !storedFileName.matches(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"
                        + "[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                        + "[0-9a-fA-F]{12}")) {
            throw new IOException(
                    "Invalid stored file identifier.");
        }
    }

   private void validatePaths(Path storagePath, Path quarantinePath)
        throws IOException {

    if (!storagePath.startsWith(storageRoot)
            || storagePath.equals(storageRoot)
            || !quarantinePath.startsWith(quarantineRoot)
            || quarantinePath.equals(quarantineRoot)) {
        throw new IOException("Invalid storage path.");
    }
}

    private void moveFile(Path source, Path destination)
            throws IOException {

        try {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }
}
