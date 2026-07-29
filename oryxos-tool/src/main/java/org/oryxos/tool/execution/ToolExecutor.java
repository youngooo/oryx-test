package org.oryxos.tool.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.oryxos.core.port.ClockProvider;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.port.Sandbox;
import org.oryxos.core.port.ToolCatalog;

/**
 * Tool-module composition type for the core-owned execution pipeline.
 */
public final class ToolExecutor extends org.oryxos.core.tool.ToolExecutor {

    public ToolExecutor(ToolCatalog catalog, Sandbox sandbox,
            InvocationAuditStore auditStore, ClockProvider clock,
            ObjectMapper objectMapper) {
        super(catalog, sandbox, auditStore, clock, objectMapper);
    }
}
