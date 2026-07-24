package org.oryxos.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * OryxOS executable application.
 *
 * <p>Database auto-configuration is temporarily disabled until the first
 * storage user story supplies the SQLite schema and dialect configuration.</p>
 */
@SpringBootApplication(
        scanBasePackages = "org.oryxos",
        exclude = {
                DataSourceAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class
        }
)
public class OryxOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(OryxOsApplication.class, args);
    }
}

