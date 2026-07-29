package org.oryxos.web.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.oryxos.core.service.AgentService;
import org.oryxos.web.api.AgentDtos;
import org.oryxos.web.api.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/agents")
public class AgentApiController {
    private final AgentService agents;

    public AgentApiController(AgentService agents) {
        this.agents = agents;
    }

    @PostMapping("/{name}/invoke")
    public ApiResponse<AgentDtos.AgentTurn> invoke(
            @PathVariable
            @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$")
            String name,
            @Valid @RequestBody AgentDtos.InvokeAgentRequest request) {
        if (!agents.hasAgent(name)) {
            throw new java.util.NoSuchElementException(
                    "Agent not found: " + name);
        }
        var result = agents.invokeStateless(name, request.effectiveChannel(),
                request.effectiveUserId(), request.message().strip());
        SessionApiController.ensureCompleted(result);
        return ApiResponse.ok(AgentDtos.AgentTurn.from(result));
    }
}
