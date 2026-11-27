package com.repomap.ingestion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Full upload pipeline: multipart import → extraction → inventory → source
 * reading, plus the rejection paths (garbage archives, traversal, foreign
 * projects).
 */
@SpringBootTest
@AutoConfigureMockMvc
class IngestionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String prefix) throws Exception {
        String username = prefix + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"sup3r-secret"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated());
        return login(username);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"sup3r-secret"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private static byte[] demoArchive() {
        return zip(
                "src/main/java/com/example/App.java=package com.example;\n\npublic class App {\n}\n",
                "src/main/java/com/example/AppService.java=package com.example;\n\npublic class AppService {\n}\n",
                "README.md=# Demo\n",
                "node_modules/dep/index.js=module.exports = 1;",
                ".env=API_KEY=super-secret");
    }

    private static byte[] zip(String... entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zipStream = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (String entry : entries) {
                String[] parts = entry.split("=", 2);
                zipStream.putNextEntry(new ZipEntry(parts[0]));
                if (parts.length > 1) {
                    zipStream.write(parts[1].getBytes(StandardCharsets.UTF_8));
                }
                zipStream.closeEntry();
            }
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        return out.toByteArray();
    }

    private JsonNode importArchive(String token, byte[] content, String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/zip", content);
        MvcResult result = mockMvc.perform(multipart("/api/projects/import").file(file)
                        .param("name", "demo-service")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void importBuildsInventoryFromExtractedFiles() throws Exception {
        String token = register("imp");
        JsonNode project = importArchive(token, demoArchive(), "demo.zip");

        long id = project.get("id").asLong();
        assertThatStatus(project, "IMPORTED");
        // 3 analysable files; node_modules and .env are skipped.
        org.assertj.core.api.Assertions.assertThat(project.get("fileCount").asInt()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(project.get("javaFileCount").asInt()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(project.get("totalSizeBytes").asLong()).isPositive();

        mockMvc.perform(get("/api/projects/" + id + "/files")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.path == 'src/main/java/com/example/App.java')]").exists())
                .andExpect(jsonPath("$[?(@.path == 'README.md')]").exists());

        mockMvc.perform(get("/api/projects/" + id + "/source")
                        .param("path", "src/main/java/com/example/App.java")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("java"))
                .andExpect(jsonPath("$.lineCount").value(4))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void sourcePathTraversalIsRejected() throws Exception {
        String token = register("trv");
        JsonNode project = importArchive(token, demoArchive(), "demo.zip");
        long id = project.get("id").asLong();

        mockMvc.perform(get("/api/projects/" + id + "/source")
                        .param("path", "../../../../windows/win.ini")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/projects/" + id + "/source")
                        .param("path", "/etc/passwd")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/projects/" + id + "/source")
                        .param("path", "src/main/java/com/example/App.java")
                        .header("Authorization", "Bearer " + "not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void foreignProjectsCannotBeRead() throws Exception {
        String ownerToken = register("own");
        JsonNode project = importArchive(ownerToken, demoArchive(), "demo.zip");
        long id = project.get("id").asLong();

        String otherToken = register("frn");
        mockMvc.perform(get("/api/projects/" + id + "/source")
                        .param("path", "README.md")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void foreignProjectsCannotBeImported() throws Exception {
        String ownerToken = register("own");
        JsonNode project = importArchive(ownerToken, demoArchive(), "demo.zip");
        long id = project.get("id").asLong();

        String otherToken = register("frn");
        MockMultipartFile file = new MockMultipartFile("file", "demo.zip", "application/zip", demoArchive());
        mockMvc.perform(multipart("/api/projects/" + id + "/import").file(file)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongContentTypeOnImportReturns415() throws Exception {
        String token = register("ct");
        mockMvc.perform(post("/api/projects/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void garbageArchiveIsRejectedAndTrackedAsFailedImport() throws Exception {
        String token = register("bad");
        MockMultipartFile file = new MockMultipartFile(
                "file", "not-a-zip.zip", "application/zip", "definitely not a zip".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/projects/import").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Archive rejected: the file is not a valid ZIP archive"));

        // The failed attempt is still visible so import status can be tracked.
        MvcResult list = mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(list.getResponse().getContentAsString()).get("items");
        boolean hasFailed = false;
        for (JsonNode item : items) {
            if ("IMPORT_FAILED".equals(item.get("status").asText())) {
                hasFailed = true;
                org.assertj.core.api.Assertions.assertThat(item.get("errorMessage").asText())
                        .doesNotContain("C:\\")
                        .doesNotContain(System.getProperty("java.io.tmpdir"));
            }
        }
        org.assertj.core.api.Assertions.assertThat(hasFailed).isTrue();
    }

    @Test
    void emptyUploadIsRejectedBeforeAProjectIsCreated() throws Exception {
        String token = register("emp");
        MockMultipartFile file = new MockMultipartFile("file", "empty.zip", "application/zip", new byte[0]);

        mockMvc.perform(multipart("/api/projects/import").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No archive uploaded"));
    }

    @Test
    void reimportReplacesThePreviousInventory() throws Exception {
        String token = register("rei");
        JsonNode project = importArchive(token, demoArchive(), "demo.zip");
        long id = project.get("id").asLong();
        org.assertj.core.api.Assertions.assertThat(project.get("fileCount").asInt()).isEqualTo(3);

        byte[] smaller = zip("only/One.java=class One {}");
        MockMultipartFile file = new MockMultipartFile("file", "small.zip", "application/zip", smaller);

        mockMvc.perform(multipart("/api/projects/" + id + "/import").file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileCount").value(1))
                .andExpect(jsonPath("$.javaFileCount").value(1));

        mockMvc.perform(get("/api/projects/" + id + "/files")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void importRequiresAuthentication() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "demo.zip", "application/zip", demoArchive());
        mockMvc.perform(multipart("/api/projects/import").file(file))
                .andExpect(status().isUnauthorized());
    }

    private static void assertThatStatus(JsonNode project, String expected) {
        org.assertj.core.api.Assertions.assertThat(project.get("status").asText()).isEqualTo(expected);
    }
}
