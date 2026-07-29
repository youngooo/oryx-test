package org.oryxos.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.oryxos.web.error.GlobalExceptionHandler;
import org.oryxos.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsMissingResourcesToStableEnvelope() throws Exception {
        mvc.perform(get("/failure/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Session not found"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void mapsArchivedSessionsToConflictWithoutStackTrace() throws Exception {
        mvc.perform(get("/failure/archived"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ARCHIVED"))
                .andExpect(jsonPath("$.message").value(
                        "Session is archived; create a new Session"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void hidesUnexpectedInternalFailureDetails() throws Exception {
        mvc.perform(get("/failure/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "An unexpected internal error occurred"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void mapsProviderToolAndTimeoutFailuresToStableCodes() throws Exception {
        mvc.perform(get("/failure/provider"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PROVIDER_FAILURE"));
        mvc.perform(get("/failure/tool"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("TOOL_FAILURE"));
        mvc.perform(get("/failure/timeout"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("INVOCATION_TIMEOUT"));
    }

    @RestController
    static class FailureController {
        @GetMapping("/failure/missing")
        void missing() {
            throw new NoSuchElementException("Session not found");
        }

        @GetMapping("/failure/archived")
        void archived() {
            throw new IllegalStateException(
                    "Session is archived; create a new Session");
        }

        @GetMapping("/failure/internal")
        void internal() {
            throw new RuntimeException("secret-token-must-not-leak");
        }

        @GetMapping("/failure/provider")
        void provider() {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "PROVIDER_FAILURE", "Provider invocation failed");
        }

        @GetMapping("/failure/tool")
        void tool() {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "TOOL_FAILURE", "Tool invocation failed");
        }

        @GetMapping("/failure/timeout")
        void timeout() {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT,
                    "INVOCATION_TIMEOUT", "Agent invocation timed out");
        }
    }
}
