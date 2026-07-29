package org.oryxos.core.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.oryxos.core.model.AgentDefinition;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Session;
import org.oryxos.core.port.ClockProvider;
import org.oryxos.core.port.SessionStore;
import org.oryxos.core.react.ReActLoop;
import org.oryxos.core.session.SessionManager;

/**
 * Sole orchestration entry for CLI, Web, and scheduled invocations.
 */
public final class AgentService {

    private final Map<String, AgentDefinition> agents;
    private final SessionManager sessions;
    private final ReActLoop reactLoop;
    private final ClockProvider clock;
    private final Path workspaceRoot;

    public AgentService(Map<String, AgentDefinition> agents,
            SessionStore sessionStore, ReActLoop reactLoop,
            ClockProvider clock, Path workspaceRoot) {
        this.agents = Map.copyOf(agents);
        this.sessions = new SessionManager(
                java.util.Objects.requireNonNull(sessionStore));
        this.reactLoop = java.util.Objects.requireNonNull(reactLoop);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
    }

    public ReActLoop.Result invoke(String agentName, String channel,
            String userId, String content) {
        var agent = agents.get(agentName);
        if (agent == null || agent.loadStatus() != AgentDefinition.LoadStatus.VALID) {
            throw new IllegalArgumentException("Unknown or invalid Agent: " + agentName);
        }
        var session = sessions.findActive(agentName, channel, userId)
                .orElseGet(() -> createSession(agentName, channel, userId));
        return invokeSession(agent, session.sessionId(), content);
    }

    public ReActLoop.Result invokeSession(String agentName, String sessionId,
            String content) {
        var agent = agents.get(agentName);
        if (agent == null || agent.loadStatus() != AgentDefinition.LoadStatus.VALID) {
            throw new IllegalArgumentException("Unknown or invalid Agent: " + agentName);
        }
        return invokeSession(agent, sessionId, content);
    }

    /**
     * Executes a caller-stateless request while retaining a dedicated persisted
     * Session as the history and audit correlation root.
     */
    public ReActLoop.Result invokeStateless(String agentName, String channel,
            String userId, String content) {
        var agent = agents.get(agentName);
        if (agent == null || agent.loadStatus() != AgentDefinition.LoadStatus.VALID) {
            throw new IllegalArgumentException("Unknown or invalid Agent: " + agentName);
        }
        var session = createSession(agentName, channel, userId);
        return invokeSession(agent, session.sessionId(), content);
    }

    public boolean hasAgent(String agentName) {
        var agent = agents.get(agentName);
        return agent != null && agent.loadStatus() == AgentDefinition.LoadStatus.VALID;
    }

    private ReActLoop.Result invokeSession(AgentDefinition agent,
            String sessionId, String content) {
        return sessions.serialized(sessionId, () -> {
            var session = sessions.require(sessionId);
            if (session.status() == Session.Status.ARCHIVED) {
                throw new IllegalStateException(
                        "Session is archived; create a new Session");
            }
            var userMessage = new Message(UUID.randomUUID().toString(),
                    session.messages().size() + 1L, Message.Role.USER,
                    content, null, null, clock.now());
            session = sessions.append(sessionId, userMessage);
            return reactLoop.run(agent, session, workspaceRoot);
        });
    }

    private Session createSession(String agentName, String channel, String userId) {
        Instant now = clock.now();
        return sessions.create(agentName, channel, userId, now);
    }

    int activeCoordinatorCount() {
        return sessions.activeCoordinatorCount();
    }
}
