package org.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleBoundaryTest {

    @Test
    void reactorKeepsNineModulesOneToolModuleAndOneExecutable() throws Exception {
        var root = repositoryRoot();
        var rootPom = Files.readString(root.resolve("pom.xml"));
        var modules = List.of("oryxos-core", "oryxos-provider", "oryxos-memory",
                "oryxos-tool", "oryxos-channel-cli", "oryxos-web",
                "oryxos-storage", "oryxos-cli", "oryxos-boot");
        assertThat(modules).allSatisfy(module ->
                assertThat(rootPom).contains("<module>" + module + "</module>"));
        assertThat(rootPom).doesNotContain("oryxos-tool-builtin",
                "oryxos-tool-mcp", "oryxos-agent");
        assertThat(Files.readString(root.resolve("oryxos-cli/pom.xml")))
                .doesNotContain("maven-shade-plugin");
        assertThat(Files.readString(root.resolve("oryxos-boot/pom.xml")))
                .contains("spring-boot-maven-plugin");
    }

    @Test
    void coreHasNoImplementationModuleDependency() throws Exception {
        var corePom = Files.readString(repositoryRoot().resolve("oryxos-core/pom.xml"));
        assertThat(corePom).doesNotContain("<artifactId>oryxos-provider</artifactId>",
                "<artifactId>oryxos-memory</artifactId>",
                "<artifactId>oryxos-tool</artifactId>",
                "<artifactId>oryxos-storage</artifactId>");
    }

    private Path repositoryRoot() {
        var path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("specs/003-core-runtime"))) {
            path = path.getParent();
        }
        if (path == null) {
            throw new IllegalStateException("Repository root not found");
        }
        return path;
    }
}
