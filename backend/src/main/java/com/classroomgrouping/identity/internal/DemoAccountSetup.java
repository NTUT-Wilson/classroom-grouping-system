package com.classroomgrouping.identity.internal;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Explicit opt-in foundation account only; no course or grouping data is created. */
@Configuration
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
class DemoAccountSetup {
    @Bean
    ApplicationRunner seedDemoAccount(AccountRepository accounts, PasswordEncoder encoder,
            @Value("${app.demo.username}") String username,
            @Value("${app.demo.password}") String password) {
        if (username.isBlank() || username.length() > 100
                || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Opt-in demo setup needs a username and a 12+ character password within bcrypt's 72-byte limit");
        }
        return args -> {
            if (accounts.findByUsername(username).isEmpty()) {
                accounts.save(new Account(username, encoder.encode(password), "STUDENT"));
            }
        };
    }
}
