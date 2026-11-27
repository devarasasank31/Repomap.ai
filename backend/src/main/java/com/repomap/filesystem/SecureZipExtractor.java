package com.repomap.filesystem;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.repomap.exception.BadRequestException;
import com.repomap.ingestion.LanguageDetector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Extracts an uploaded ZIP into a project directory with defences against the
 * classic archive attacks:
 *
 * <ul>
 *   <li>path traversal ({@code ../}, absolute paths, Windows drive letters) →
 *       the whole archive is rejected</li>
 *   <li>decompression bombs → per-file, per-archive and entry-count budgets
 *       plus a compression-ratio check when sizes are declared</li>
 *   <li>generated trees ({@code node_modules}, {@code .git}, {@code target},
 *       …) → skipped so imports stay small</li>
 *   <li>credential files ({@code .env}, {@code *.pem}, {@code id_rsa}, …) →
 *       never written to disk</li>
 *   <li>binaries / unknown formats → skipped, they are not analysable</li>
 * </ul>
 *
 * The extractor never executes archive contents and never follows symbolic
 * links; every written file is verified to stay inside the target directory.
 */
@Component
public class SecureZipExtractor {

    private static final Logger log = LoggerFactory.getLogger(SecureZipExtractor.class);

    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", ".svn", ".hg", "node_modules", "target", "build", "dist", "out", "bin", "obj",
            ".idea", ".vscode", ".vs", ".gradle", ".mvn", ".next", ".nuxt", ".cache", "coverage",
            "__pycache__", "venv", ".venv", "vendor", "site-packages", ".settings", ".terraform");

    private static final int BUFFER = 8 * 1024;

    private final IngestionLimits limits;

    public SecureZipExtractor(IngestionLimits limits) {
        this.limits = limits;
    }

    public record ExtractionResult(
            int extractedFiles,
            long extractedBytes,
            int skippedUnsupported,
            int skippedOversized,
            int skippedExcluded,
            int skippedSecrets,
            int scannedEntries) {
    }

    /**
     * @throws BadRequestException when the archive is malformed or attacks the
     *         filesystem — the caller must treat the upload as rejected
     */
    public ExtractionResult extract(InputStream archive, Path targetDirectory) {
        final Path target;
        try {
            target = targetDirectory.toAbsolutePath().normalize();
            Files.createDirectories(target);
        } catch (IOException ex) {
            log.error("Could not create the project directory: {}", ex.getMessage());
            throw new BadRequestException("Could not prepare the project directory");
        }

        Counters counters = new Counters();

        try (ZipInputStream zip = new ZipInputStream(checkZipSignature(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                counters.scanned++;
                if (counters.scanned > limits.maxEntriesScanned()) {
                    throw new BadRequestException("Archive rejected: too many entries");
                }

                String entryName = sanitize(entry.getName());
                if (entryName.isEmpty() || entryName.endsWith("/")) {
                    continue; // directories are created implicitly
                }

                if (isExcluded(entryName)) {
                    counters.skippedExcluded++;
                    continue;
                }

                String filename = entryName.substring(entryName.lastIndexOf('/') + 1);
                if (LanguageDetector.isSecretLooking(filename)) {
                    counters.skippedSecrets++;
                    continue;
                }
                if (LanguageDetector.languageFor(filename).isEmpty()) {
                    counters.skippedUnsupported++;
                    continue;
                }
                if (entry.getSize() > limits.maxSingleFileBytes()) {
                    counters.skippedOversized++;
                    continue;
                }

                Path destination = resolveInside(target, entryName);
                if (Files.isSymbolicLink(destination)
                        || (destination.getParent() != null && Files.isSymbolicLink(destination.getParent()))) {
                    throw new BadRequestException("Archive rejected: symbolic link entry");
                }

                counters.write(zip, entry, destination, limits);
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (IOException ex) {
            log.warn("Archive extraction failed: {}", ex.getMessage());
            throw new BadRequestException("Archive rejected: the file is not a valid ZIP archive");
        }

        if (counters.extracted == 0) {
            throw new BadRequestException("Archive rejected: it contains no analysable source files");
        }

        return new ExtractionResult(counters.extracted, counters.totalBytes, counters.skippedUnsupported,
                counters.skippedOversized, counters.skippedExcluded, counters.skippedSecrets, counters.scanned);
    }

    private static final class Counters {
        int extracted;
        int skippedUnsupported;
        int skippedOversized;
        int skippedExcluded;
        int skippedSecrets;
        int scanned;
        long totalBytes;

        void write(ZipInputStream zip, ZipEntry entry, Path destination, IngestionLimits limits)
                throws IOException {
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            long entryBytes = 0;
            boolean discarded = false;

            try (OutputStream out = Files.newOutputStream(destination,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[BUFFER];
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    entryBytes += read;
                    if (entryBytes > limits.maxSingleFileBytes()) {
                        discarded = true;
                    } else if (!discarded) {
                        out.write(buffer, 0, read);
                    }
                }
            }

            if (discarded) {
                Files.deleteIfExists(destination);
                skippedOversized++;
                return;
            }

            // Declared uncompressed/compressed sizes let us spot a bomb early.
            long declaredSize = entry.getSize();
            long declaredCompressed = entry.getCompressedSize();
            if (declaredSize > 0 && declaredCompressed > 0
                    && (double) declaredSize / declaredCompressed > limits.maxCompressionRatio()) {
                Files.deleteIfExists(destination);
                throw new BadRequestException("Archive rejected: suspicious compression ratio");
            }

            totalBytes += entryBytes;
            if (totalBytes > limits.maxExtractedBytes()) {
                throw new BadRequestException("Archive rejected: expanded size exceeds the allowed budget");
            }
            if (entryBytes > 0) {
                extracted++;
            }
            if (extracted > limits.maxExtractedFiles()) {
                throw new BadRequestException("Archive rejected: too many files");
            }
        }
    }

    /**
     * Verifies the ZIP magic bytes up-front so callers get a precise error
     * instead of "no analysable files" when they upload something else.
     */
    private static InputStream checkZipSignature(InputStream archive) throws IOException {
        java.io.BufferedInputStream buffered =
                new java.io.BufferedInputStream(archive, 8192);
        buffered.mark(4);
        byte[] signature = buffered.readNBytes(4);
        boolean looksLikeZip = signature.length == 4
                && signature[0] == 'P' && signature[1] == 'K'
                && (signature[2] == 3 || signature[2] == 5 || signature[2] == 7);
        if (!looksLikeZip) {
            throw new BadRequestException("Archive rejected: the file is not a valid ZIP archive");
        }
        buffered.reset();
        return buffered;
    }

    /**
     * Normalises an entry name and rejects anything that could escape the
     * target directory.
     *
     * @throws BadRequestException on traversal or absolute paths
     */
    static String sanitize(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new BadRequestException("Archive rejected: empty entry name");
        }
        String name = rawName.replace('\\', '/');
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        if (name.isEmpty()) {
            return "";
        }
        if (name.startsWith("/")) {
            throw new BadRequestException("Archive rejected: absolute entry path");
        }
        if (name.length() >= 2 && Character.isLetter(name.charAt(0)) && name.charAt(1) == ':') {
            throw new BadRequestException("Archive rejected: drive-letter entry path");
        }
        if (name.indexOf('\0') >= 0) {
            throw new BadRequestException("Archive rejected: invalid entry name");
        }
        for (String segment : name.split("/")) {
            if (segment.equals("..")) {
                throw new BadRequestException("Archive rejected: path traversal entry");
            }
        }
        return name;
    }

    private Path resolveInside(Path target, String entryName) {
        Path resolved = target.resolve(entryName.replace('/', File.separatorChar)).normalize();
        if (!resolved.startsWith(target)) {
            throw new BadRequestException("Archive rejected: entry escapes the project directory");
        }
        return resolved;
    }

    /**
     * Returns true when any directory segment of the entry belongs to a
     * generated tree such as {@code node_modules} or {@code .git}. The final
     * segment is the file name itself, so a file called {@code build} at the
     * repository root is still kept.
     */
    private static boolean isExcluded(String entryName) {
        String[] segments = entryName.split("/");
        for (int i = 0; i < segments.length - 1; i++) {
            if (EXCLUDED_DIRECTORIES.contains(segments[i].toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
