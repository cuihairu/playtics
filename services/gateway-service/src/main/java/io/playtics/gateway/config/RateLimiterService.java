package io.playtics.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimiterService {
    private final int rpm;
    private final Map<String, Window> buckets = new ConcurrentHashMap<>();

    public RateLimiterService(@Value("${playtics.ratelimit.rpm:600}") int rpm) {
        this.rpm = rpm;
    }

    public boolean allow(String key) {
        long minute = Instant.now().getEpochSecond() / 60L;
        Window w = buckets.computeIfAbsent(key, k -> new Window(minute));
        synchronized (w) {
            if (w.window != minute) { w.window = minute; w.counter.set(0); }
            if (w.counter.incrementAndGet() > rpm) return false;
            return true;
        }
    }

    private static class Window {
        long window; AtomicInteger counter = new AtomicInteger();
        Window(long w) { this.window = w; }
    }
}
