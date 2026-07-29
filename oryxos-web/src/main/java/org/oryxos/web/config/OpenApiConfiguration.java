package org.oryxos.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI oryxOsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("OryxOS Core Runtime API")
                .version("1.0.0")
                .description("Core-stage single-node API; all Agent execution "
                        + "paths converge on AgentService."));
    }
}
