package com.repomap.ingestion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.repomap.exception.BadRequestException;
import com.repomap.exception.ResourceNotFoundException;
import com.repomap.filesystem.IngestionLimits;
import com.repomap.filesystem.SecureZipExtractor;
import com.repomap.filesystem.StorageService;
import com.repomap.project.Project;
import com.repomap.project.ProjectFile;
import com.repomap.project.ProjectFileRepository;
import com.repomap.project.ProjectService;
import com.repomap.project.ProjectStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Orchestrates a repository import: validate the upload, extract it into a
 * staging directory, swap it into place, then build the file inventory.
 *
 * <p>Nothing in the archive is ever executed; only bytes are written to disk.
 * On any failure the staging area is dropped, the previous import (if any)
 * survives untouched, and the project is marked {@code IMPORT_FAILED}.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    /** API responses are capped so a huge source file cannot flood the client. */
    private static final int MAX_SOURCE_CHARS = 262_144;

    private final SecureZipExtractor extractor;
    private final FileInventoryService inventoryService;
    private final ProjectService projectService;
    private final ProjectFileRepository projectFileRepository;
    private final StorageService storageService;
    private final IngestionLimits limits;

    public IngestionService(
            SecureZipExtractor extractor,
            FileInventoryService inventoryService,
            ProjectService projectService,
            ProjectFileRepository projectFileRepository,
            StorageService storageService,
            IngestionLimits limits) {
        this.extractor = extractor;
        this.inventoryService = inventoryService;
        this.projectService = projectService;
        this.projectFileRepository = projectFileRepository;
        this.storageService = storageService;
        this.limits = limits;
    }

    public record ImportResult(
            Project project,
            SecureZipExtractor.ExtractionResult extraction,
            FileInventoryService.Inventory inventory) {
    }

    /** Creates a project and imports the uploaded archive into it. */
    public ImportResult importArchive(Long ownerId, MultipartFile file, String name, String description) {
        String projectName = (name == null || name.isBlank()) ? defaultProjectName(file) : name.trim();
        Project created = projectService.createInternal(ownerId, projectName, description);
        return runImport(ownerId, created, file);
    }

    /** Re-imports an archive into an already existing, owned project. */
    public ImportResult importIntoExisting(Long projectId, Long ownerId, MultipartFile file) {
        Project project = projectService.requireOwned(projectId, ownerId);
        return runImport(ownerId, project, file);
    }

    private ImportResult runImport(Long ownerId, Project project, MultipartFile file) {
        validateUpload(file);
        String storageKey = project.getStorageKey();

        storageService.discardStaging(storageKey);

        try {
            Project managed = projectService.loadOwned(project.getId(), ownerId);
            managed.transitionTo(ProjectStatus.IMPORTING);
            managed.setSourceFilename(sanitizeFilename(file.getOriginalFilename()));
            managed.setErrorMessage(null);
            projectService.save(managed);

            SecureZipExtractor.ExtractionResult extraction = extractor.extract(file.getInputStream(),
                    storageService.stagingDirectory(storageKey));
            storageService.promoteStaging(storageKey);

            Path root = storageService.projectRoot(storageKey);
            FileInventoryService.Inventory inventory = inventoryService.rebuild(managed.getId(), root);

            // The inventory ran in its own transaction, so reload before the
            // final status update instead of merging stale field values.
            Project finished = projectService.loadOwned(project.getId(), ownerId);
            finished.transitionTo(ProjectStatus.IMPORTED);
            finished.setErrorMessage(null);
            projectService.save(finished);

            log.info("Imported project {} ({} files, {} bytes, {} entries scanned)",
                    finished.getId(), inventory.fileCount(), inventory.totalBytes(), extraction.scannedEntries());
            return new ImportResult(finished, extraction, inventory);

        } catch (BadRequestException ex) {
            storageService.discardStaging(storageKey);
            fail(project, ownerId, safeMessage(ex));
            throw ex;
        } catch (UncheckedIOException ex) {
            storageService.discardStaging(storageKey);
            fail(project, ownerId, safeMessage(ex));
            throw new BadRequestException(safeMessage(ex));
        } catch (IOException | RuntimeException ex) {
            storageService.discardStaging(storageKey);
            log.error("Import of project {} failed: {}", project.getId(), ex.toString());
            String message = "Import failed: unexpected error while reading the archive";
            fail(project, ownerId, message);
            throw new BadRequestException(message);
        }
    }

    private void fail(Project project, Long ownerId, String message) {
        try {
            Project managed = projectService.loadOwned(project.getId(), ownerId);
            managed.transitionTo(ProjectStatus.IMPORT_FAILED);
            managed.setErrorMessage(truncate(message, 2000));
            projectService.save(managed);
        } catch (RuntimeException ex) {
            log.error("Could not persist the failed import status: {}", ex.getMessage());
        }
    }

    /**
     * Error text is kept generic on purpose: exception messages from the
     * filesystem layer can contain absolute server paths.
     */
    private static String safeMessage(Exception ex) {
        if (ex instanceof BadRequestException) {
            return truncate(ex.getMessage(), 2000);
        }
        if (ex instanceof UncheckedIOException) {
            return "Import failed: the archive could not be written to storage";
        }
        return "Import failed: unexpected error while reading the archive";
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No archive uploaded");
        }
        if (file.getSize() > limits.maxUploadBytes()) {
            throw new BadRequestException(
                    "Archive exceeds the maximum upload size of " + limits.maxUploadBytes() + " bytes");
        }
    }

    private static String defaultProjectName(MultipartFile file) {
        String filename = file == null ? null : file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            return "imported-repository";
        }
        String base = sanitizeFilename(filename);
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return base.isBlank() || base.equals(".") ? "imported-repository" : truncate(base, 200);
    }

    /** Strips directory components and control characters from a client name. */
    static String sanitizeFilename(String original) {
        if (original == null) {
            return null;
        }
        String value = original.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        value = value.replaceAll("[\\p{Cntrl}]", "").trim();
        return truncate(value, 255);
    }

    /** Reads a single source file, resolving it strictly inside the project. */
    public SourceContent readSource(Long projectId, Long ownerId, String relativePath) {
        Project project = projectService.requireOwned(projectId, ownerId);
        String path = relativePath == null ? "" : relativePath.replace('\\', '/').trim();
        if (path.isEmpty()) {
            throw new BadRequestException("Missing file path");
        }

        Path root = storageService.projectRoot(project.getStorageKey());
        Path resolved = root.resolve(path.replace('/', java.io.File.separatorChar)).normalize();
        if (!resolved.startsWith(root)) {
            throw new BadRequestException("Invalid file path");
        }
        if (!Files.isRegularFile(resolved) || Files.isSymbolicLink(resolved)) {
            throw new ResourceNotFoundException("File not found");
        }

        ProjectFile metadata = projectFileRepository.findByProjectIdAndPath(projectId, path).orElse(null);
        String language = metadata != null ? metadata.getLanguage()
                : LanguageDetector.languageFor(resolved.getFileName().toString()).orElse(null);

        try {
            byte[] bytes = Files.readAllBytes(resolved);
            boolean truncated = bytes.length > MAX_SOURCE_CHARS;
            String content = new String(bytes, 0, Math.min(bytes.length, MAX_SOURCE_CHARS), StandardCharsets.UTF_8);
            int lineCount = metadata != null ? metadata.getLineCount() : FileInventoryService.countLines(resolved);
            return new SourceContent(path, content, lineCount, language, truncated, bytes.length);
        } catch (IOException ex) {
            throw new ResourceNotFoundException("File not found");
        }
    }

    public record SourceContent(
            String path, String content, int lineCount, String language, boolean truncated, long sizeBytes) {
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
