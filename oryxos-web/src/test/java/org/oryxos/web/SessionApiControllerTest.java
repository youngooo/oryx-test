package org.oryxos.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.oryxos.core.model.AgentDefinition;
import org.oryxos.core.model.LlmCallRecord;
import org.oryxos.core.model.Profile;
import org.oryxos.core.model.Session;
import org.oryxos.core.model.ToolInvocationRecord;
import org.oryxos.core.port.ClockProvider;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.port.SessionStore;
import org.oryxos.core.service.AgentService;
import org.oryxos.web.controller.SessionApiController;
import org.oryxos.web.error.GlobalExceptionHandler;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SessionApiControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-29T02:00:00Z");
    private SessionStore sessions;
    private InvocationAuditStore audits;
    private AgentService agents;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        sessions = mock(SessionStore.class);
        audits = mock(InvocationAuditStore.class);
        agents = mock(AgentService.class);
        ClockProvider clock = () -> NOW;
        var profile = new Profile("weather", "deepseek", "deepseek-chat",
                java.util.Set.of(), java.util.Set.of("api"), null,
                List.of(), 10, null);
        var definition = mock(AgentDefinition.class);
        when(definition.loadStatus()).thenReturn(AgentDefinition.LoadStatus.VALID);
        when(definition.profile()).thenReturn(profile);
        mvc = MockMvcBuilders.standaloneSetup(new SessionApiController(
                        sessions, audits, agents, clock,
                        Map.of("weather", definition)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsSessionWithCommonEnvelope() throws Exception {
        when(sessions.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"profileName":"weather","channel":"api",
                                 "userId":"operator"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.profileName").value("weather"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void rejectsMessagesLargerThanThirtyTwoKib() throws Exception {
        var content = "x".repeat(32 * 1024 + 1);
        mvc.perform(post("/api/v1/sessions/id/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void returnsIndependentAuditPagesAndTotals() throws Exception {
        var session = new Session("session-1", "weather", "api", "operator",
                List.of(), Session.Status.ACTIVE, NOW, NOW, null, 0);
        var llm = new LlmCallRecord("llm-1", "session-1", "deepseek",
                "deepseek-chat", 1, 10L, 4L, true, "stop",
                null, null, NOW, NOW, 0);
        var tool = new ToolInvocationRecord("tool-1", "session-1", "http_get",
                "{}", true, "ok", null, null, false, NOW, NOW, 0);
        when(sessions.findById("session-1"))
                .thenReturn(java.util.Optional.of(session));
        when(sessions.findMessages("session-1", 20)).thenReturn(List.of());
        when(audits.findLlmCalls("session-1", 1, 2))
                .thenReturn(new InvocationAuditStore.Page<>(List.of(llm), 1, 2, 5));
        when(audits.findToolInvocations("session-1", 3, 4))
                .thenReturn(new InvocationAuditStore.Page<>(List.of(tool), 3, 4, 9));

        mvc.perform(get("/api/v1/sessions/session-1")
                        .param("messageLimit", "20")
                        .param("llmOffset", "1").param("llmLimit", "2")
                        .param("toolOffset", "3").param("toolLimit", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.llmCallsPage.total").value(5))
                .andExpect(jsonPath("$.data.toolInvocationsPage.total").value(9))
                .andExpect(jsonPath("$.data.llmCalls[0].id").value("llm-1"))
                .andExpect(jsonPath("$.data.toolInvocations[0].id").value("tool-1"));
    }

    @Test
    void rejectsInvalidAuditPageInputs() throws Exception {
        when(sessions.findById("session-1")).thenReturn(java.util.Optional.of(
                new Session("session-1", "weather", "api", "operator",
                        List.of(), Session.Status.ACTIVE, NOW, NOW, null, 0)));
        mvc.perform(get("/api/v1/sessions/session-1")
                        .param("llmOffset", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void archivedSessionRemainsReadableButRejectsNewMessages() throws Exception {
        var archived = new Session("archived-1", "weather", "api", "operator",
                List.of(), Session.Status.ARCHIVED, NOW, NOW, NOW, 1);
        when(sessions.findById("archived-1"))
                .thenReturn(java.util.Optional.of(archived));

        mvc.perform(post("/api/v1/sessions/archived-1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ARCHIVED"));
    }

    @Test
    void sendsMessageThroughAgentServiceAndArchivesIdempotently()
            throws Exception {
        var active = new Session("session-2", "weather", "api", "operator",
                List.of(), Session.Status.ACTIVE, NOW, NOW, null, 0);
        var archived = active.archive(NOW);
        when(sessions.findById("session-2"))
                .thenReturn(java.util.Optional.of(active));
        when(agents.invokeSession("weather", "session-2", "hello"))
                .thenReturn(new org.oryxos.core.react.ReActLoop.Result(
                        active, "response", 1,
                        org.oryxos.core.react.ReActLoop.TerminationReason
                                .FINAL_RESPONSE));
        when(sessions.archive("session-2", NOW)).thenReturn(archived);

        mvc.perform(post("/api/v1/sessions/session-2/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.response").value("response"));
        mvc.perform(delete("/api/v1/sessions/session-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    }
}
