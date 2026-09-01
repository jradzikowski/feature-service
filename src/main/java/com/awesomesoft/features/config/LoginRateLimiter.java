package com.awesomesoft.features.config;

import com.awesomesoft.features.exception.RateLimitedException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** Fixed-window per-IP throttle for panel logins (same knobs as the audit application). */
@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final Cache<String, AtomicInteger> windows;

    public LoginRateLimiter(@Value("${features.security.login-rate-limit.max-attempts:10}") int maxAttempts,
                            @Value("${features.security.login-rate-limit.window-seconds:60}") int windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windows = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(windowSeconds))
                .maximumSize(100_000)
                .build();
    }

    public void check(String clientIp) {
        AtomicInteger counter = windows.get(clientIp, k -> new AtomicInteger());
        if (counter.incrementAndGet() > maxAttempts) {
            throw new RateLimitedException("Too many login attempts, try again later");
        }
    }
}
