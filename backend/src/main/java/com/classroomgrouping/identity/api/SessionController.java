package com.classroomgrouping.identity.api;

import java.security.Principal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {
    private final SessionActors actors;
    public SessionController(SessionActors actors) { this.actors = actors; }
    @GetMapping("/api/auth/csrf")
    public CsrfResponse csrf(CsrfToken csrf) {
        return new CsrfResponse(csrf.getHeaderName(), csrf.getToken());
    }

    @GetMapping("/api/auth/session")
    public SessionActors.Actor session(Principal principal) {
        return actors.require(principal);
    }

    public record CsrfResponse(String headerName, String token) {}
}
