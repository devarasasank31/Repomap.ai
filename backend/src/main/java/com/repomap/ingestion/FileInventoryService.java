package com.repomap.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.repomap.filesystem.IngestionLimits;
import com.repomap.project.Project;
import com.repomap.project.ProjectFile;
import com.repomap.project.ProjectFileRepository;
import com.repomap.project.ProjectRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Walks an extracted repository and turns it into {@link ProjectFile} records.
 * Symlinks, unsupported formats and oversized files are skipped rather than
 * failing the import.
 */
@Service
public class FileInventoryService {

    private static final int PERSIST_CHUNK = 500;

    private final ProjectFileRepository projectFileRepository;
    private final ProjectRepository projectRepository;
    private final IngestionLimits limits;

    public FileInventoryService(
            ProjectFileRepository projectFileRepository,
            ProjectRepository projectRepository,
            IngestionLimits limits) {
        this.projectFileRepository = projectFileRepository;
        this.projectRepository = projectRepository;
        this.limits = limits;
    }

    public record Inventory(int fileCount, long totalBytes, int javaFileCount, int skipped) {
    }

    /** Replaces the inventory of a project with the current on-disk tree. */
    @Transactional
    public Inventory rebuild(Long projectId, Path root) throws IOException {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project disappeared during import"));

        projectFileRepository.deleteAllForProject(projectId);

        List<ProjectFile> collected = new ArrayList<>();
        int skipped = 0;

        if (Files.isDirectory(root)) {
            try (var stream = Files.walk(root)) {
                for (Path path : (Iterable<Path>) stream::iterator) {
                    if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
                        continue;
                    }
                    String relative = toPosix(root, path);
                    if (relative == null) {
                        skipped++;
                        continue;
                    }
                    String filename = path.getFileName().toString();
                    Optional<String> language = LanguageDetector.languageFor(filename);
                    if (language.isEmpty()) {
                        skipped++;
                        continue;
                    }
                    long size = Files.size(path);
                    if (size > limits.maxSingleFileBytes()) {
                        skipped++;
                        continue;
                    }
                    String directory = relative.contains("/")
                            ? relative.substring(0, relative.lastIndexOf('/'))
                            : "";
                    collected.add(new ProjectFile(
                            project,
                            relative,
                            directory,
                            filename,
                            LanguageDetector.extensionOf(filename),
                            language.get(),
                            size,
                            countLines(path)));
                }
            }
        }

        for (int i = 0; i < collected.size(); i += PERSIST_CHUNK) {
            projectFileRepository.saveAll(collected.subList(i, Math.min(i + PERSIST_CHUNK, collected.size())));
        }

        long totalBytes = collected.stream().mapToLong(ProjectFile::getSizeBytes).sum();
        int javaFiles = (int) collected.stream().filter(f -> "java".equals(f.getLanguage())).count();

        project.setFileCount(collected.size());
        project.setTotalSizeBytes(totalBytes);
        project.setJavaFileCount(javaFiles);

        return new Inventory(collected.size(), totalBytes, javaFiles, skipped);
    }

    static String toPosix(Path root, Path file) {
        try {
            Path relative = root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize());
            String value = relative.toString().replace('\\', '/');
            if (value.isEmpty() || value.startsWith("..") || value.startsWith("/")) {
                return null;
            }
            return value;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static int countLines(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) {
                return 0;
            }
            int lines = 1;
            for (byte b : bytes) {
                if (b == '\n') {
                    lines++;
                }
            }
            // A trailing newline does not start a new line.
            if (bytes[bytes.length - 1] == '\n') {
                lines--;
            }
            return lines;
        } catch (IOException ex) {
            return 0;
        }
    }
}
