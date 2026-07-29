package org.oryxos.web.controller;

import java.util.Map;
import org.oryxos.core.model.AgentDefinition;
import org.oryxos.web.api.ApiResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileApiController {
    private final Map<String, AgentDefinition> definitions;

    public ProfileApiController(
            @Qualifier("agentDefinitions")
            Map<String, AgentDefinition> definitions) {
        this.definitions = Map.copyOf(definitions);
    }

    @GetMapping
    public ApiResponse<java.util.List<ProfileView>> list() {
        return ApiResponse.ok(definitions.values().stream()
                .filter(value -> value.loadStatus()
                        == AgentDefinition.LoadStatus.VALID)
                .map(value -> ProfileView.from(value.profile()))
                .sorted(java.util.Comparator.comparing(ProfileView::name))
                .toList());
    }

    public record ProfileView(String name, String provider, String model,
            java.util.Set<String> tools, java.util.Set<String> channels,
            int scheduleCount, int maxIterations) {
        static ProfileView from(org.oryxos.core.model.Profile value) {
            return new ProfileView(value.name(), value.provider(), value.model(),
                    value.tools(), value.channels(), value.schedules().size(),
                    value.maxIterations());
        }
    }
}
