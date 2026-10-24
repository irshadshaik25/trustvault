package com.irshad.trustvault;

import com.irshad.trustvault.service.DocumentStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStorageServiceTests {

    private DocumentStorageService storageService;
    private Path temporaryDirectory;

    @BeforeEach
    void setUp() throws IOException {
        temporaryDirectory = Files.createTempDirectory("trustvault-test-");

        storageService = new DocumentStorageService();
        setStorageLocation(temporaryDirectory.toString());
        storageService.initialize();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (temporaryDirectory != null
                && Files.exists(temporaryDirectory)) {
            try (var paths = Files.walk(temporaryDirectory)) {
                paths.sorted((first, second) ->
                                second.compareTo(first))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException exception) {
                                throw new RuntimeException(exception);
                            }
                        });
            }
        }
    }

    @Test
    void storeAndLoadFileSuccessfully() throws IOException {
        byte[] content = "TrustVault storage test".getBytes();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.txt",
                "text/plain",
                content
        );

        String storedFileName = storageService.store(file);

        assertDoesNotThrow(() ->
                UUID.fromString(storedFileName));

        Resource resource =
                storageService.loadAsResource(storedFileName);

        assertTrue(resource.isReadable());

try (var inputStream = resource.getInputStream()) {
    assertArrayEquals(content, inputStream.readAllBytes());
}
    }

    @Test
    void storeRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> storageService.store(file)
        );
    }

    @Test
    void loadRejectsInvalidStoredFileName() {
        assertThrows(
                IOException.class,
                () -> storageService.loadAsResource("../outside.txt")
        );
    }

    @Test
    void quarantineAndRestorePreserveFile() throws IOException {
        String storedFileName = UUID.randomUUID().toString();
        Path storedFile = temporaryDirectory.resolve(storedFileName);
        byte[] content = "Restore test".getBytes();

        Files.write(storedFile, content);

        storageService.quarantine(storedFileName);

        assertFalse(Files.exists(storedFile));
        assertTrue(Files.exists(
                temporaryDirectory.resolve(".quarantine")
                        .resolve(storedFileName)));

        storageService.restoreFromQuarantine(storedFileName);

        assertTrue(Files.exists(storedFile));
        assertArrayEquals(content, Files.readAllBytes(storedFile));
    }

    @Test
    void purgeRemovesQuarantinedFile() throws IOException {
        String storedFileName = UUID.randomUUID().toString();
        Path quarantinedFile = temporaryDirectory.resolve(".quarantine")
                .resolve(storedFileName);

        Files.write(quarantinedFile, "Purge test".getBytes());

        storageService.purgeQuarantined(storedFileName);

        assertFalse(Files.exists(quarantinedFile));
    }

    @Test
    void deleteRemovesStoredFile() throws IOException {
        String storedFileName = UUID.randomUUID().toString();
        Path storedFile = temporaryDirectory.resolve(storedFileName);

        Files.write(storedFile, "Delete test".getBytes());

        storageService.deleteStoredFile(storedFileName);

        assertFalse(Files.exists(storedFile));
    }

    private void setStorageLocation(String location)
            throws IOException {
        try {
            var field = DocumentStorageService.class
                    .getDeclaredField("storageLocation");
            field.setAccessible(true);
            field.set(storageService, location);
        } catch (ReflectiveOperationException exception) {
            throw new IOException(
                    "Could not configure test storage directory.",
                    exception
            );
        }
    }
}