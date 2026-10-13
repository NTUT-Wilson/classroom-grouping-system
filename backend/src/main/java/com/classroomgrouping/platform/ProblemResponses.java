package com.classroomgrouping.platform;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

final class ProblemResponses {
    private ProblemResponses() {}

    static void write(HttpServletResponse response, int status, String title, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        // Arguments are fixed application constants, never request input or exception messages.
        response.getWriter().write("{\"type\":\"about:blank\",\"status\":" + status
                + ",\"title\":\"" + title + "\",\"code\":\"" + code + "\"}");
    }
}
