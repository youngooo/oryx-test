package org.oryxos.web;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.oryxos.core.model.AgentDefinition;
import org.oryxos.core.port.SessionStore;
import org.oryxos.web.api.ApiResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Safe health and build/capability metadata.
 */
@RestController
@RequestMapping("/api/v1")
public class SystemApiController {
    private final Map<String, AgentDefinition> agents;
    private final SessionStore sessions;
    private final Environment environment;
    private final Path workspace;

    public SystemApiController(
            @Qualifier("agentDefinitions") Map<String, AgentDefinition> agents,
            SessionStore sessions, Environment environment,
            @Qualifier("oryxWorkspace") Path workspace) {
        this.agents = Map.copyOf(agents);
        this.sessions = sessions;
        this.environment = environment;
        this.workspace = workspace.toAbsolutePath().normalize();
    }

    @GetMapping("/health")
    public ApiResponse<Health> health() {
        var providers = new LinkedHashMap<String, String>();
        providers.put("deepseek", configured("DEEPSEEK_API_KEY"));
        providers.put("kimi", configuredEither(
                "KIMI_API_KEY", "MOONSHOT_API_KEY"));
        var valid = agents.values().stream().filter(agent ->
                agent.loadStatus() == AgentDefinition.LoadStatus.VALID).count();
        return ApiResponse.ok(new Health("UP",
                sessions == null ? "DOWN" : "UP", providers, valid,
                agents.size() - valid));
    }

    @GetMapping("/info")
    public ApiResponse<Info> info() {
        return ApiResponse.ok(new Info("OryxOS", "0.1.0-SNAPSHOT",
                System.getProperty("java.version"), workspace.toString(),
                List.of("provider", "react", "memory", "tool",
                        "session", "audit", "scheduler", "rest", "cli")));
    }

    private String configured(String name) {
        var value = environment.getProperty(name);
        return value == null || value.isBlank()
                ? "UNCONFIGURED" : "CONFIGURED";
    }

    private String configuredEither(String first, String second) {
        return "CONFIGURED".equals(configured(first))
                || "CONFIGURED".equals(configured(second))
                ? "CONFIGURED" : "UNCONFIGURED";
    }

    public record Health(String status, String database,
            Map<String, String> providers, long loadedProfiles,
            long invalidProfiles) {
    }

    public record Info(String name, String version, String javaVersion,
            String workspace, List<String> capabilities) {
    }
}
