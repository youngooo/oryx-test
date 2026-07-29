package org.oryxos.web.config;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InvocationTimeoutConfiguration implements WebMvcConfigurer {
    public static final Duration INVOCATION_TIMEOUT = Duration.ofSeconds(60);

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(INVOCATION_TIMEOUT.toMillis());
    }
}
