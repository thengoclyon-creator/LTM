package com.lan.chat.util;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Generates pleasant procedural sound notifications (e.g. Ting Ting message sound,
 * incoming call alert, connect chime) using standard javax.sound.sampled without external files.
 */
public class AudioNotifier {

    private static volatile boolean soundEnabled = true;

    public static boolean isSoundEnabled() {
        return soundEnabled;
    }

    public static void setSoundEnabled(boolean enabled) {
        soundEnabled = enabled;
    }

    /**
     * Play high-pitched gentle "ting ting" message notification.
     */
    public static void playMessageTing() {
        if (!soundEnabled) return;
        new Thread(() -> {
            try {
                // Play two pleasant tones in quick succession: 1046Hz (C6) and 1318Hz (E6)
                playTone(1046, 70, 0.5);
                Thread.sleep(40);
                playTone(1318, 120, 0.6);
            } catch (Exception ignored) {}
        }, "AudioNotifier-Ting").start();
    }

    /**
     * Play connection chime.
     */
    public static void playConnectChime() {
        if (!soundEnabled) return;
        new Thread(() -> {
            try {
                playTone(523, 80, 0.4);  // C5
                playTone(659, 80, 0.4);  // E5
                playTone(784, 140, 0.5); // G5
            } catch (Exception ignored) {}
        }, "AudioNotifier-Connect").start();
    }

    /**
     * Synthesize and output a smooth sine wave tone with attack-decay envelope.
     */
    private static void playTone(double frequency, int durationMs, double volume) {
        try {
            float sampleRate = 44100;
            int numSamples = (int) (sampleRate * (durationMs / 1000.0));
            byte[] pcmData = new byte[numSamples * 2]; // 16-bit mono

            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, true);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format);
                line.start();

                for (int i = 0; i < numSamples; i++) {
                    double time = i / (double) sampleRate;
                    // Envelope: fast linear attack, exponential decay
                    double envelope = Math.exp(-4.0 * i / numSamples);
                    if (i < 200) envelope *= (i / 200.0);

                    double sinValue = Math.sin(2.0 * Math.PI * frequency * time);
                    short sample = (short) (sinValue * envelope * volume * Short.MAX_VALUE);

                    // Big-endian 16-bit
                    pcmData[2 * i] = (byte) ((sample >> 8) & 0xFF);
                    pcmData[2 * i + 1] = (byte) (sample & 0xFF);
                }

                line.write(pcmData, 0, pcmData.length);
                line.drain();
            }
        } catch (Exception ignored) {
            // Audio hardware might be busy or unavailable
        }
    }
}
