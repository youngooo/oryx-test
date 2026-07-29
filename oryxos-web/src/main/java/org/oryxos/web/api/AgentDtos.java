package org.oryxos.web.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.oryxos.core.react.ReActLoop;

public final class AgentDtos {
    private AgentDtos() {
    }

    public record InvokeAgentRequest(
            @NotBlank @Size(max = 32 * 1024) String message,
            @Size(max = 32) String channel,
            @Size(max = 128) String userId) {
        public String effectiveChannel() {
            return channel == null || channel.isBlank() ? "api" : channel.strip();
        }

        public String effectiveUserId() {
            return userId == null || userId.isBlank()
                    ? "stateless" : userId.strip();
        }
    }

    public record AgentTurn(
            String sessionId,
            String response,
            int iterations,
            ReActLoop.TerminationReason terminationReason,
            Instant completedAt) {
        public static AgentTurn from(ReActLoop.Result result) {
            return new AgentTurn(result.session().sessionId(), result.response(),
                    result.iterations(), result.terminationReason(),
                    result.session().lastActiveAt());
        }
    }
}
