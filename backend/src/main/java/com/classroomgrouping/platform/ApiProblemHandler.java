package com.classroomgrouping.platform;

import com.classroomgrouping.courses.api.CourseNotAccessible;
import com.classroomgrouping.courses.api.ProfileValidationException;
import com.classroomgrouping.grouping.api.ActivityConflict;
import com.classroomgrouping.grouping.api.ActivityValidationException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.databind.ObjectMapper;

@RestControllerAdvice
class ApiProblemHandler {
    private final ObjectMapper mapper;
    ApiProblemHandler(ObjectMapper mapper) { this.mapper = mapper; }

    @ExceptionHandler(ActivityValidationException.class)
    void invalidActivity(ActivityValidationException exception, HttpServletResponse response) throws IOException {
        response.setStatus(400);
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(mapper.writeValueAsString(Map.of("type", "about:blank", "status", 400,
                "title", "Invalid activity", "code", exception.code(),
                "fieldErrors", Map.of(exception.field(), exception.safeMessage()))));
    }

    @ExceptionHandler(ActivityConflict.class)
    void activityConflict(ActivityConflict exception, HttpServletResponse response) throws IOException {
        response.setStatus(409);
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(mapper.writeValueAsString(Map.of("type", "about:blank", "status", 409,
                "title", "Activity conflict", "code", exception.code(), "detail", exception.safeMessage())));
    }
    @ExceptionHandler(ProfileValidationException.class)
    void invalidProfile(HttpServletResponse response) throws IOException {
        response.setStatus(400);
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"type\":\"about:blank\",\"status\":400,\"title\":\"Invalid introduction\","
                + "\"code\":\"INVALID_ABOUT_ME\",\"fieldErrors\":{\"aboutMe\":\""
                + ProfileValidationException.MESSAGE + "\"}}");
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    void invalidBody(HttpServletResponse response) throws IOException {
        ProblemResponses.write(response, 400, "Invalid request body", "INVALID_REQUEST");
    }
    @ExceptionHandler(CourseNotAccessible.class)
    void inaccessible(HttpServletResponse response) throws IOException {
        ProblemResponses.write(response, 404, "Course not accessible", "COURSE_NOT_ACCESSIBLE");
    }
    @ExceptionHandler(AuthenticationException.class)
    void authentication(HttpServletResponse response) throws IOException {
        ProblemResponses.write(response, 401, "Authentication required", "AUTH_REQUIRED");
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    void invalidIdentifier(HttpServletResponse response) throws IOException {
        ProblemResponses.write(response, 404, "Course not accessible", "COURSE_NOT_ACCESSIBLE");
    }
}
