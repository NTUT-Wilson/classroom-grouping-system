package com.classroomgrouping.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
class SecurityConfiguration {
    @Bean
    @Order(1)
    SecurityFilterChain schedulerChain(HttpSecurity http,
            @Value("${app.scheduler.secret}") String secret) throws Exception {
        return http.securityMatcher("/internal/scheduler/**")
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .addFilterAfter(new SchedulerAuthenticationFilter(secret), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/internal/scheduler/tick").hasRole("SCHEDULER")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                ProblemResponses.write(response, 401, "Unauthorized", "SCHEDULER_AUTH_REQUIRED"))
                        .accessDeniedHandler((request, response, exception) ->
                                ProblemResponses.write(response, 403, "Forbidden", "ACCESS_DENIED")))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain browserChain(HttpSecurity http,
            @Value("${app.security.login-attempts-per-minute:100}") int loginAttempts) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/favicon.ico",
                                "/api/auth/csrf", "/actuator/health", "/actuator/health/liveness",
                                "/actuator/health/readiness").permitAll()
                        .requestMatchers("/api/auth/login", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/session").authenticated()
                        .anyRequest().denyAll())
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .addFilterBefore(new LoginThrottleFilter(loginAttempts), UsernamePasswordAuthenticationFilter.class)
                .formLogin(login -> login.loginPage("/").loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) -> response.setStatus(204))
                        .failureHandler((request, response, exception) ->
                                ProblemResponses.write(response, 401, "Invalid credentials", "INVALID_CREDENTIALS")))
                .logout(logout -> logout.logoutUrl("/api/auth/logout").deleteCookies("SESSION")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                ProblemResponses.write(response, 401, "Authentication required", "AUTH_REQUIRED"))
                        .accessDeniedHandler((request, response, exception) ->
                                ProblemResponses.write(response, 403, "Forbidden", "ACCESS_DENIED")))
                .build();
    }
}
