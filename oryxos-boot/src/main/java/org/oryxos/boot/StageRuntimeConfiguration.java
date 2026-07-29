package org.oryxos.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.oryxos.channel.cli.CliChannel;
import org.oryxos.core.agent.AgentLoader;
import org.oryxos.core.agent.ContextLoader;
import org.oryxos.core.model.AgentDefinition;
import org.oryxos.core.port.ClockProvider;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.port.MemoryService;
import org.oryxos.core.port.ProviderGateway;
import org.oryxos.core.port.Sandbox;
import org.oryxos.core.port.SessionStore;
import org.oryxos.core.prompt.PromptBuilder;
import org.oryxos.core.react.ReActLoop;
import org.oryxos.core.service.AgentService;
import org.oryxos.core.scheduling.AgentScheduler;
import org.oryxos.core.scheduling.ScheduledAgentInvoker;
import org.oryxos.core.tool.ToolExecutor;
import org.oryxos.memory.DefaultMemoryService;
import org.oryxos.memory.LongTermMemoryStore;
import org.oryxos.memory.config.MemoryBackendConfiguration;
import org.oryxos.memory.sqlite.MemoryEntryRepository;
import org.oryxos.memory.tool.MemoryTools;
import org.oryxos.core.session.SessionManager;
import org.oryxos.tool.builtin.HttpGetTool;
import org.oryxos.tool.builtin.HttpPostTool;
import org.oryxos.tool.builtin.ListDirTool;
import org.oryxos.tool.builtin.ReadFileTool;
import org.oryxos.tool.builtin.ShellTool;
import org.oryxos.tool.builtin.WriteFileTool;
import org.oryxos.tool.mcp.DefaultMcpClientFactory;
import org.oryxos.tool.mcp.McpClientService;
import org.oryxos.tool.mcp.McpConfigLoader;
import org.oryxos.tool.notify.ConsoleNotificationAdapter;
import org.oryxos.tool.notify.NotifyTool;
import org.oryxos.tool.plugin.JavaPluginDiscovery;
import org.oryxos.tool.registry.ToolRegistry;
import org.oryxos.tool.sandbox.WhitelistSandbox;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Composition root for the US1/US2 interactive runtime.
 */
@Configuration
public class StageRuntimeConfiguration {

    @Bean
    Path oryxWorkspace(Environment environment) {
        return Path.of(environment.getRequiredProperty("oryxos.workspace"))
                .toAbsolutePath().normalize();
    }

    @Bean
    LongTermMemoryStore longTermMemoryStore(
            Environment environment, Path oryxWorkspace,
            MemoryEntryRepository repository, ObjectMapper mapper) {
        return MemoryBackendConfiguration.select(
                environment.getProperty("oryxos.memory.backend", "markdown"),
                oryxWorkspace, repository,
                URI.create(environment.getProperty(
                        "oryxos.memory.mem0.base-url", "http://localhost:8000")),
                environment.getProperty("oryxos.memory.mem0.api-key", ""),
                mapper);
    }

    @Bean
    MemoryService memoryService(
            SessionStore sessions, LongTermMemoryStore longTerm) {
        return new DefaultMemoryService(new SessionManager(sessions), longTerm);
    }

    @Bean
    Sandbox sandbox(Environment environment, Path oryxWorkspace) {
        return new WhitelistSandbox(
                csv(environment.getProperty("oryxos.sandbox.allowed-http-hosts")),
                csv(environment.getProperty("oryxos.sandbox.allowed-commands")),
                Set.of(oryxWorkspace));
    }

