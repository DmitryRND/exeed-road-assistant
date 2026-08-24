package com.example.instrumentawdprobe;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Encodes UTF-8 navigation text exactly as the stock TXL2 HudImpl does. */
final class HudNavigationTextEncoder {
    private static final int MAX_PAYLOAD_BYTES = 68;
    private static final int FRAME_BYTES = 24;
    private static final int FIRST_FRAME_PAYLOAD_BYTES = 22;
    private static final int CONTINUATION_PAYLOAD_BYTES = 23;

    private HudNavigationTextEncoder() {}

    static byte[][] encode(String text) {
        byte[] payload = truncateUtf8(text == null ? "" : text.trim());
        if (payload.length == 0) payload = new byte[]{0x20};

        List<byte[]> frames = new ArrayList<>();
        int offset = 0;
        int sequence = 0;
        while (offset < payload.length) {
            byte[] frame = new byte[FRAME_BYTES];
            Arrays.fill(frame, (byte) 0xaa);
            frame[0] = (byte) sequence;
            int destinationOffset;
            int capacity;
            if (sequence == 0) {
                frame[1] = (byte) payload.length;
                destinationOffset = 2;
                capacity = FIRST_FRAME_PAYLOAD_BYTES;
            } else {
                destinationOffset = 1;
                capacity = CONTINUATION_PAYLOAD_BYTES;
            }
            int count = Math.min(capacity, payload.length - offset);
            System.arraycopy(payload, offset, frame, destinationOffset, count);
            frames.add(frame);
            offset += count;
            sequence++;
        }
        return frames.toArray(new byte[frames.size()][]);
    }

    private static byte[] truncateUtf8(String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length <= MAX_PAYLOAD_BYTES) return encoded;

        int end = value.length();
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1);
            encoded = value.substring(0, end).getBytes(StandardCharsets.UTF_8);
            if (encoded.length <= MAX_PAYLOAD_BYTES) return encoded;
        }
        return new byte[0];
    }
}
