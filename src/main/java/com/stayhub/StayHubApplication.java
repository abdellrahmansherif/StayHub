package com.stayhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "com.stayhub", "com.common", "com.identity", "com.hostverification"
})
@EntityScan(basePackages = {"com.identity", "com.hostverification"})
@EnableJpaRepositories(basePackages = {"com.identity", "com.hostverification"})
public class StayHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(StayHubApplication.class, args);
    }

}
