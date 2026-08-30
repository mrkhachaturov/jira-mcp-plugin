package com.atlassian.mcp.plugin.rest;

import jakarta.inject.Named;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple IP-based rate limiter with time-bucketed counters. Each bucket covers one minute. The IP
 * map is capped and cleared periodically.
 */
@Named
public class RateLimiter {

  private static final int MAX_TRACKED_IPS = 10_000;
  private static final long BUCKET_MS = 60_000; // 1 minute

  private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
  private final AtomicLong currentBucket = new AtomicLong(System.currentTimeMillis() / BUCKET_MS);

  /**
   * Snapshot of current rate-limit state for a given bucket key + endpoint, used to emit
   * RateLimit-* response headers per <a
   * href="https://datatracker.ietf.org/doc/draft-ietf-httpapi-ratelimit-headers/">draft-ietf-httpapi-ratelimit-headers-09</a>.
   */
  public static final class Snapshot {
    public final int limit;
    public final int remaining;
    public final long resetSeconds;

    public Snapshot(int limit, int remaining, long resetSeconds) {
      this.limit = limit;
      this.remaining = remaining;
      this.resetSeconds = resetSeconds;
    }
  }

  /**
   * Check if a request from the given IP to the given endpoint is allowed.
   *
   * @param ip remote IP address
   * @param endpoint logical endpoint name (e.g. "register", "token", "mcp")
   * @param maxPerMin maximum requests per minute
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

  /**
   * Read-only inspection of the current bucket state for the given key. Does NOT consume a slot.
   * Intended for emitting RateLimit-* response headers after a successful consume.
   *
   * <p>The returned {@code resetSeconds} is the time (in seconds) until the current one-minute
   * window rolls over. Remaining is {@code max(0, maxPerMin - used)} where {@code used} is the
   * current counter value (clamped at {@code maxPerMin}).
   */
  public Snapshot snapshot(String ip, String endpoint, int maxPerMin) {
    long now = System.currentTimeMillis();
    long bucket = now / BUCKET_MS;
    long resetSeconds = Math.max(0L, ((bucket + 1) * BUCKET_MS - now + 999) / 1000);

    // If the bucket has rolled over since the last consume, snapshot reflects a fresh window.
    if (bucket != currentBucket.get()) {
      return new Snapshot(maxPerMin, maxPerMin, resetSeconds);
    }

    String key = ip + ":" + endpoint;
    AtomicInteger counter = counters.get(key);
    int used = counter == null ? 0 : counter.get();
    int remaining = Math.max(0, maxPerMin - used);
    return new Snapshot(maxPerMin, remaining, resetSeconds);
  }
}
