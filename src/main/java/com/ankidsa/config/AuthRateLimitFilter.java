package com.ankidsa.config;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
 private final ClientAddress address;
 public AuthRateLimitFilter(ClientAddress address) { this.address = address; }
 private record Window(long start,int count) {}
 private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
 private static final long PERIOD=600_000L;
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
  if(!req.getMethod().equals("POST") || !req.getRequestURI().startsWith("/api/auth/")) {chain.doFilter(req,res);return;}
  long now=System.currentTimeMillis();
  windows.entrySet().removeIf(e -> now-e.getValue().start()>PERIOD);
  // Do not trust caller-supplied forwarding headers.
  if(windows.size()>10000) {res.setStatus(503);res.setContentType("application/json");res.getWriter().write("{\"message\":\"Please retry shortly.\"}");return;}
  Window w=windows.compute(address.resolve(req),(key,old)->old==null || now-old.start()>PERIOD ? new Window(now,1):new Window(old.start(),old.count()+1));
  if(w.count()>30) {
   res.setStatus(429);res.setHeader("Retry-After","600");res.setContentType("application/json");
   res.getWriter().write("{\"message\":\"Too many account requests. Please wait a few minutes.\"}");return;
  }
  chain.doFilter(req,res);
 }
}
