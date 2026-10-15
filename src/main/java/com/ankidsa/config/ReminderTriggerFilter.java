package com.ankidsa.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class ReminderTriggerFilter extends OncePerRequestFilter {
    private final byte[] expected;
    public ReminderTriggerFilter(@Value("${app.reminders.trigger-secret:}") String secret) {
        expected = secret.getBytes(StandardCharsets.UTF_8);
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest req) {
        return !req.getRequestURI().equals("/internal/reminders");
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String supplied = req.getHeader("X-Reminder-Secret");
        if (!req.getMethod().equals("POST") || expected.length < 32 || supplied == null
                || supplied.length() > 256 || !MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8))) {
            res.setStatus(401); res.setContentType("application/json");
            res.getWriter().write("{\"message\":\"Job authorization required.\"}"); return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "reminder-job", null, List.of(new SimpleGrantedAuthority("REMINDER_JOB"))));
        chain.doFilter(req, res);
    }
}
