package com.scimplayground.mgmt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.scimplayground.mgmt", "com.scimplayground.server.repository"})
@EntityScan(basePackages = {"com.scimplayground.server.model", "com.scimplayground.mgmt.model"})
@EnableJpaRepositories(basePackages = {"com.scimplayground.server.repository", "com.scimplayground.mgmt.repository"})
public class ScimServerManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScimServerManagementApplication.class, args);
    }
}
