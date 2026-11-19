package com.exentricdev.clipshare.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Keycloak realm roles, mapped to Spring authorities ROLE_USER / ROLE_ADMIN (see SecurityConfig).
 * Every self-registered account gets "user" through the realm's default roles; "admin" is assigned by hand.
 */
public final class Roles {
    public static final String USER = "USER";
    public static final String ADMIN = "ADMIN";

    private Roles() {
    }

    public static boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> ("ROLE_" + ADMIN).equals(a.getAuthority()));
    }

    /** The caller's user id (the JWT subject), or null when the request is anonymous. */
    public static String userId(Authentication auth) {
        return auth != null && auth.getPrincipal() instanceof Jwt jwt ? jwt.getSubject() : null;
    }
}
