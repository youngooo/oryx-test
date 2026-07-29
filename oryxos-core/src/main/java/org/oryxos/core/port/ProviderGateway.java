package org.oryxos.core.port;

import java.util.List;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.ToolDefinition;

public interface ProviderGateway {

    Response generate(Request request);

    record Request(
            String sessionId,
            String provider,
            String model,
            int iteration,
            List<Message> messages,
            List<ToolDefinition> tools,
            Double temperature) {
        public Request {
            messages = List.copyOf(messages == null ? List.of() : messages);
            tools = List.copyOf(tools == null ? List.of() : tools);
        }
    }

    record Response(
            String content,
            List<ToolCall> toolCalls,
            Long promptTokens,
            Long completionTokens,
            String finishReason) {
        public Response {
            toolCalls = List.copyOf(toolCalls == null ? List.of() : toolCalls);
        }
    }

    record ToolCall(String id, String name, String argumentsJson) {
    }
}
