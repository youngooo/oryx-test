package org.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.oryxos.core.model.LlmCallRecord;
import org.oryxos.core.model.Session;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.port.SessionStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

class LlmCallPersistenceTest {

    private static final String DATABASE =
            "target/llm-persistence-" + UUID.randomUUID() + ".db";
    private static ConfigurableApplicationContext firstContext;

    @BeforeAll
    static void start() {
        firstContext = startContext();
    }

    @AfterAll
    static void stop() {
        if (firstContext != null) {
            firstContext.close();
        }
    }

    @Test
    void persistsSuccessFailureOptionalTokensDurationAndSurvivesRestart() {
        var now = Instant.parse("2026-07-29T00:00:00Z");
        firstContext.getBean(SessionStore.class).save(new Session(
                "session-llm", "demo", "api", "user", List.of(),
                Session.Status.ACTIVE, now, now, null, 0));
        var audit = firstContext.getBean(InvocationAuditStore.class);
        audit.saveLlmCall(new LlmCallRecord("success", "session-llm", "deepseek",
                "deepseek-chat", 1, 12L, 4L, true, "STOP", null, null,
                now, now.plusMillis(35), 35));
        audit.saveLlmCall(new LlmCallRecord("failure", "session-llm", "kimi",
                "moonshot-v1-8k", 2, null, null, false, null,
                "PROVIDER_ERROR", "safe failure", now.plusSeconds(1),
                now.plusSeconds(1).plusMillis(20), 20));

        firstContext.close();
        firstContext = null;
        try (var restarted = startContext()) {
            assertThat(restarted.getBean(SessionStore.class)
                    .findById("session-llm")).isPresent();
            var page = restarted.getBean(InvocationAuditStore.class)
                    .findLlmCalls("session-llm", 0, 10);
            assertThat(page.items()).extracting(LlmCallRecord::id)
                    .containsExactly("success", "failure");
            assertThat(page.items().get(0).promptTokens()).isEqualTo(12);
            assertThat(page.items().get(1).promptTokens()).isNull();
            assertThat(page.items()).extracting(LlmCallRecord::durationMs)
                    .containsExactly(35L, 20L);
            assertThat(restarted.getBean(InvocationAuditStore.class)
                    .findLlmCalls("session-llm", 1, 1).items())
                    .extracting(LlmCallRecord::id).containsExactly("failure");
        }
    }

    private static ConfigurableApplicationContext startContext() {
        return new SpringApplication(StorageApplication.class)
                .run("--spring.main.web-application-type=none",
                        "--spring.datasource.url=jdbc:sqlite:" + DATABASE,
                        "--spring.datasource.driver-class-name=org.sqlite.JDBC",
                        "--spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
                        "--spring.jpa.hibernate.ddl-auto=none",
                        "--spring.sql.init.mode=always",
                        "--spring.sql.init.schema-locations=classpath:db/schema.sql");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "org.oryxos.storage.entity")
    @EnableJpaRepositories(basePackages = "org.oryxos.storage.repository")
    @ComponentScan(basePackages = "org.oryxos.storage.adapter")
    static class StorageApplication {
    }
}
