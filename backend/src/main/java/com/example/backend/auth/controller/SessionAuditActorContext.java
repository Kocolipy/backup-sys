package com.example.backend.auth.controller;

import com.example.backend.audit.domain.AuditActorContext;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The audit trail's {@link AuditActorContext}: the owner of the session the current request is
 * served under, read through the signed-in session module ({@link HttpSessionAttributes}), so a
 * session nobody signed in to, or one whose owner is malformed, names nobody here as everywhere.
 * It never creates a session.
 */
@Component
class SessionAuditActorContext implements AuditActorContext {

    @Override
    public Optional<UUID> signedInUser() {
        if (!(RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes servlet)) {
            return Optional.empty();
        }
        HttpSession session = servlet.getRequest().getSession(false);
        return session == null ? Optional.empty() : HttpSessionAttributes.signedIn(session).owner();
    }
}
