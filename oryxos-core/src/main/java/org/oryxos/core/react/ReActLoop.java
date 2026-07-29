package org.oryxos.core.react;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.oryxos.core.model.AgentDefinition;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Session;
import org.oryxos.core.port.ClockProvider;
import org.oryxos.core.port.MemoryService;
import org.oryxos.core.port.ProviderGateway;
import org.oryxos.core.prompt.PromptBuilder;
import org.oryxos.core.tool.ToolExecutionContext;
import org.oryxos.core.tool.ToolExecutor;

public final class ReActLoop {

    private final PromptBuilder promptBuilder;
    private final ProviderGateway providerGateway;
    private final ToolExecutor toolExecutor;
    private final MemoryService memoryService;
    private final ClockProvider clock;
    private final Duration invocationTimeout;

    public ReActLoop(PromptBuilder promptBuilder, ProviderGateway providerGateway,
            ToolExecutor toolExecutor, MemoryService memoryService,
            ClockProvider clock, Duration invocationTimeout) {
        this.promptBuilder = java.util.Objects.requireNonNull(promptBuilder);
        this.providerGateway = java.util.Objects.requireNonNull(providerGateway);
        this.toolExecutor = java.util.Objects.requireNonNull(toolExecutor);
        this.memoryService = java.util.Objects.requireNonNull(memoryService);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.invocationTimeout = java.util.Objects.requireNonNull(invocationTimeout);
    }

    public Result run(AgentDefinition agent, Session initial, Path workspaceRoot) {
        var session = initial;
        var deadline = clock.now().plus(invocationTimeout);
        for (int iteration = 1; iteration <= agent.profile().maxIterations(); iteration++) {
            if (!clock.now().isBefore(deadline)) {
                return new Result(session, "Invocation timed out", iteration - 1,
                        TerminationReason.TIMEOUT);
            }
            var prompt = promptBuilder.build(agent, session);
            ProviderGateway.Response response;
            try {
                response = providerGateway.generate(new ProviderGateway.Request(
                        session.sessionId(), agent.profile().provider(),
                        agent.profile().model(), iteration, prompt.messages(),
                        prompt.tools(), agent.profile().temperature()));
            } catch (RuntimeException failure) {
                return new Result(session, failure.getMessage(), iteration,
                        TerminationReason.PROVIDER_ERROR);
            }
            if ((response.content() == null || response.content().isBlank())
                    && response.toolCalls().isEmpty()) {
                return new Result(session, "Provider returned a malformed response",
                        iteration, TerminationReason.PROVIDER_ERROR);
            }

            var assistantContent = response.content();
            if (assistantContent == null || assistantContent.isBlank()) {
                assistantContent = "Tool requests: " + response.toolCalls().stream()
                        .map(call -> call.id() + ":" + call.name())
                        .collect(Collectors.joining(", "));
            }
            session = append(session, Message.Role.ASSISTANT, assistantContent,
                    null, null, response.toolCalls().stream()
                            .map(call -> new Message.ToolRequest(
                                    call.id(), call.name(), call.argumentsJson()))
                            .toList());
            if (response.toolCalls().isEmpty()) {
                return new Result(session, assistantContent, iteration,
                        TerminationReason.FINAL_RESPONSE);
            }

            for (var call : response.toolCalls()) {
                var executionContext = new ToolExecutionContext(session.sessionId(),
                        agent.name(), workspaceRoot, deadline, session.channel(),
                        session.userId(), agent.profile().notifyTarget() == null
                                ? Map.of()
                                : Map.of("notifyTarget",
                                        agent.profile().notifyTarget()));
                var toolResult = toolExecutor.execute(call.name(),
                        call.argumentsJson(), executionContext);
                var visible = toolResult.success()
                        ? (toolResult.content() == null ? "(empty result)" : toolResult.content())
                        : "ERROR: " + toolResult.error();
                session = append(session, Message.Role.TOOL, visible,
                        call.id(), call.name(), java.util.List.of());
                if (!toolResult.success()) {
                    return new Result(session, visible, iteration,
                            TerminationReason.TOOL_ERROR);
                }
            }
        }
        return new Result(session, "Maximum ReAct iterations reached",
                agent.profile().maxIterations(), TerminationReason.MAX_ITERATIONS);
    }

    private Session append(Session session, Message.Role role, String content,
            String toolCallId, String toolName,
            java.util.List<Message.ToolRequest> toolRequests) {
        var message = new Message(UUID.randomUUID().toString(),
                session.messages().size() + 1L, role, content,
                toolCallId, toolName, toolRequests, clock.now());
        return memoryService.appendSessionMessage(session.sessionId(), message);
    }

    public enum TerminationReason {
        FINAL_RESPONSE, MAX_ITERATIONS, PROVIDER_ERROR, TOOL_ERROR, TIMEOUT
    }

    public record Result(Session session, String response, int iterations,
            TerminationReason terminationReason) {
    }
}
