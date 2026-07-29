package org.oryxos.web.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.oryxos.core.model.AgentDefinition;
import org.oryxos.core.model.Session;
import org.oryxos.core.port.ClockProvider;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.port.SessionStore;
import org.oryxos.core.react.ReActLoop;
import org.oryxos.core.service.AgentService;
import org.oryxos.web.api.AgentDtos;
import org.oryxos.web.api.ApiResponse;
import org.oryxos.web.api.SessionDtos;
import org.oryxos.web.error.ApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionApiController {
    private final SessionStore sessions;
    private final InvocationAuditStore audits;
    private final AgentService agents;
    private final ClockProvider clock;
    private final Map<String, AgentDefinition> definitions;

    public SessionApiController(SessionStore sessions,
            InvocationAuditStore audits, AgentService agents,
            ClockProvider clock,
            @Qualifier("agentDefinitions")
            Map<String, AgentDefinition> definitions) {
        this.sessions = sessions;
        this.audits = audits;
        this.agents = agents;
        this.clock = clock;
        this.definitions = Map.copyOf(definitions);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SessionDtos.SessionView>> create(
            @Valid @RequestBody SessionDtos.CreateSessionRequest request) {
        var definition = definitions.get(request.profileName());
        if (definition == null
                || definition.loadStatus() != AgentDefinition.LoadStatus.VALID) {
            throw new NoSuchElementException(
                    "Agent not found: " + request.profileName());
        }
        Instant now = clock.now();
        var session = sessions.save(new Session(UUID.randomUUID().toString(),
                request.profileName().strip(), request.channel().strip(),
                request.userId().strip(), java.util.List.of(),
                Session.Status.ACTIVE, now, now, null, 0));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(SessionDtos.SessionView.from(session)));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<AgentDtos.AgentTurn> message(@PathVariable String id,
            @Valid @RequestBody SessionDtos.MessageRequest request) {
        var session = requireSession(id);
        if (session.status() == Session.Status.ARCHIVED) {
            throw new IllegalStateException(
                    "Session is archived; create a new Session");
        }
        var result = agents.invokeSession(
                session.profileName(), id, request.content().strip());
        ensureCompleted(result);
        return ApiResponse.ok(AgentDtos.AgentTurn.from(result));
    }

    @GetMapping("/{id}")
    public ApiResponse<SessionDtos.SessionDetail> get(@PathVariable String id,
            @RequestParam(defaultValue = "100")
            @Min(1) @Max(100) int messageLimit,
            @RequestParam(defaultValue = "0") @Min(0) int llmOffset,
            @RequestParam(defaultValue = "100")
            @Min(1) @Max(100) int llmLimit,
            @RequestParam(defaultValue = "0") @Min(0) int toolOffset,
            @RequestParam(defaultValue = "100")
            @Min(1) @Max(100) int toolLimit) {
        validatePage(messageLimit, llmOffset, llmLimit,
                toolOffset, toolLimit);
        var session = requireSession(id);
        return ApiResponse.ok(SessionDtos.SessionDetail.from(session,
                sessions.findMessages(id, messageLimit),
                audits.findLlmCalls(id, llmOffset, llmLimit),
                audits.findToolInvocations(id, toolOffset, toolLimit)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<SessionDtos.SessionView> archive(@PathVariable String id) {
        requireSession(id);
        return ApiResponse.ok(SessionDtos.SessionView.from(
                sessions.archive(id, clock.now())));
    }

    private Session requireSession(String id) {
        return sessions.findById(id).orElseThrow(() ->
                new NoSuchElementException("Session not found: " + id));
    }

    private void validatePage(int messageLimit, int llmOffset, int llmLimit,
            int toolOffset, int toolLimit) {
        if (messageLimit < 1 || messageLimit > 100
                || llmOffset < 0 || toolOffset < 0
                || llmLimit < 1 || llmLimit > 100
                || toolLimit < 1 || toolLimit > 100) {
            throw new IllegalArgumentException(
                    "Offsets must be non-negative and limits must be 1..100");
        }
    }

    static void ensureCompleted(ReActLoop.Result result) {
        switch (result.terminationReason()) {
            case TIMEOUT -> throw new ApiException(HttpStatus.GATEWAY_TIMEOUT,
                    "INVOCATION_TIMEOUT", "Agent invocation timed out");
            case PROVIDER_ERROR -> throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "PROVIDER_FAILURE", "Provider invocation failed");
            case TOOL_ERROR -> throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "TOOL_FAILURE", "Tool invocation failed");
            default -> {
            }
        }
    }
}
