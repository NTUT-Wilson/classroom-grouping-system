package com.classroomgrouping.platform;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class SchedulerAuthenticationFilter extends OncePerRequestFilter {
    private final byte[] expected;

    SchedulerAuthenticationFilter(String secret) {
        expected = secret.getBytes(StandardCharsets.UTF_8);
        if (expected.length > 0 && expected.length < 32) {
            throw new IllegalArgumentException("SCHEDULER_SECRET must contain at least 32 bytes when configured");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (expected.length == 0 || header == null || !header.startsWith("Bearer ")
                || !MessageDigest.isEqual(expected, header.substring(7).getBytes(StandardCharsets.UTF_8))) {
            ProblemResponses.write(response, 401, "Unauthorized", "SCHEDULER_AUTH_REQUIRED");
            return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("scheduler", null,
                List.of(new SimpleGrantedAuthority("ROLE_SCHEDULER"))));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
