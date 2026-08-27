package com.example.instrumentawdprobe;

public final class NavigatorRouteFreshnessPolicyTest {
    public static void main(String[] args) {
        activeRouteExpiresAfterNavigatorSilence();
        recentlyUpdatedRouteRemainsVisible();
        emptyRouteNeverNeedsExpiration();
        elapsedRealtimeResetExpiresOldStateSafely();
        System.out.println("Navigator route freshness policy OK");
    }

    private static void activeRouteExpiresAfterNavigatorSilence() {
        long now = NavigatorRouteFreshnessPolicy.MAX_LIVE_ROUTE_SILENCE_MS + 2L;
        assertValue(true, NavigatorRouteFreshnessPolicy.isExpired(true, 1L, now));
    }

    private static void recentlyUpdatedRouteRemainsVisible() {
        assertValue(false, NavigatorRouteFreshnessPolicy.isExpired(true, 100L, 200L));
    }

    private static void emptyRouteNeverNeedsExpiration() {
        assertValue(false, NavigatorRouteFreshnessPolicy.isExpired(false, 1L,
                NavigatorRouteFreshnessPolicy.MAX_LIVE_ROUTE_SILENCE_MS + 2L));
    }

    private static void elapsedRealtimeResetExpiresOldStateSafely() {
        assertValue(true, NavigatorRouteFreshnessPolicy.isExpired(true, 200L, 100L));
    }

    private static void assertValue(boolean expected, boolean actual) {
        if (expected != actual) {
            throw new AssertionError("expected " + expected + ", actual " + actual);
        }
    }
}
