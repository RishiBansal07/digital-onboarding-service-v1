package com.randombank.onboarding.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of("/register", "/login", "/overview");

    private final AtomicLong currentEpochSecond = new AtomicLong(Instant.now().getEpochSecond());
    private final AtomicInteger requestCounter = new AtomicInteger(0);

    @Value("${app.db.max-requests-per-second:2}")
    private int maxRequestsPerSecond;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!tryConsume()) {
            log.warn("Rate limit exceeded: path={}", request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"Rate limit exceeded. Please retry shortly.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private synchronized boolean tryConsume() {
        long now = Instant.now().getEpochSecond();
        long activeWindow = currentEpochSecond.get();

        if (now != activeWindow) {
            currentEpochSecond.set(now);
            requestCounter.set(0);
        }

        if (requestCounter.get() >= maxRequestsPerSecond) {
            return false;
        }

        requestCounter.incrementAndGet();
        return true;
    }
}

