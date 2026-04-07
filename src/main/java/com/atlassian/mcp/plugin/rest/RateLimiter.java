package com.atlassian.mcp.plugin.rest;

import javax.inject.Named;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple IP-based rate limiter with time-bucketed counters.
 * Each bucket covers one minute. The IP map is capped and cleared periodically.
 */
@Named
public class RateLimiter {

    private static final int MAX_TRACKED_IPS = 10_000;
    private static final long BUCKET_MS = 60_000; // 1 minute

    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final AtomicLong currentBucket = new AtomicLong(System.currentTimeMillis() / BUCKET_MS);

    /**
     * Check if a request from the given IP to the given endpoint is allowed.
     *
     * @param ip         remote IP address
     * @param endpoint   logical endpoint name (e.g. "register", "token", "mcp")
     * @param maxPerMin  maximum requests per minute
     * @return true if allowed, false if rate limited
     */
    public boolean isAllowed(String ip, String endpoint, int maxPerMin) {
        long bucket = System.currentTimeMillis() / BUCKET_MS;
        if (bucket != currentBucket.get()) {
            // New minute — clear all counters
            counters.clear();
            currentBucket.set(bucket);
        }

        // Cap tracked IPs to prevent memory exhaustion
        if (counters.size() >= MAX_TRACKED_IPS) {
            // Under attack — reject new IPs, allow existing
            String key = ip + ":" + endpoint;
            AtomicInteger existing = counters.get(key);
            if (existing == null) {
                return false;
            }
            return existing.incrementAndGet() <= maxPerMin;
        }

        String key = ip + ":" + endpoint;
        AtomicInteger counter = counters.computeIfAbsent(key, k -> new AtomicInteger(0));
        return counter.incrementAndGet() <= maxPerMin;
    }
}
