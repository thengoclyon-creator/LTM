package com.lan.chat.voice;

import javax.sound.sampled.*;
import java.io.File;

/**
 * VoicePlayer handles audio playback of recorded Voice Notes (.wav) using Java Sound Clip.
 */
public class VoicePlayer {

    private Clip activeClip;
    private String currentlyPlayingName;
    private Runnable currentOnFinished;

    public synchronized void play(File wavFile, Runnable onFinished) {
        stop();

        if (wavFile == null || !wavFile.exists()) {
            System.err.println("File âm thanh không tồn tại: " + (wavFile != null ? wavFile.getPath() : "null"));
            return;
        }

        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(wavFile);
            Clip clip = AudioSystem.getClip();
            this.activeClip = clip;
            this.currentlyPlayingName = wavFile.getName();
            this.currentOnFinished = onFinished;

            clip.open(ais);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    synchronized (VoicePlayer.this) {
                        if (activeClip == clip) {
                            clip.close();
                            activeClip = null;
                            currentlyPlayingName = null;
                            if (currentOnFinished != null) {
                                Runnable cb = currentOnFinished;
                                currentOnFinished = null;
                                cb.run();
                            }
                        }
                    }
                }
            });

            clip.start();

        } catch (Exception e) {
            System.err.println("Lỗi phát âm thanh voice: " + e.getMessage());
            stop();
        }
    }

    public synchronized void stop() {
        if (activeClip != null) {
            try {
                if (activeClip.isRunning()) {
                    activeClip.stop();
                }
                activeClip.close();
            } catch (Exception ignored) {}
            activeClip = null;
        }
        currentlyPlayingName = null;
        if (currentOnFinished != null) {
            Runnable cb = currentOnFinished;
            currentOnFinished = null;
            cb.run();
        }
    }

    public synchronized boolean isPlaying() {
        return activeClip != null && activeClip.isRunning();
    }

    public synchronized String getCurrentlyPlayingName() {
        return currentlyPlayingName;
    }
}