    @Bean
    ToolRegistry toolRegistry(
            Sandbox sandbox, ObjectMapper mapper, MemoryService memory,
            Path oryxWorkspace, Environment environment,
            ListableBeanFactory beanFactory) {
        var tools = new java.util.ArrayList<org.oryxos.core.tool.OryxTool>();
        var http = HttpGetTool.safeClient();
        tools.add(new ReadFileTool(mapper));
        tools.add(new WriteFileTool(mapper));
        tools.add(new ListDirTool(mapper));
        tools.add(new ShellTool(mapper));
        tools.add(new HttpGetTool(http, sandbox, mapper));
        tools.add(new HttpPostTool(http, sandbox, mapper));
        tools.addAll(MemoryTools.create(memory, mapper));
        tools.add(new NotifyTool(List.of(
                new ConsoleNotificationAdapter(
                        new java.io.PrintWriter(System.out, true))),
                "console", mapper));
        var configured = Path.of(environment.getProperty(
                "oryxos.mcp.config", oryxWorkspace.resolve("mcp_servers.yaml").toString()));
        if (!configured.isAbsolute() && !java.nio.file.Files.isRegularFile(configured)) {
            configured = oryxWorkspace.resolve("mcp_servers.yaml");
        }
        var mcpConfigs = new McpConfigLoader().load(configured);
        if (!mcpConfigs.isEmpty()) {
            tools.addAll(new McpClientService(mcpConfigs,
                    new DefaultMcpClientFactory(http, mapper, sandbox)).tools());
        }
        tools.addAll(new JavaPluginDiscovery(beanFactory, mapper).discover());
        return new ToolRegistry(tools);
    }

    @Bean
    Map<String, AgentDefinition> agentDefinitions(
            Path oryxWorkspace, ToolRegistry tools, Environment environment) {
        var zone = ZoneId.of(environment.getRequiredProperty(
                "oryxos.scheduler.zone"));
        return new AgentLoader(Set.of("deepseek", "kimi"),
                tools.all().stream().map(tool -> tool.getName())
                        .collect(Collectors.toSet()),
                Set.of("cli", "api", "scheduler"), zone)
                .load(oryxWorkspace).stream()
                .collect(Collectors.toUnmodifiableMap(
                        AgentDefinition::name, Function.identity()));
    }

    @Bean
    PromptBuilder promptBuilder(Path oryxWorkspace,
            MemoryService memory, ToolRegistry tools,
            ClockProvider clock, Environment environment) {
        return new PromptBuilder(new ContextLoader(oryxWorkspace),
                memory, tools, clock,
                ZoneId.of(environment.getRequiredProperty(
                        "oryxos.scheduler.zone")));
    }

    @Bean
    ToolExecutor toolExecutor(ToolRegistry tools, Sandbox sandbox,
            InvocationAuditStore audit, ClockProvider clock,
            ObjectMapper mapper) {
        return new ToolExecutor(tools, sandbox, audit, clock, mapper);
    }

    @Bean
    ReActLoop reActLoop(PromptBuilder prompt, ProviderGateway provider,
            ToolExecutor tools, MemoryService memory, ClockProvider clock) {
        return new ReActLoop(prompt, provider, tools, memory,
                clock, Duration.ofSeconds(60));
    }

    @Bean
    AgentService agentService(
            @Qualifier("agentDefinitions") Map<String, AgentDefinition> agents,
            SessionStore sessions, ReActLoop loop, ClockProvider clock,
            Path oryxWorkspace) {
        return new AgentService(agents, sessions, loop, clock, oryxWorkspace);
    }

    @Bean
    CliChannel cliChannel(AgentService agents) {
        return new CliChannel(agents);
    }

    @Bean(destroyMethod = "shutdown")
    ThreadPoolTaskScheduler agentTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("oryxos-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }

    @Bean(destroyMethod = "close")
    AgentScheduler agentScheduler(
            ThreadPoolTaskScheduler taskScheduler,
            AgentService agentService,
            @Qualifier("agentDefinitions") Map<String, AgentDefinition> agents,
            Environment environment) {
        var scheduler = new AgentScheduler(taskScheduler,
                new ScheduledAgentInvoker(agentService));
        if (environment.getProperty(
                "oryxos.scheduler.enabled", Boolean.class, true)) {
            scheduler.reload(agents.values().stream()
                    .filter(agent -> agent.loadStatus()
                            == AgentDefinition.LoadStatus.VALID)
                    .map(AgentDefinition::profile).toList());
        }
        return scheduler;
    }

    private Set<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}
