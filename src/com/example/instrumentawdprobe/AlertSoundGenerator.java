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
                // Rounded three-note navigation cue with gentle overlaps.
                sample = warmTone(timeMs, index, sampleRate,
                        80f, 420f, 659.25f, 55f, 130f) * 0.52
                        + warmTone(timeMs, index, sampleRate,
                        370f, 760f, 830.61f, 60f, 155f) * 0.50
                        + warmTone(timeMs, index, sampleRate,
                        700f, 1160f, 987.77f, 70f, 230f) * 0.49;
            } else if (style == 2) {
                // Short, but no longer sharp: both notes have rounded edges.
                sample = warmTone(timeMs, index, sampleRate,
                        110f, 500f, 783.99f, 45f, 135f) * 0.54
                        + warmTone(timeMs, index, sampleRate,
                        480f, 920f, 987.77f, 52f, 175f) * 0.54;
            } else {
                // Warm major chord with a calm resolving note.
                sample = warmTone(timeMs, index, sampleRate,
                        90f, 820f, 523.25f, 80f, 270f) * 0.32
                        + warmTone(timeMs, index, sampleRate,
                        115f, 860f, 659.25f, 85f, 290f) * 0.26
                        + warmTone(timeMs, index, sampleRate,
                        145f, 900f, 783.99f, 90f, 310f) * 0.20
                        + warmTone(timeMs, index, sampleRate,
                        760f, 1180f, 1046.50f, 70f, 230f) * 0.21;
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
        // A quiet subharmonic adds body without the brittle edge of a loud overtone.
        double timbre = Math.sin(phase) * 0.90 + Math.sin(phase * 0.5) * 0.10;
        return envelope * timbre;
    }
}
