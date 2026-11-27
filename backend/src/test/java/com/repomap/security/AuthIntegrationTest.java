package com.repomap.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * End-to-end authentication flow: register → token → protected call → logout.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String register(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"%s"}
                                """.formatted(username, username, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value(username))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }

    @Test
    void registerLoginAndAuthenticatedCall() throws Exception {
        String username = "u" + uniqueSuffix();
        String token = register(username, "sup3r-secret");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        // Password must never appear in any response.
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(containsString("sup3r-secret"))));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"sup3r-secret"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void duplicateUsernameIsRejectedWith409() throws Exception {
        String username = "d" + uniqueSuffix();
        register(username, "sup3r-secret");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"other@example.com","password":"sup3r-secret"}
                                """.formatted(username)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already taken")));
    }

    @Test
    void weakPasswordIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"weakuser","email":"weak@example.com","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    void wrongPasswordIsRejectedWith401() throws Exception {
        String username = "w" + uniqueSuffix();
        register(username, "sup3r-secret");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"totally-wrong"}
                                """.formatted(username)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesToken() throws Exception {
        String username = "o" + uniqueSuffix();
        String token = register(username, "sup3r-secret");

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
