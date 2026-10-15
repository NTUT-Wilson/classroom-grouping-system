package com.classroomgrouping.platform;

import com.classroomgrouping.courses.api.CourseNotAccessible;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ApiProblemHandler {
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
