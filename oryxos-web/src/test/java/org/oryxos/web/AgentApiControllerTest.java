package org.oryxos.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.oryxos.core.model.Session;
import org.oryxos.core.react.ReActLoop;
import org.oryxos.core.service.AgentService;
import org.oryxos.web.controller.AgentApiController;
import org.oryxos.web.error.GlobalExceptionHandler;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AgentApiControllerTest {

    @Test
    void statelessInvocationUsesFreshPersistedSessionThroughAgentService()
            throws Exception {
        var service = mock(AgentService.class);
        when(service.hasAgent("digest")).thenReturn(true);
        var now = Instant.parse("2026-07-29T02:00:00Z");
        var session = new Session("invocation-session", "digest", "api",
                "stateless", List.of(), Session.Status.ACTIVE,
                now, now, null, 0);
        when(service.invokeStateless("digest", "api", "stateless", "run now"))
                .thenReturn(new ReActLoop.Result(session, "done", 2,
                        ReActLoop.TerminationReason.FINAL_RESPONSE));
        var mvc = MockMvcBuilders.standaloneSetup(
                        new AgentApiController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/api/v1/agents/digest/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"run now\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId")
                        .value("invocation-session"))
                .andExpect(jsonPath("$.data.response").value("done"))
                .andExpect(jsonPath("$.data.iterations").value(2));

        verify(service).invokeStateless(
                "digest", "api", "stateless", "run now");
    }
}
