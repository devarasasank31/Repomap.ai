package com.repomap.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verifies that every error path produces a stable {@link ApiError} payload and
 * never leaks stack traces.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @GetMapping("/missing")
        public String missing() {
            throw new ResourceNotFoundException("project 42 not found");
        }

        @GetMapping("/bad")
        public String bad() {
            throw new BadRequestException("unsupported file type");
        }

        @GetMapping("/boom")
        public String boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @GetMapping("/unreadable")
        public String unreadable() {
            throw new HttpMessageNotReadableException("broken json");
        }

        @PostMapping("/validated")
        public String validated(@RequestBody @Valid Payload payload) {
            return "ok";
        }

        record Payload(@NotBlank String name) {
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void notFoundReturns404WithErrorBody() throws Exception {
        mockMvc.perform(get("/probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("project 42 not found"))
                .andExpect(jsonPath("$.path").value("/probe/missing"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void badRequestReturns400() throws Exception {
        mockMvc.perform(get("/probe/bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("unsupported file type"));
    }

    @Test
    void unexpectedExceptionReturnsGeneric500WithoutInternals() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(get("/probe/unreadable"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void validationFailureReturnsFieldErrors() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/probe/validated")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details.name").exists());
    }

    @Test
    void statusReasonPhraseIsMapped() {
        org.junit.jupiter.api.Assertions.assertEquals("Not Found", HttpStatus.NOT_FOUND.getReasonPhrase());
    }
}
