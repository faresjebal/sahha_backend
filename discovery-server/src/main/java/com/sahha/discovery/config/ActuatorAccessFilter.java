package com.sahha.discovery.config;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Restricts only Actuator; existing Eureka/Config protocols are unchanged. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ActuatorAccessFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_READS = Set.of("/actuator/health",
            "/actuator/health/liveness", "/actuator/health/readiness", "/actuator/info");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        if ((path.equals("/actuator") || path.startsWith("/actuator/"))
                && !("GET".equals(request.getMethod()) && PUBLIC_READS.contains(path))) {
            response.setStatus(403);
            return;
        }
        chain.doFilter(request, response);
    }
}
