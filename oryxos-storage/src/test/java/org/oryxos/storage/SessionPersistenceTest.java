package org.oryxos.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.oryxos.core.model.Message;
import org.oryxos.core.model.Session;
import org.oryxos.core.port.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = SessionPersistenceTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SessionPersistenceTest {
    static final String DB = "target/session-" + UUID.randomUUID() + ".db";
    @Autowired SessionStore store;

    @DynamicPropertySource static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB);
        registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
        registry.add("spring.jpa.database-platform",
                () -> "org.hibernate.community.dialect.SQLiteDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:db/schema.sql");
    }

    @Test void reusesBoundsAndArchivesWithoutDeletingHistory() {
        var now = Instant.parse("2026-07-29T00:00:00Z");
        store.save(new Session("s", "a", "cli", "u", List.of(),
                Session.Status.ACTIVE, now, now, null, 0));
        for (int i = 1; i <= 105; i++) {
            store.append("s", new Message("m" + i, i, Message.Role.USER,
                    "v" + i, null, null, now.plusSeconds(i)));
        }
        assertThat(store.findMessages("s", 100)).hasSize(100)
                .first().extracting(Message::content).isEqualTo("v6");
        var archived = store.archive("s", now.plusSeconds(200));
        assertThat(store.archive("s", now.plusSeconds(201))).isEqualTo(archived);
        assertThatThrownBy(() -> store.append("s", new Message(
                "m106", 106, Message.Role.USER, "no", null, null,
                now.plusSeconds(202)))).isInstanceOf(IllegalStateException.class);
    }

    @SpringBootConfiguration @EnableAutoConfiguration
    @EntityScan("org.oryxos.storage.entity")
    @EnableJpaRepositories("org.oryxos.storage.repository")
    @ComponentScan("org.oryxos.storage.adapter")
    static class App { }
}
