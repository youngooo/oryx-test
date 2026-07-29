package org.oryxos.core.scheduling;

import java.util.Objects;
import org.oryxos.core.react.ReActLoop;
import org.oryxos.core.service.AgentService;

/**
 * Minimal schedule-to-AgentService adapter. Cron registration/non-overlap belongs
 * to the later complete scheduler story.
 */
public final class ScheduledAgentInvoker {

    private final AgentService agentService;

    public ScheduledAgentInvoker(AgentService agentService) {
        this.agentService = Objects.requireNonNull(agentService);
    }

    public ReActLoop.Result invoke(String agentName, String scheduleId, String prompt) {
        return agentService.invoke(agentName, "scheduler",
                "scheduler:" + scheduleId, prompt);
    }
}
