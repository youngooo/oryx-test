package org.oryxos.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.oryxos.core.memory.LongTermMemoryView;
import org.oryxos.core.memory.MemoryScope;
import org.oryxos.core.model.AgentDefinition;
import org.oryxos.core.model.Profile;
import org.oryxos.core.model.ToolDefinition;
import org.oryxos.core.port.MemoryService;
import org.oryxos.core.port.ToolCatalog;
import org.oryxos.web.controller.MemoryApiController;
import org.oryxos.web.controller.ProfileApiController;
import org.oryxos.web.controller.ToolApiController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReadApiControllerTest {

    @Test
    void exposesProfilesMemoryAndToolsWithoutRuntimeSecrets() throws Exception {
        var profile = new Profile("digest", "deepseek", "deepseek-chat",
                Set.of("read_file"), Set.of("api"), null, List.of(), 10, null);
        var definition = mock(AgentDefinition.class);
        when(definition.loadStatus()).thenReturn(AgentDefinition.LoadStatus.VALID);
        when(definition.profile()).thenReturn(profile);
        var memory = mock(MemoryService.class);
        when(memory.loadLongTerm()).thenReturn(List.of(
                new LongTermMemoryView("m1", MemoryScope.CORE,
                        "Spring Boot on K8s", Instant.parse(
                                "2026-07-29T02:00:00Z"))));
        var tools = mock(ToolCatalog.class);
        when(tools.definitions()).thenReturn(List.of(new ToolDefinition(
                "read_file", "Read a file",
                new ObjectMapper().createObjectNode(),
                ToolDefinition.Origin.BUILT_IN, "oryxos-tool")));
        var mvc = MockMvcBuilders.standaloneSetup(
                new ProfileApiController(Map.of("digest", definition)),
                new MemoryApiController(memory),
                new ToolApiController(tools)).build();

        mvc.perform(get("/api/v1/profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("digest"));
        mvc.perform(get("/api/v1/memory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.core").value("Spring Boot on K8s"));
        mvc.perform(get("/api/v1/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("read_file"))
                .andExpect(jsonPath("$..apiKey").doesNotExist());
    }
}
