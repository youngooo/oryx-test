package org.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.oryxos.core.model.LlmCallRecord;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Session;
import org.oryxos.core.model.ToolInvocationRecord;
import org.oryxos.core.port.InvocationAuditStore;
import org.oryxos.core.port.SessionStore;
import org.oryxos.storage.entity.SessionEntity;
import org.oryxos.storage.repository.SessionRepository;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = CorePersistenceFoundationTest.TestApplication.class,
        webEnvironment = WebEnvironment.NONE)
class CorePersistenceFoundationTest {

    private static final String DATABASE = "target/core-foundation-" + UUID.randomUUID() + ".db";
    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

    @org.springframework.beans.factory.annotation.Autowired
    SessionStore sessions;
    @org.springframework.beans.factory.annotation.Autowired
    InvocationAuditStore audits;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
        registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
        registry.add("spring.jpa.database-platform",
                () -> "org.hibernate.community.dialect.SQLiteDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:db/schema.sql");
    }

    @Test
    void createsLoadsAppendsAndReadsPagedAudits() {
        sessions.save(new Session("s1", "demo", "cli", "user", List.of(),
                Session.Status.ACTIVE, NOW, NOW, null, 0));
        sessions.append("s1", new Message("m1", 1, Message.Role.USER,
                "hello", null, null, NOW.plusSeconds(1)));

        audits.saveLlmCall(new LlmCallRecord("l1", "s1", "deepseek",
                "deepseek-chat", 1, 3L, 2L, true, "STOP", null, null,
                NOW, NOW.plusMillis(25), 25));
        audits.saveToolInvocation(new ToolInvocationRecord("t1", "s1",
                "http_get", "{}", false, null, "TIMEOUT", "request timed out",
                true, NOW.plusSeconds(1), NOW.plusSeconds(2), 1000));

        assertThat(sessions.findById("s1").orElseThrow().messages()).hasSize(1);
        assertThat(audits.findLlmCalls("s1", 0, 10).items())
                .extracting(LlmCallRecord::id).containsExactly("l1");
        assertThat(audits.findToolInvocations("s1", 0, 10).total()).isEqualTo(1);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "org.oryxos.storage.entity")
    @EnableJpaRepositories(basePackages = "org.oryxos.storage.repository")
    @ComponentScan(basePackages = "org.oryxos.storage.adapter")
    static class TestApplication {
    }
}
