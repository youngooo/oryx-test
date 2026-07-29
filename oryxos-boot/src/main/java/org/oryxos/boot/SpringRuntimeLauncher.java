package org.oryxos.boot;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.oryxos.channel.cli.CliChannel;
import org.oryxos.cli.config.ConfigLoader;
import org.oryxos.cli.runtime.RuntimeLauncher;
import org.oryxos.core.port.SessionStore;
import org.oryxos.tool.registry.ToolRegistry;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

public final class SpringRuntimeLauncher implements RuntimeLauncher {
    private final ConfigLoader config = new ConfigLoader();

    @Override
    public int chat(Path selected, String profileName, String userId,
            Reader input, Writer output) {
        return withContext(selected, WebApplicationType.NONE, context ->
                context.getBean(CliChannel.class).chat(
                        profileName, userId, input, output));
    }

    @Override
    public int serve(Path selected) {
        if (!valid(selected)) {
            return 3;
        }
        application(WebApplicationType.SERVLET).run(arguments(selected));
        return 0;
    }

    @Override
    public int gateway(Path selected) {
        return serve(selected);
    }

    @Override
    public List<Map<String, Object>> providers(Path selected) {
        return withContext(selected, WebApplicationType.NONE, context -> {
            var environment = context.getEnvironment();
            return List.of(provider("deepseek",
                            environment.getProperty(
                                    "oryxos.provider.deepseek.model"),
                            environment.getProperty(
                                    "oryxos.provider.deepseek.api-key")),
                    provider("kimi",
                            environment.getProperty("oryxos.provider.kimi.model"),
                            environment.getProperty(
                                    "oryxos.provider.kimi.api-key")));
        });
    }

    @Override
    public List<Map<String, Object>> tools(Path selected) {
        return withContext(selected, WebApplicationType.NONE, context ->
                context.getBean(ToolRegistry.class).definitions().stream()
                        .map(tool -> Map.<String, Object>of(
                                "name", tool.name(),
                                "origin", tool.origin().name(),
                                "description", tool.description()))
                        .toList());
    }

    @Override
    public List<Map<String, Object>> sessions(Path selected) {
        return withContext(selected, WebApplicationType.NONE, context ->
                context.getBean(SessionStore.class).findAll(100).stream()
                        .map(session -> {
                            var row = new LinkedHashMap<String, Object>();
                            row.put("sessionId", session.sessionId());
                            row.put("profileName", session.profileName());
                            row.put("channel", session.channel());
                            row.put("userId", session.userId());
                            row.put("status", session.status().name());
                            row.put("lastActiveAt", session.lastActiveAt());
                            return Map.copyOf(row);
                        }).toList());
    }

    private Map<String, Object> provider(
            String name, String model, String key) {
        var row = new LinkedHashMap<String, Object>();
        row.put("name", name);
        row.put("model", model == null ? "" : model);
        row.put("status", key == null || key.isBlank()
                ? "UNCONFIGURED" : "CONFIGURED");
        return Map.copyOf(row);
    }

    private <T> T withContext(Path selected, WebApplicationType type,
            Function<ConfigurableApplicationContext, T> action) {
        if (!valid(selected)) {
            throw new IllegalStateException(
                    "OryxOS workspace does not exist: "
                            + config.workspace(selected));
        }
        try (var context = application(type).run(arguments(selected))) {
            return action.apply(context);
        }
    }

    private boolean valid(Path selected) {
        return Files.isDirectory(config.workspace(selected));
    }

    private SpringApplication application(WebApplicationType type) {
        var application = new SpringApplication(OryxOsApplication.class);
        application.setWebApplicationType(type);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        if (type == WebApplicationType.NONE) {
            application.setDefaultProperties(Map.of("logging.level.root", "OFF"));
        }
        return application;
    }

    private String[] arguments(Path selected) {
        var workspace = config.workspace(selected);
        var normalized = workspace.toString().replace('\\', '/');
        return new String[] {
                "--oryxos.workspace=" + normalized,
                "--spring.datasource.url=jdbc:sqlite:"
                        + normalized + "/oryxos.db"
        };
    }
}
