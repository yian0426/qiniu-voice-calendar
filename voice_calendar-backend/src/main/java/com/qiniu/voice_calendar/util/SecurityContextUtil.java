package com.qiniu.voice_calendar.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility to extract current user info from Spring Security context.
 * After JwtAuthenticationFilter sets the authentication,
 * the principal is the userId and details is the username.
 */
public class SecurityContextUtil {

    private SecurityContextUtil() {}

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("User not authenticated");
        }
        return (Long) auth.getPrincipal();
    }

    public static String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("User not authenticated");
        }
        return (String) auth.getDetails();
    }
}
