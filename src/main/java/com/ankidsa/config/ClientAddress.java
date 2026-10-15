package com.ankidsa.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ClientAddress {
    private final List<IpAddressMatcher> trusted;
    public ClientAddress(@Value("${app.trusted-proxy-cidrs:}") String cidrs) {
        trusted = Arrays.stream(cidrs.split(",")).map(String::trim).filter(s -> !s.isEmpty())
            .map(IpAddressMatcher::new).toList();
    }
    private boolean trusted(String ip) { return trusted.stream().anyMatch(m -> m.matches(ip)); }
    public String resolve(HttpServletRequest request) {
        String current = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (!trusted(current) || forwarded == null || forwarded.length() > 1024) return current;
        String[] chain = forwarded.split(",");
        for (int i = chain.length - 1; i >= 0 && trusted(current); i--) {
            String next = chain[i].trim();
            if (!next.matches("[0-9a-fA-F:.]{3,45}")) return current;
            try { new IpAddressMatcher(next); current = next; } catch (IllegalArgumentException e) { return current; }
        }
        return current;
    }
}
