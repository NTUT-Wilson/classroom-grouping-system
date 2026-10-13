package com.classroomgrouping.platform;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;

class SecurityUnitTest {
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void schedulerRejectsMissingInvalidOrUnconfiguredSecrets() throws Exception {
        for (String configured : new String[]{"", "test-secret-with-at-least-thirty-two-bytes"}) {
            var filter = new SchedulerAuthenticationFilter(configured);
            for (String supplied : new String[]{"", "Bearer wrong"}) {
                var request = new MockHttpServletRequest("POST", "/internal/scheduler/tick");
                request.addHeader("Authorization", supplied);
                var response = new MockHttpServletResponse();
                filter.doFilter(request, response, (req, res) -> fail("Unauthorized invocation reached core"));
                assertEquals(401, response.getStatus());
                assertNull(SecurityContextHolder.getContext().getAuthentication());
            }
        }
    }

    @Test void schedulerAuthenticatesOnlyConfiguredBearerSecret() throws Exception {
        String secret = "test-secret-with-at-least-thirty-two-bytes";
        var request = new MockHttpServletRequest("POST", "/internal/scheduler/tick");
        request.addHeader("Authorization", "Bearer " + secret);
        var invoked = new AtomicBoolean();
        new SchedulerAuthenticationFilter(secret).doFilter(request, new MockHttpServletResponse(),
                (req, res) -> invoked.set(true));
        assertTrue(invoked.get());
        assertEquals("scheduler", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test void loginThrottleIsTemporaryAndDoesNotApplyToReads() throws Exception {
        var filter = new LoginThrottleFilter(1);
        var first = new MockHttpServletRequest("POST", "/api/auth/login");
        first.setServletPath("/api/auth/login");
        filter.doFilter(first, new MockHttpServletResponse(), (req, res) -> {});
        var next = new MockHttpServletRequest("POST", "/api/auth/login");
        next.setServletPath("/api/auth/login");
        var response = new MockHttpServletResponse();
        filter.doFilter(next, response, (req, res) -> fail("Excessive login should be throttled"));
        assertEquals(429, response.getStatus());
        var read = new MockHttpServletRequest("GET", "/api/auth/session");
        filter.doFilter(read, new MockHttpServletResponse(), (req, res) -> {});
    }
}
