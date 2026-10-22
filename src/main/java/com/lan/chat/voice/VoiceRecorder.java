package com.lan.chat.voice;

import javax.sound.sampled.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * VoiceRecorder captures audio from the local microphone and saves it as a WAV file.
 */
public class VoiceRecorder {

    public record RecordResult(File file, int durationSeconds, byte[] audioBytes) {}

    private final AudioFormat audioFormat;
    private TargetDataLine microphone;
    private ByteArrayOutputStream audioBuffer;
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private Thread recordThread;
    private Thread timerThread;
    private int elapsedSeconds = 0;
    private final File storageDir;

    public VoiceRecorder() {
        // High-clarity 16kHz, 16-bit, Mono PCM
        this.audioFormat = new AudioFormat(16000.0f, 16, 1, true, false);
        this.storageDir = new File("voice_notes");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
    }

    public synchronized boolean startRecording(Consumer<Integer> onTick) {
        if (recording.get()) return false;

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
        if (!AudioSystem.isLineSupported(info)) {
            System.err.println("Microphone không hỗ trợ định dạng âm thanh 16kHz mono.");
            return false;
        }

        try {
            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(audioFormat);
            microphone.start();
        } catch (LineUnavailableException e) {
            System.err.println("Không thể truy cập Microphone: " + e.getMessage());
            return false;
        }

        audioBuffer = new ByteArrayOutputStream();
        recording.set(true);
        elapsedSeconds = 0;

        // Background capture thread
        recordThread = new Thread(() -> {
            byte[] buffer = new byte[2048];
            while (recording.get() && microphone != null) {
                int count = microphone.read(buffer, 0, buffer.length);
                if (count > 0) {
                    audioBuffer.write(buffer, 0, count);
                }
            }
        }, "VoiceRecord-Capture");
        recordThread.setDaemon(true);
        recordThread.start();

        // Timer ticker thread
        timerThread = new Thread(() -> {
            while (recording.get()) {
                try {
                    Thread.sleep(1000);
                    if (!recording.get()) break;
                    elapsedSeconds++;
                    if (onTick != null) {
                        onTick.accept(elapsedSeconds);
                    }
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }, "VoiceRecord-Timer");
        timerThread.setDaemon(true);
        timerThread.start();

        return true;
    }

    public synchronized RecordResult stopRecording() {
        if (!recording.get()) return null;
        recording.set(false);

        if (timerThread != null) {
            timerThread.interrupt();
        }

        if (microphone != null) {
            try {
                microphone.stop();
                microphone.close();
            } catch (Exception ignored) {}
        }

        try {
            if (recordThread != null) {
                recordThread.join(500);
            }
        } catch (InterruptedException ignored) {}

        byte[] pcmData = audioBuffer.toByteArray();
        if (pcmData.length < 1600) { // Less than 0.05s
            return null;
        }

        int finalDuration = Math.max(1, elapsedSeconds);
        String fileName = "voice_" + System.currentTimeMillis() + ".wav";
        File wavFile = new File(storageDir, fileName);

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
            long frameLength = pcmData.length / audioFormat.getFrameSize();
            try (AudioInputStream ais = new AudioInputStream(bais, audioFormat, frameLength)) {
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wavFile);
            }

            // Read the full written WAV file bytes for network transmission
            byte[] wavBytes = new byte[(int) wavFile.length()];
            try (FileInputStream fis = new FileInputStream(wavFile)) {
                int read = fis.read(wavBytes);
                if (read < wavBytes.length) {
                    System.err.println("Cảnh báo: Không đọc hết file WAV");
                }
            }

            return new RecordResult(wavFile, finalDuration, wavBytes);

        } catch (IOException e) {
            System.err.println("Lỗi lưu file WAV: " + e.getMessage());
            return null;
        }
    }

    public synchronized void cancelRecording() {
        if (!recording.get()) return;
        recording.set(false);

        if (timerThread != null) timerThread.interrupt();
        if (microphone != null) {
            try {
                microphone.stop();
                microphone.close();
            } catch (Exception ignored) {}
        }
        audioBuffer = null;
    }

    public boolean isRecording() {
        return recording.get();
    }

    public int getElapsedSeconds() {
        return elapsedSeconds;
    }
}
