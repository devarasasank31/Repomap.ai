package com.repomap.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.repomap.exception.BadRequestException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageServiceTest {

    @TempDir
    Path tempDir;

    private StorageService service() {
        return new StorageService(tempDir.toString());
    }

    @Test
    void generatesWellFormedStorageKeys() {
        String key = service().newStorageKey();
        assertThat(key).matches("[a-f0-9]{32}");
    }

    @Test
    void resolvesProjectRootInsideStorageRoot() {
        StorageService service = service();
        Path root = service.projectRoot(service.newStorageKey());
        assertThat(root.toAbsolutePath().normalize().toString())
                .startsWith(tempDir.toAbsolutePath().normalize().toString());
        assertThat(root.getFileName().toString()).matches("[a-f0-9]{32}");
    }

    @Test
    void rejectsPathTraversalInStorageKey() {
        StorageService service = service();
        assertThatThrownBy(() -> service.projectRoot("../../windows/system32"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.projectRoot("/etc/passwd")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.projectRoot("")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.projectRoot(null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void deleteRemovesProjectDirectoryOnly() throws IOException {
        StorageService service = service();
        String key = service.newStorageKey();
        Path projectRoot = service.projectRoot(key);
        Files.createDirectories(projectRoot.resolve("src/main/java"));
        Files.writeString(projectRoot.resolve("src/main/java/App.java"), "class App {}");

        Path outside = tempDir.resolve("other-project");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("keep.txt"), "important");

        service.deleteProject(key);

        assertThat(Files.exists(projectRoot)).isFalse();
        assertThat(Files.exists(outside.resolve("keep.txt"))).isTrue();
    }

    @Test
    void deleteIsIdempotentForMissingProject() {
        StorageService service = service();
        service.deleteProject(service.newStorageKey());
        // no exception == pass
    }

    @Test
    void directorySizeSumsFiles() throws IOException {
        StorageService service = service();
        String key = service.newStorageKey();
        Path projectRoot = service.projectRoot(key);
        Files.createDirectories(projectRoot);
        Files.write(projectRoot.resolve("a.txt"), new byte[10]);
        Files.write(projectRoot.resolve("b.txt"), new byte[32]);

        assertThat(service.directorySize(projectRoot)).isEqualTo(42);
        assertThat(service.directorySize(tempDir.resolve("does-not-exist"))).isZero();
    }
}
