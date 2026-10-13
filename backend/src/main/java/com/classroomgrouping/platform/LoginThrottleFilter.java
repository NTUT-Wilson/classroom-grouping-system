package com.classroomgrouping.platform;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded, temporary IP throttling; no permanent lockouts or additional infrastructure. */
final class LoginThrottleFilter extends OncePerRequestFilter {
    private final Map<String, Window> windows = new HashMap<>();
    private final int maxAttempts;
    private record Window(long started, int attempts) {}

    LoginThrottleFilter(int maxAttempts) { this.maxAttempts = maxAttempts; }

    private synchronized boolean allowed(String address) {
        long now = System.nanoTime();
        long duration = 60_000_000_000L;
        windows.entrySet().removeIf(entry -> now - entry.getValue().started() >= duration);
        Window current = windows.get(address);
        if (current == null && windows.size() >= 1024) { return false; }
        if (current != null && current.attempts() >= maxAttempts) { return false; }
        windows.put(address, current == null ? new Window(now, 1)
                : new Window(current.started(), current.attempts() + 1));
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && "/api/auth/login".equals(request.getServletPath())
                && !allowed(request.getRemoteAddr())) {
            response.setHeader("Retry-After", "60");
            ProblemResponses.write(response, 429, "Too many requests", "LOGIN_RATE_LIMITED");
            return;
        }
        chain.doFilter(request, response);
    }
}
