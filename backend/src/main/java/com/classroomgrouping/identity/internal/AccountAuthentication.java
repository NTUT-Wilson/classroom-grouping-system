package com.classroomgrouping.identity.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class AccountAuthentication {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService accountUsers(AccountRepository accounts) {
        return username -> accounts.findByUsername(username)
                .map(account -> User.withUsername(account.getUsername())
                        .password(account.getPasswordHash()).roles(account.getRole()).build())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
