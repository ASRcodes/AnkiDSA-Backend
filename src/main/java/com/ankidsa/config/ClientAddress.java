package com.ankidsa.config;

import com.google.common.net.InetAddresses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;

@Component
public class ClientAddress {
    private static final String UNVERIFIED_RENDER_CLIENT = "render-unverified-client";
    private final List<IpAddressMatcher> trusted;
    private final boolean renderCloudflare;

    public ClientAddress(@Value("${app.trusted-proxy-cidrs:}") String cidrs,
            @Value("${app.client-ip-mode:cidr}") String mode,
            @Value("${RENDER:false}") boolean render,
            @Value("${RENDER_SERVICE_TYPE:}") String serviceType,
            @Value("${app.api-public-url:}") String apiUrl,
            @Value("${RENDER_EXTERNAL_URL:}") String externalUrl) {
        trusted = Arrays.stream(cidrs.split(",")).map(String::trim).filter(s -> !s.isEmpty())
            .map(IpAddressMatcher::new).toList();
        if (!mode.equals("cidr") && !mode.equals("render-cloudflare"))
            throw new IllegalStateException("Unsupported client IP mode.");
        renderCloudflare = mode.equals("render-cloudflare");
        if (renderCloudflare && (!render || !serviceType.equals("web") || !trusted.isEmpty()
                || !apiUrl.equals(externalUrl) || !publicRenderOrigin(apiUrl)))
            throw new IllegalStateException("Render client IP mode requires the matching public Render web service and no CIDR override.");
    }

    private static boolean publicRenderOrigin(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equals(uri.getScheme()) && uri.getHost() != null
                && uri.getHost().matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.onrender\\.com")
                && uri.getPort() == -1 && uri.getUserInfo() == null
                && uri.getRawQuery() == null && uri.getRawFragment() == null && uri.getPath().isEmpty();
        } catch (IllegalArgumentException invalid) { return false; }
    }

    private boolean trusted(String ip) { return trusted.stream().anyMatch(m -> m.matches(ip)); }

    public String resolve(HttpServletRequest request) {
        if (renderCloudflare) return renderClient(request);
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

    private String renderClient(HttpServletRequest request) {
        // Render documents that its public Cloudflare ingress overwrites this header;
        // X-Forwarded-For can retain caller-supplied entries and is deliberately ignored.
        // This mode is for public-only Render ingress; review before enabling private ingress.
        var values = request.getHeaders("CF-Connecting-IP");
        if (values == null || !values.hasMoreElements()) return UNVERIFIED_RENDER_CLIENT;
        String value = values.nextElement();
        if (values.hasMoreElements() || value == null || !value.matches("[0-9a-fA-F:.]{3,45}"))
            return UNVERIFIED_RENDER_CLIENT;
        try {
            // Guava is already supplied by Firebase Admin; this parser never performs DNS lookups.
            return InetAddresses.toAddrString(InetAddresses.forString(value));
        } catch (IllegalArgumentException invalid) {
            return UNVERIFIED_RENDER_CLIENT;
        }
    }
}
