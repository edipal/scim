package de.palsoftware.scim.server.mgmt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"de.palsoftware.scim.server.mgmt",
                                           "de.palsoftware.scim.server.common.repository"})
@EntityScan(basePackages = {"de.palsoftware.scim.server.common.model",
                            "de.palsoftware.scim.server.mgmt.model"
})
@EnableJpaRepositories(basePackages = {"de.palsoftware.scim.server.common.repository",
                                       "de.palsoftware.scim.server.mgmt.repository"
})
public class ScimServerManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScimServerManagementApplication.class, args);
    }
}
