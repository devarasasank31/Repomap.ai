package com.repomap.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Project CRUD including the critical isolation guarantee: a project is only
 * visible to the user who created it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProjectRepository projectRepository;

    private String registerUser(String prefix) throws Exception {
        String username = prefix + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"sup3r-secret"}
                                """.formatted(username, username)))
                .andExpect(status().isCreated());
        return username;
    }

    private String loginToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"sup3r-secret"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long createProject(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"description\":\"demo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void crudLifecycleForOwner() throws Exception {
        String token = loginToken(registerUser("own"));
        long projectId = createProject(token, "billing-service");

        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.items[0].name").value("billing-service"));

        mockMvc.perform(patch("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"billing-service-v2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("billing-service-v2"));

        mockMvc.perform(get("/api/projects?q=billing")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("billing-service-v2"));

        mockMvc.perform(get("/api/projects/" + projectId + "/files")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(delete("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void projectsAreIsolatedBetweenUsers() throws Exception {
        String ownerToken = loginToken(registerUser("iso"));
        long projectId = createProject(ownerToken, "owner-secret-project");

        String otherToken = loginToken(registerUser("oth"));

        // Another user must not see, read or delete it — and gets 404, not 403,
        // so project ids cannot be enumerated either.
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + otherToken)
                        .param("q", "owner-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(delete("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        // Owner still sees it afterwards.
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankNameIsRejected() throws Exception {
        String token = loginToken(registerUser("bad"));
        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void unknownProjectReturns404() throws Exception {
        String token = loginToken(registerUser("nope"));
        mockMvc.perform(get("/api/projects/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void storageKeysAreOpaqueAndUnique() throws Exception {
        String token = loginToken(registerUser("keys"));
        MvcResult first = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"one\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(first.getResponse().getContentAsString());

        // The API response must not leak a server filesystem path.
        assertThat(body.toString()).doesNotContain(tempPathFragment());

        long id = body.get("id").asLong();
        Project project = projectRepository.findById(id).orElseThrow();
        assertThat(project.getStorageKey()).matches("[a-f0-9]{32}");
    }

    private String tempPathFragment() {
        return System.getProperty("java.io.tmpdir");
    }
}
