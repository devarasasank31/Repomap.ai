package com.repomap.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.repomap.exception.BadRequestException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Security and robustness tests for archive extraction. Every case here is an
 * attack or a malformed input that must never touch the filesystem outside the
 * project directory.
 */
class SecureZipExtractorTest {

    @TempDir
    Path tempDir;

    private Path target;
    private SecureZipExtractor extractor;

    @BeforeEach
    void setUp() {
        target = tempDir.resolve("project");
        extractor = new SecureZipExtractor(new IngestionLimits(
                10 * 1024 * 1024, 100, 10 * 1024 * 1024, 1024 * 1024, 1000, 100));
    }

    private static byte[] zip(String... entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (String entry : entries) {
                String[] parts = entry.split("=", 2);
                zip.putNextEntry(new ZipEntry(parts[0]));
                if (parts.length > 1) {
                    zip.write(parts[1].getBytes(StandardCharsets.UTF_8));
                }
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        return out.toByteArray();
    }

    private SecureZipExtractor.ExtractionResult extract(byte[] archive) {
        return extractor.extract(new ByteArrayInputStream(archive), target);
    }

    @Test
    void extractsAPlainJavaProject() throws Exception {
        byte[] archive = zip(
                "pom.xml=<project/>",
                "src/main/java/com/example/App.java=package com.example; public class App {}");

        SecureZipExtractor.ExtractionResult result = extract(archive);

        assertThat(result.extractedFiles()).isEqualTo(2);
        assertThat(Files.readString(target.resolve("src/main/java/com/example/App.java")))
                .contains("class App");
        assertThat(Files.readString(target.resolve("pom.xml"))).contains("<project/>");
    }

    @Test
    void rejectsPathTraversalEntry() {
        byte[] archive = zip("../evil.txt=boom");

        assertThatThrownBy(() -> extract(archive))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("traversal");

        assertThat(Files.exists(tempDir.resolve("evil.txt"))).isFalse();
    }

    @Test
    void rejectsWindowsStylePathTraversal() {
        byte[] archive = zip("..\\evil.txt=boom");

        assertThatThrownBy(() -> extract(archive))
                .isInstanceOf(BadRequestException.class);
        assertThat(Files.exists(tempDir.resolve("evil.txt"))).isFalse();
    }

    @Test
    void rejectsAbsolutePathEntry() {
        byte[] archive = zip("/etc/passwd=root:x:0:0");

        assertThatThrownBy(() -> extract(archive))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("absolute");
    }

    @Test
    void rejectsNestedTraversal() {
        byte[] archive = zip("src/main/../../../outside.java=boom");

        assertThatThrownBy(() -> extract(archive))
                .isInstanceOf(BadRequestException.class);
        assertThat(Files.exists(tempDir.resolve("outside.java"))).isFalse();
    }

    @Test
    void rejectsDriveLetterEntry() {
        byte[] archive = zip("C:/windows/system32/evil.dll=boom");

        assertThatThrownBy(() -> extract(archive))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("drive-letter");
    }

    @Test
    void skipsGeneratedDirectories() throws Exception {
        byte[] archive = zip(
                "node_modules/dep/index.js=module.exports = 1;",
                ".git/config=[core]",
                "src/App.tsx=export const App = () => null;",
                "target/classes/App.class=cafebabe");

        SecureZipExtractor.ExtractionResult result = extract(archive);

        assertThat(result.extractedFiles()).isEqualTo(1);
        assertThat(result.skippedExcluded()).isEqualTo(3);
        assertThat(Files.exists(target.resolve("node_modules/dep/index.js"))).isFalse();
        assertThat(Files.exists(target.resolve(".git/config"))).isFalse();
        assertThat(Files.exists(target.resolve("src/App.tsx"))).isTrue();
    }

    @Test
    void skipsUnsupportedAndSecretFiles() throws Exception {
        byte[] archive = zip(
                "logo.png=notreallyapng",
                ".env=SECRET_KEY=abc",
                "id_rsa=-----BEGIN PRIVATE KEY-----",
                "server.pem=-----BEGIN CERTIFICATE-----",
                "src/App.java=package x;");

        SecureZipExtractor.ExtractionResult result = extract(archive);

        assertThat(result.extractedFiles()).isEqualTo(1);
        assertThat(result.skippedSecrets()).isEqualTo(3);
        assertThat(result.skippedUnsupported()).isEqualTo(1);
        assertThat(Files.exists(target.resolve(".env"))).isFalse();
        assertThat(Files.exists(target.resolve("id_rsa"))).isFalse();
        assertThat(Files.exists(target.resolve("server.pem"))).isFalse();
    }

    @Test
    void skipsFilesLargerThanTheSingleFileBudget() throws Exception {
        SecureZipExtractor tight = new SecureZipExtractor(new IngestionLimits(
                10 * 1024 * 1024, 100, 10 * 1024 * 1024, 16, 1000, 100));
        String big = "x".repeat(500);
        byte[] archive = zip("big.java=" + big, "small.java=class A {}");

        SecureZipExtractor.ExtractionResult result =
                tight.extract(new ByteArrayInputStream(archive), target);

        assertThat(result.extractedFiles()).isEqualTo(1);
        assertThat(result.skippedOversized()).isEqualTo(1);
        assertThat(Files.exists(target.resolve("big.java"))).isFalse();
        assertThat(Files.exists(target.resolve("small.java"))).isTrue();
    }

    @Test
    void enforcesTheExtractedFileCountBudget() {
        SecureZipExtractor limited = new SecureZipExtractor(new IngestionLimits(
                10 * 1024 * 1024, 2, 10 * 1024 * 1024, 1024 * 1024, 1000, 100));
        byte[] archive = zip("A.java=class A {}", "B.java=class B {}", "C.java=class C {}");

        assertThatThrownBy(() -> limited.extract(new ByteArrayInputStream(archive), target))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("too many files");
    }

    @Test
    void rejectsArchiveWithNoAnalysableFiles() {
        byte[] archive = zip("node_modules/pkg/index.js=module.exports = {};");

        assertThatThrownBy(() -> extract(archive))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no analysable source files");
    }

    @Test
    void rejectsGarbageThatIsNotAZip() {
        byte[] garbage = "this is definitely not a zip archive".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> extract(garbage))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not a valid ZIP");
    }

    @Test
    void supportsUnicodeFileNames() throws Exception {
        byte[] archive = zip("src/日本語/App.java=package x;");

        SecureZipExtractor.ExtractionResult result = extract(archive);

        assertThat(result.extractedFiles()).isEqualTo(1);
        assertThat(Files.exists(target.resolve("src").resolve("日本語").resolve("App.java"))).isTrue();
    }

    @Test
    void sanitizeRejectsEveryShapeOfTraversal() {
        assertThatThrownBy(() -> SecureZipExtractor.sanitize("../a")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SecureZipExtractor.sanitize("a/../../b")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SecureZipExtractor.sanitize("/abs/path")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SecureZipExtractor.sanitize("C:/win")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SecureZipExtractor.sanitize("bad\0name")).isInstanceOf(BadRequestException.class);
        assertThat(SecureZipExtractor.sanitize("./src/A.java")).isEqualTo("src/A.java");
        assertThat(SecureZipExtractor.sanitize("src\\A.java")).isEqualTo("src/A.java");
    }
}
