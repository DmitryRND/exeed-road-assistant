package com.example.instrumentawdprobe;

import android.location.Location;
import android.os.SystemClock;

/**
 * Conservative GPS sanity filter for the manual trial mode.
 *
 * It deliberately uses elapsed realtime only. Wall-clock time can be changed
 * by the vehicle system or by an external time source and must not control
 * navigation freshness or ordering.
 */
final class LocationGuard {
    static final class Result {
        final Location location;
        final String reason;

        private Result(Location location, String reason) {
            this.location = location;
            this.reason = reason;
        }

        static Result accepted(Location location) {
            return new Result(location, null);
        }

        static Result rejected(String reason) {
            return new Result(null, reason);
        }

        boolean isAccepted() {
            return location != null;
        }
    }

    private static final long MAX_AGE_MS = 10_000L;
    private static final long MAX_FUTURE_MS = 1_500L;
    private static final float MAX_ACCURACY_METERS = 500f;
    private static final float MAX_ACCELERATION_MPS2 = 8.5f;
    private static final float MAX_UNCONFIRMED_JUMP_METERS = 55f;
    private static final float MIN_REASONABLE_SPEED_MPS = 1.5f;
    private static final float MAX_REASONABLE_SPEED_MPS = 80f;

    private Location lastAccepted;
    private long lastAcceptedElapsedNanos;

    synchronized Result filter(Location candidate) {
        if (candidate == null) return Result.rejected("null location");
        if (!Double.isFinite(candidate.getLatitude())
                || !Double.isFinite(candidate.getLongitude())
                || Math.abs(candidate.getLatitude()) > 90.0
                || Math.abs(candidate.getLongitude()) > 180.0) {
            return Result.rejected("invalid coordinates");
        }
        if (candidate.hasAccuracy() && (!Float.isFinite(candidate.getAccuracy())
                || candidate.getAccuracy() > MAX_ACCURACY_METERS)) {
            return Result.rejected("accuracy=" + Math.round(candidate.getAccuracy()) + "m");
        }
        if (candidate.hasSpeed() && (!Float.isFinite(candidate.getSpeed())
                || candidate.getSpeed() < 0f
                || candidate.getSpeed() > MAX_REASONABLE_SPEED_MPS)) {
            return Result.rejected("speed=" + candidate.getSpeed() + "m/s");
        }

        long nowNanos = SystemClock.elapsedRealtimeNanos();
        long sampleNanos = candidate.getElapsedRealtimeNanos();
        if (sampleNanos <= 0L) sampleNanos = nowNanos;
        long ageMs = (nowNanos - sampleNanos) / 1_000_000L;
        if (ageMs > MAX_AGE_MS) return Result.rejected("stale=" + ageMs + "ms");
        if (ageMs < -MAX_FUTURE_MS) return Result.rejected("future=" + ageMs + "ms");

        if (lastAccepted == null) {
            return accept(candidate, sampleNanos);
        }

        long deltaNanos = sampleNanos - lastAcceptedElapsedNanos;
        if (deltaNanos <= 0L) return Result.rejected("non-monotonic elapsed time");
        float deltaSeconds = deltaNanos / 1_000_000_000f;
        if (deltaSeconds > MAX_AGE_MS / 1000f) {
            // There was an outage. The first resumed fix must be confirmed by
            // a later sample instead of causing an immediate map jump.
            lastAccepted = null;
            lastAcceptedElapsedNanos = 0L;
            return Result.rejected("resume after outage");
        }

        float distanceMeters = lastAccepted.distanceTo(candidate);
        float previousSpeed = lastAccepted.hasSpeed()
                ? Math.max(0f, lastAccepted.getSpeed()) : 0f;
        float candidateSpeed = candidate.hasSpeed()
                ? Math.max(0f, candidate.getSpeed()) : 0f;
        float speedDelta = Math.abs(candidateSpeed - previousSpeed);
        float allowedAcceleration = MAX_ACCELERATION_MPS2 * Math.max(1f, deltaSeconds) + 3f;
        if (lastAccepted.hasSpeed() && candidate.hasSpeed()
                && speedDelta > allowedAcceleration) {
            return Result.rejected("acceleration jump=" + round(speedDelta) + "m/s2");
        }

        float expectedSpeed = Math.max(previousSpeed, candidateSpeed);
        float allowedDistance = Math.max(
                MAX_UNCONFIRMED_JUMP_METERS,
                expectedSpeed * deltaSeconds + MAX_ACCELERATION_MPS2
                        * deltaSeconds * deltaSeconds * 0.5f + 12f);
        if (distanceMeters > allowedDistance) {
            return Result.rejected("position jump=" + Math.round(distanceMeters)
                    + "m allowed=" + Math.round(allowedDistance) + "m");
        }
        if (!candidate.hasSpeed() && distanceMeters / deltaSeconds
                > MAX_REASONABLE_SPEED_MPS + MIN_REASONABLE_SPEED_MPS) {
            return Result.rejected("implied speed="
                    + round(distanceMeters / deltaSeconds) + "m/s");
        }
        return accept(candidate, sampleNanos);
    }

    synchronized void reset() {
        lastAccepted = null;
        lastAcceptedElapsedNanos = 0L;
    }

    private Result accept(Location candidate, long sampleNanos) {
        lastAccepted = new Location(candidate);
        lastAcceptedElapsedNanos = sampleNanos;
        return Result.accepted(new Location(candidate));
    }

    private static String round(float value) {
        return String.format(java.util.Locale.US, "%.1f", value);
    }
}
