package com.upnext.web.security;

import com.upnext.domain.Actor;
import com.upnext.web.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Reads the signed-in user, if any. Browse endpoints work for visitors; participate and admin endpoints require one. */
public final class CurrentUser {
    private CurrentUser() {}

    public static Optional<UserPrincipal> optional() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof UserPrincipal p ? Optional.of(p) : Optional.empty();
    }

    public static UserPrincipal required() {
        return optional().orElseThrow(() -> new IllegalStateException("No signed-in user on a protected route"));
    }

    /** The viewer id for "did I vote?". Null for visitors. */
    public static UUID viewerId() { return optional().map(UserPrincipal::id).orElse(null); }

    public static Actor actor() {
        UserPrincipal p = required();
        return new Actor(p.id(), p.displayName() + " (" + p.role().name().toLowerCase(Locale.ROOT) + ")", MDC.get(RequestIdFilter.MDC_KEY));
    }
}
