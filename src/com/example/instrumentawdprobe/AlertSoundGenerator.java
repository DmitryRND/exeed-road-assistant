package com.example.instrumentawdprobe;

final class AlertSoundGenerator {
    static final int TOTAL_MS = 1400;
    static final int STYLE_COUNT = 3;

    private AlertSoundGenerator() { }

    static byte[] createPcm(int sampleRate, int soundStyle) {
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate must be positive");
        int style = Math.max(0, Math.min(STYLE_COUNT - 1, soundStyle));
        int sampleCount = sampleRate * TOTAL_MS / 1000;
        byte[] pcm = new byte[sampleCount * 4];
        for (int index = 0; index < sampleCount; index++) {
            float timeMs = index * 1000f / sampleRate;
            double sample;
            if (style == 1) {
                // Quiet rounded three-note cue, kept below the level of media audio.
                sample = warmTone(timeMs, index, sampleRate,
                        80f, 440f, 622.25f, 90f, 190f) * 0.35
                        + warmTone(timeMs, index, sampleRate,
                        370f, 790f, 739.99f, 100f, 230f) * 0.33
                        + warmTone(timeMs, index, sampleRate,
                        710f, 1190f, 830.61f, 115f, 300f) * 0.31;
            } else if (style == 2) {
                // Compact two-note cue with subdued highs and long rounded tails.
                sample = warmTone(timeMs, index, sampleRate,
                        100f, 520f, 698.46f, 85f, 210f) * 0.37
                        + warmTone(timeMs, index, sampleRate,
                        490f, 960f, 830.61f, 95f, 270f) * 0.36;
            } else {
                // Low-level warm chord with a slow bloom and calm resolution.
                sample = warmTone(timeMs, index, sampleRate,
                        90f, 850f, 523.25f, 115f, 350f) * 0.23
                        + warmTone(timeMs, index, sampleRate,
                        115f, 890f, 659.25f, 125f, 370f) * 0.18
                        + warmTone(timeMs, index, sampleRate,
                        145f, 930f, 783.99f, 135f, 390f) * 0.14
                        + warmTone(timeMs, index, sampleRate,
                        770f, 1200f, 880.00f, 105f, 280f) * 0.13;
            }
            sample = Math.max(-0.88, Math.min(0.88, sample));
            short value = (short) Math.round(Short.MAX_VALUE * sample);
            int offset = index * 4;
            pcm[offset] = (byte) (value & 0xff);
            pcm[offset + 1] = (byte) ((value >> 8) & 0xff);
            pcm[offset + 2] = pcm[offset];
            pcm[offset + 3] = pcm[offset + 1];
        }
        return pcm;
    }

    private static double warmTone(float timeMs, int sampleIndex, int sampleRate,
                                   float startMs, float endMs, float frequency,
                                   float attackMs, float releaseMs) {
        if (timeMs < startMs || timeMs >= endMs) return 0.0;
        float position = timeMs - startMs;
        float remaining = endMs - timeMs;
        float envelope = Math.min(1f,
                Math.min(position / attackMs, remaining / releaseMs));
        envelope = envelope * envelope * (3f - 2f * envelope);
        double phase = 2.0 * Math.PI * frequency * sampleIndex / sampleRate;
        // Extra low body replaces the brittle high-frequency emphasis of a hard beep.
        double timbre = Math.sin(phase) * 0.82 + Math.sin(phase * 0.5) * 0.18;
        return envelope * timbre;
    }
}
