package org.oryxos.web;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.oryxos.core.port.SessionStore;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SystemApiControllerTest {

    @Test
    void infoAndHealthReturnSafeEnvelopes() throws Exception {
        var environment = new MockEnvironment()
                .withProperty("DEEPSEEK_API_KEY", "configured");
        var mvc = MockMvcBuilders.standaloneSetup(new SystemApiController(
                Map.of(), mock(SessionStore.class), environment,
                Path.of(".oryxos"))).build();

        mvc.perform(get("/api/v1/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.name").value("OryxOS"))
                .andExpect(jsonPath("$.data.capabilities").isArray());
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.providers.deepseek")
                        .value("CONFIGURED"))
                .andExpect(jsonPath("$..DEEPSEEK_API_KEY").doesNotExist())
                .andExpect(jsonPath("$..configured").doesNotExist());
    }
}
