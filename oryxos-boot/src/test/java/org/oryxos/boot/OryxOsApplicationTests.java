package org.oryxos.boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.url=jdbc:sqlite:target/oryxos-boot-test.db")
class OryxOsApplicationTests {

    @Test
    void contextLoads() {
    }
}
