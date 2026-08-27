package com.example.instrumentawdprobe;

/** Prevents an abandoned Navigator session from remaining in the cluster widget. */
final class NavigatorRouteFreshnessPolicy {
    static final long MAX_LIVE_ROUTE_SILENCE_MS = 5L * 60L * 1000L;
    static final long WATCHDOG_INTERVAL_MS = 30L * 1000L;

    private NavigatorRouteFreshnessPolicy() { }

    static boolean isExpired(boolean hasRouteState, long lastUpdateElapsedMs,
                             long nowElapsedMs) {
        if (!hasRouteState || lastUpdateElapsedMs <= 0L) return false;
        long silence = nowElapsedMs - lastUpdateElapsedMs;
        return silence < 0L || silence > MAX_LIVE_ROUTE_SILENCE_MS;
    }
}
