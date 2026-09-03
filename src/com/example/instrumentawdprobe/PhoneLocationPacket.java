package com.example.instrumentawdprobe;

import android.location.Location;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Signed, compact UDP protocol shared with the Phone GPS Bridge companion. */
final class PhoneLocationPacket {
    static final int PORT = 44888;
    private static final int VERSION = 1;
    private static final int MAX_PACKET_BYTES = 1024;

    static final class Sample {
        final String session;
        final long sequence;
        final long phoneElapsedMs;
        final int latitudeE7;
        final int longitudeE7;
        final int accuracyCm;
        final int speedCmPerSecond;
        final int bearingCentiDegrees;

        Sample(String session, long sequence, long phoneElapsedMs, int latitudeE7,
               int longitudeE7, int accuracyCm, int speedCmPerSecond,
               int bearingCentiDegrees) {
            this.session = session;
            this.sequence = sequence;
            this.phoneElapsedMs = phoneElapsedMs;
            this.latitudeE7 = latitudeE7;
            this.longitudeE7 = longitudeE7;
            this.accuracyCm = accuracyCm;
            this.speedCmPerSecond = speedCmPerSecond;
            this.bearingCentiDegrees = bearingCentiDegrees;
        }

        Location toLocation() {
            Location location = new Location("phone-gps");
            location.setLatitude(latitudeE7 / 10000000.0);
            location.setLongitude(longitudeE7 / 10000000.0);
            if (accuracyCm >= 0) location.setAccuracy(accuracyCm / 100f);
            if (speedCmPerSecond >= 0) location.setSpeed(speedCmPerSecond / 100f);
            if (bearingCentiDegrees >= 0) {
                location.setBearing(bearingCentiDegrees / 100f);
            }
            // Phone elapsedRealtime belongs to a different device. The receiving
            // side must use its local monotonic clock to assess packet freshness.
            location.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos());
            return location;
        }
    }

    private PhoneLocationPacket() { }

    static Sample decode(byte[] bytes, int length, String pairingToken) throws Exception {
        if (bytes == null || length <= 0 || length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("packet size");
        }
        JSONObject json = new JSONObject(new String(bytes, 0, length, StandardCharsets.UTF_8));
        int version = json.getInt("v");
        String session = json.getString("s");
        long sequence = json.getLong("q");
        long phoneElapsedMs = json.getLong("e");
        int latitudeE7 = json.getInt("la");
        int longitudeE7 = json.getInt("lo");
        int accuracyCm = json.optInt("ac", -1);
        int speedCmPerSecond = json.optInt("sp", -1);
        int bearingCentiDegrees = json.optInt("be", -1);
        String signature = json.getString("h");
        if (version != VERSION || session.length() < 8 || session.length() > 64
                || sequence < 0 || phoneElapsedMs < 0
                || Math.abs(latitudeE7) > 900000000 || Math.abs(longitudeE7) > 1800000000
                || accuracyCm > 500000 || speedCmPerSecond > 800000
                || bearingCentiDegrees > 36000) {
            throw new IllegalArgumentException("packet values");
        }
        String expected = signature(pairingToken, canonical(session, sequence, phoneElapsedMs,
                latitudeE7, longitudeE7, accuracyCm, speedCmPerSecond, bearingCentiDegrees));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII))) {
            throw new SecurityException("pairing signature");
        }
        return new Sample(session, sequence, phoneElapsedMs, latitudeE7, longitudeE7,
                accuracyCm, speedCmPerSecond, bearingCentiDegrees);
    }

    static String canonical(String session, long sequence, long phoneElapsedMs, int latitudeE7,
                            int longitudeE7, int accuracyCm, int speedCmPerSecond,
                            int bearingCentiDegrees) {
        return VERSION + "|" + session + "|" + sequence + "|" + phoneElapsedMs + "|"
                + latitudeE7 + "|" + longitudeE7 + "|" + accuracyCm + "|"
                + speedCmPerSecond + "|" + bearingCentiDegrees;
    }

    static String signature(String pairingToken, String canonical) throws Exception {
        if (pairingToken == null || pairingToken.length() < 12) {
            throw new SecurityException("pairing token");
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(pairingToken.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    }
}
