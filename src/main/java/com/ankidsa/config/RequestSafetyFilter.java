package com.ankidsa.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSafetyFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        res.setHeader("X-Request-ID", id);
        res.setHeader("Cache-Control", "no-store");
        MDC.put("requestId", id);
        try {
            if (req.getContentLengthLong() > 65536) {
                res.setStatus(413); res.setContentType("application/json");
                res.getWriter().write("{\"message\":\"This request is too large.\"}"); return;
            }
            chain.doFilter(new HttpServletRequestWrapper(req) {
                @Override public ServletInputStream getInputStream() throws IOException {
                    ServletInputStream source = super.getInputStream();
                    return new ServletInputStream() {
                        int read;
                        public int read() throws IOException {
                            if (++read > 65536) throw new IOException("Request size limit reached");
                            return source.read();
                        }
                        public boolean isFinished() { return source.isFinished(); }
                        public boolean isReady() { return source.isReady(); }
                        public void setReadListener(ReadListener listener) { source.setReadListener(listener); }
                    };
                }
            }, res);
        } finally { MDC.remove("requestId"); }
    }
}
