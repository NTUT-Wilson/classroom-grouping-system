package com.classroomgrouping.identity.internal;

import com.classroomgrouping.identity.api.SessionActors;
import java.security.Principal;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;

@Service
class PersistedSessionActors implements SessionActors {
    private final AccountRepository accounts;
    PersistedSessionActors(AccountRepository accounts) { this.accounts = accounts; }
    public Actor require(Principal principal) {
        if (principal == null) throw new AuthenticationCredentialsNotFoundException("Authentication required");
        return accounts.findByUsername(principal.getName())
                .map(a -> new Actor(a.getId(), a.getUsername(), a.getRole()))
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Authentication required"));
    }
}
