package org.oryxos.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class OpenApiContractTest {

    @Test
    void contractPublishesExactlyTenOperationsAndAuditProjection() throws Exception {
        var contract = locateContract();
        var yaml = Files.readString(contract);
        var operationIds = Pattern.compile("(?m)^\\s+operationId:")
                .matcher(yaml).results().count();

        assertThat(operationIds).isEqualTo(10);
        assertThat(yaml).contains("SessionDetail:", "llmCallsPage:",
                "toolInvocationsPage:", "PageMetadata:");
    }

    private Path locateContract() {
        var current = Path.of("").toAbsolutePath().normalize();
        for (var candidate = current; candidate != null;
                candidate = candidate.getParent()) {
            var contract = candidate.resolve(
                    "specs/003-core-runtime/contracts/openapi.yaml");
            if (Files.isRegularFile(contract)) {
                return contract;
            }
        }
        throw new IllegalStateException("OpenAPI contract not found");
    }
}
