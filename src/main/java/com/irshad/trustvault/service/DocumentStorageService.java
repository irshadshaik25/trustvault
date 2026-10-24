package com.irshad.trustvault.service;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentStorageService {

    @Value("${trustvault.storage.location}")
    private String storageLocation;

    @Value("${trustvault.storage.provider:local}")
    private String storageProvider;

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.secret-key:}")
    private String supabaseSecretKey;

    @Value("${supabase.bucket:trustvault-documents}")
    private String supabaseBucket;

    private Path storageRoot;
    private Path quarantineRoot;
    private RestClient supabaseClient;
    private boolean useSupabase;

    @PostConstruct
    public void initialize() throws IOException {
        String provider = storageProvider == null
                ? "local"
                : storageProvider.trim().toLowerCase();

        if ("supabase".equals(provider)) {
            initializeSupabase();
            useSupabase = true;
            return;
        }

        if (!"local".equals(provider)) {
            throw new IOException(
                    "Unsupported document storage provider.");
        }

        useSupabase = false;

        storageRoot = Paths.get(storageLocation)
                .toAbsolutePath()
                .normalize();

        quarantineRoot = storageRoot.resolve(".quarantine")
                .normalize();

        if (!quarantineRoot.startsWith(storageRoot)
                || quarantineRoot.equals(storageRoot)) {
            throw new IOException(
                    "Invalid quarantine directory.");
        }

        Files.createDirectories(storageRoot);
        Files.createDirectories(quarantineRoot);
    }

    private void initializeSupabase() throws IOException {
        if (supabaseUrl == null
                || supabaseUrl.isBlank()
                || supabaseSecretKey == null
                || supabaseSecretKey.isBlank()
                || supabaseBucket == null
                || !supabaseBucket.matches(
                        "[a-z0-9][a-z0-9_-]*")) {
            throw new IOException(
                    "Supabase storage configuration is incomplete.");
        }

        supabaseUrl = supabaseUrl.trim().replaceAll("/+$", "");
        supabaseSecretKey = supabaseSecretKey.trim();
        supabaseBucket = supabaseBucket.trim();

        if (!supabaseUrl.startsWith("https://")) {
            throw new IOException(
                    "Supabase URL must use HTTPS.");
        }

        supabaseClient = RestClient.builder()
                .defaultHeader("apikey", supabaseSecretKey)
                .build();
    }

    public String store(MultipartFile file) throws IOException {
        validateUpload(file, "Please select a non-empty file.");

        String storedFileName = UUID.randomUUID().toString();

        if (useSupabase) {
            uploadRemote(storedFileName, file);
        } else {
            Path destination = storageRoot.resolve(storedFileName)
                    .normalize();

            validateLocalPath(destination);
            file.transferTo(destination);
        }

        return storedFileName;
    }

    public String storeReplacement(MultipartFile file)
            throws IOException {

        validateUpload(
                file,
                "Please select a non-empty replacement file.");

        String storedFileName = UUID.randomUUID().toString();

        try {
            if (useSupabase) {
                uploadRemote(storedFileName, file);
            } else {
                Path destination = storageRoot.resolve(storedFileName)
                        .normalize();

                validateLocalPath(destination);
                file.transferTo(destination);
            }

            return storedFileName;
        } catch (IOException | RuntimeException exception) {
            try {
                deleteStoredFile(storedFileName);
            } catch (IOException | RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }

            throw exception;
        }
    }

    public Resource loadAsResource(String storedFileName)
            throws IOException {

        validateStoredFileName(storedFileName);

        if (useSupabase) {
            try {
                byte[] content = supabaseClient.get()
                        .uri(authenticatedObjectUrl(storedFileName))
                        .retrieve()
                        .body(byte[].class);

                if (content == null) {
                    throw new IOException(
                            "Stored document is unavailable.");
                }

                return new ByteArrayResource(content);
            } catch (RestClientException exception) {
                throw new IOException(
                        "Could not download document from Supabase.",
                        exception);
            }
        }

        Path filePath = storageRoot.resolve(storedFileName)
                .normalize();

        validateLocalPath(filePath);

        if (!Files.isRegularFile(filePath)
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

    public void quarantine(String storedFileName)
            throws IOException {

        validateStoredFileName(storedFileName);

        if (useSupabase) {
            moveRemote(
                    storedFileName,
                    quarantineKey(storedFileName));
            return;
        }

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
                    "A quarantined file with this identifier "
                            + "already exists.");
        }

        moveFile(source, quarantined);
    }

    public void restoreFromQuarantine(String storedFileName)
            throws IOException {

        validateStoredFileName(storedFileName);

        if (useSupabase) {
            moveRemote(
                    quarantineKey(storedFileName),
                    storedFileName);
            return;
        }

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

    public void purgeQuarantined(String storedFileName)
            throws IOException {

        validateStoredFileName(storedFileName);

        if (useSupabase) {
            deleteRemoteObject(quarantineKey(storedFileName));
            return;
        }

        Path quarantined = quarantineRoot.resolve(storedFileName)
                .normalize();

        validatePaths(
                storageRoot.resolve(storedFileName).normalize(),
                quarantined);

        if (Files.exists(quarantined)
                && !Files.isRegularFile(quarantined)) {
            throw new IOException(
                    "Quarantined document is not a regular file.");
        }

        Files.deleteIfExists(quarantined);
    }

    public void deleteStoredFile(String storedFileName)
            throws IOException {

        validateStoredFileName(storedFileName);

        if (useSupabase) {
            deleteRemoteObject(storedFileName);
            return;
        }

        Path filePath = storageRoot.resolve(storedFileName)
                .normalize();

        validateLocalPath(filePath);

        if (Files.exists(filePath)
                && !Files.isRegularFile(filePath)) {
            throw new IOException(
                    "Stored document is not a regular file.");
        }

        Files.deleteIfExists(filePath);
    }

    private void uploadRemote(
            String storedFileName,
            MultipartFile file) throws IOException {

        String contentType = file.getContentType();

        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        MediaType mediaType;

        try {
            mediaType = MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        try {
            supabaseClient.post()
                    .uri(objectUrl(storedFileName))
                    .contentType(mediaType)
                    .header("x-upsert", "false")
                    .body(file.getBytes())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IOException(
                    "Could not upload document to Supabase.",
                    exception);
        }
    }

    private void moveRemote(
            String sourceKey,
            String destinationKey) throws IOException {

        Map<String, String> request = Map.of(
                "bucketId", supabaseBucket,
                "sourceKey", sourceKey,
                "destinationKey", destinationKey);

        try {
            supabaseClient.post()
                    .uri(supabaseUrl + "/storage/v1/object/move")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IOException(
                    "Could not move document in Supabase storage.",
                    exception);
        }
    }

    private void deleteRemoteObject(String objectKey)
            throws IOException {

        try {
            supabaseClient.method(HttpMethod.DELETE)
                    .uri(supabaseUrl
                            + "/storage/v1/object/"
                            + supabaseBucket)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("prefixes", List.of(objectKey)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IOException(
                    "Could not delete document from Supabase storage.",
                    exception);
        }
    }

    private String objectUrl(String objectKey) {
        return supabaseUrl
                + "/storage/v1/object/"
                + supabaseBucket
                + "/"
                + objectKey;
    }

    private String authenticatedObjectUrl(String objectKey) {
        return supabaseUrl
                + "/storage/v1/object/authenticated/"
                + supabaseBucket
                + "/"
                + objectKey;
    }

    private String quarantineKey(String storedFileName) {
        return ".quarantine/" + storedFileName;
    }

    private void validateUpload(
            MultipartFile file,
            String message) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
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

    private void validateLocalPath(Path path)
            throws IOException {

        if (!path.startsWith(storageRoot)
                || path.equals(storageRoot)) {
            throw new IOException("Invalid storage path.");
        }
    }

    private void validatePaths(
            Path storagePath,
            Path quarantinePath) throws IOException {

        validateLocalPath(storagePath);

        if (!quarantinePath.startsWith(quarantineRoot)
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