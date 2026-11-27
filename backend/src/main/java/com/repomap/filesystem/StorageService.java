package com.repomap.filesystem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
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
        if (storageKey == null || !STORAGE_KEY.matcher(storageKey).matches()) {
            throw new BadRequestException("Invalid storage key");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new BadRequestException("Invalid storage key");
        }
        return resolved;
    }

    public boolean projectExists(String storageKey) {
        return Files.isDirectory(projectRoot(storageKey));
    }

    public void ensureProjectDirectory(String storageKey) {
        try {
            Files.createDirectories(projectRoot(storageKey));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not create the project storage directory", ex);
        }
    }

    /**
     * Recursively deletes a project directory, refusing to touch anything that
     * resolves outside the storage root. Symbolic links are never followed.
     */
    public void deleteProject(String storageKey) {
        Path projectRoot = projectRoot(storageKey);
        if (!Files.exists(projectRoot)) {
            return;
        }
        try {
            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
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
                    if (!dir.equals(projectRoot) && !dir.startsWith(root)) {
                        return FileVisitResult.TERMINATE;
                    }
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

    public Path getRoot() {
        return root;
    }
}
