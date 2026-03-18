package de.palsoftware.scim.server.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"de.palsoftware.scim.server.api",
                                           "de.palsoftware.scim.server.common.repository"},
                       exclude = UserDetailsServiceAutoConfiguration.class)
@EntityScan(basePackages = {"de.palsoftware.scim.server.common.model",
                            "de.palsoftware.scim.server.api"})
@EnableJpaRepositories(basePackages = {"de.palsoftware.scim.server.common.repository"})
@EnableAsync
public class ScimServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScimServerApplication.class, args);
    }
}
