package com.ankidsa.config;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthUtil {
    private AuthUtil() {}

    public static Long getCurrentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    public static long getCurrentSessionVersion() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getDetails();
    }
}
