package org.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.oryxos.memory.mem0.Mem0MemoryStore;

class Mem0MemoryStoreTest {
    @Test void satisfiesSharedContractAgainstSelfHostedStub() throws Exception {
        var mapper = new ObjectMapper();
        var values = new ArrayList<com.fasterxml.jackson.databind.node.ObjectNode>();
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/memories/", exchange -> {
            authorization.set(exchange.getRequestHeaders()
                    .getFirst("Authorization"));
            if ("POST".equals(exchange.getRequestMethod())) {
                var input = mapper.readTree(exchange.getRequestBody());
                var item = mapper.createObjectNode();
                item.put("id", "m" + values.size());
                item.put("memory", input.path("memory").asText());
                item.set("metadata", input.path("metadata"));
                values.add(item);
                respond(exchange, "{}", 200);
            } else {
                respond(exchange, mapper.writeValueAsString(values), 200);
            }
        });
        server.createContext("/v1/memories/search/", exchange ->
                respond(exchange, mapper.writeValueAsString(values), 200));
        server.start();
        try {
            var store = new Mem0MemoryStore(URI.create(
                    "http://localhost:" + server.getAddress().getPort()),
                    "test-token", mapper);
            LongTermMemoryStoreContract.verify(store);
            assertThat(store.load()).hasSize(10);
            assertThat(authorization).hasValue("Bearer test-token");
        } finally {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange,
            String body, int status) throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
