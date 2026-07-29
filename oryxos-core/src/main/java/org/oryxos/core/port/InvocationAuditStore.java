package org.oryxos.core.port;

import java.util.List;
import org.oryxos.core.model.LlmCallRecord;
import org.oryxos.core.model.ToolInvocationRecord;

public interface InvocationAuditStore {
    void saveLlmCall(LlmCallRecord record);
    void saveToolInvocation(ToolInvocationRecord record);
    Page<LlmCallRecord> findLlmCalls(String sessionId, int offset, int limit);
    Page<ToolInvocationRecord> findToolInvocations(String sessionId, int offset, int limit);

    record Page<T>(List<T> items, int offset, int limit, long total) {
        public Page {
            items = List.copyOf(items);
        }
    }
}
