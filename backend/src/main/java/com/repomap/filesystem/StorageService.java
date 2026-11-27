package com.repomap.filesystem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import java.util.regex.Pattern;

import com.repomap.exception.BadRequestException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Manages the on-disk layout for imported repositories.
 *
 * <p>Every project lives in its own directory named by an opaque
 * {@code storageKey} under the configured root. Callers only ever handle the
 * key, so absolute server paths never leak into the API or the database.
 *
 * <p>Imports are extracted into a sibling {@code <key>.staging} directory and
 * only swapped into place once extraction succeeded, so a failed or malicious
 * upload can never corrupt an already-imported repository.
 */
@Service
public class StorageService {

    /** Storage keys are generated here, so a strict format also blocks traversal. */
    private static final Pattern STORAGE_KEY = Pattern.compile("[a-f0-9]{32}");

    private final Path root;

    public StorageService(@Value("${repomap.storage.root}") String rootLocation) {
        this.root = Path.of(rootLocation).toAbsolutePath().normalize();
    }

    public String newStorageKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * @throws BadRequestException when the key is malformed (traversal attempt)
     */
    public Path projectRoot(String storageKey) {
        String key = validate(storageKey);
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new BadRequestException("Invalid storage key");
        }
        return resolved;
    }

    /** Scratch directory used while an archive is being extracted. */
    public Path stagingDirectory(String storageKey) {
        Path projectRoot = projectRoot(storageKey);
        return projectRoot.resolveSibling(projectRoot.getFileName().toString() + ".staging");
    }

    public boolean projectExists(String storageKey) {
        return Files.isDirectory(projectRoot(storageKey));
    }

    public void ensureProjectDirectory(String storageKey) {
        createDirectories(projectRoot(storageKey));
    }

    /**
     * Atomically-ish replaces the project directory with its staging
     * counterpart. The old tree is only removed once staging is known good.
     */
    public void promoteStaging(String storageKey) {
        Path staging = stagingDirectory(storageKey);
        if (!Files.exists(staging)) {
            return;
        }
        Path projectRoot = projectRoot(storageKey);
        deleteTree(projectRoot);
        try {
            Files.move(staging, projectRoot, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            // Retry without ATOMIC_MOVE for filesystems that do not support it.
            try {
                Files.move(staging, projectRoot, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException fallback) {
                throw new UncheckedIOException("Could not finalise the imported project", fallback);
            }
        }
    }

    public void discardStaging(String storageKey) {
        deleteTree(stagingDirectory(storageKey));
    }

    /**
     * Recursively deletes a project directory, refusing to touch anything that
     * resolves outside the storage root. Symbolic links are never followed.
     */
    public void deleteProject(String storageKey) {
        deleteTree(projectRoot(storageKey));
    }

    private void deleteTree(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        Path normalized = directory.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new BadRequestException("Refusing to delete outside the storage root");
        }
        try {
            Files.walkFileTree(normalized, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not clean up project storage", ex);
        }
    }

    /** Total bytes stored for a project; used for inventory totals. */
    public long directorySize(Path directory) {
        if (!Files.isDirectory(directory)) {
            return 0L;
        }
        final long[] total = {0L};
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    total[0] += attrs.size();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return total[0];
    }

    private static String validate(String storageKey) {
        if (storageKey == null || !STORAGE_KEY.matcher(storageKey).matches()) {
            throw new BadRequestException("Invalid storage key");
        }
        return storageKey;
    }

    private void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not create the project storage directory", ex);
        }
    }

    public Path getRoot() {
        return root;
    }
}
