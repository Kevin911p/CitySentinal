package CitySentinal;

import javafx.scene.media.AudioClip;
import java.net.URL;

/**
 * AlertSoundPlayer — plays a short alert tone when a Critical+Active
 * threat is detected.
 *
 * Drop any short .wav or .mp3 file named "alert.wav" into your
 * src/main/resources/ (or project root) and it will play automatically.
 * If no file is found, a synthetic beep is generated via javax.sound.sampled
 * so the app never crashes silently.
 *
 * Usage in MainDashboard (inside the alert monitor callback):
 *   AlertSoundPlayer soundPlayer = new AlertSoundPlayer();
 *   // Then when a new Critical+Active threat appears:
 *   soundPlayer.playAlert();
 */
public class AlertSoundPlayer {

    private AudioClip clip;
    private boolean   useSynth = false;

    public AlertSoundPlayer() {
        try {
            URL url = getClass().getResource("/alert.wav");
            if (url == null) url = getClass().getResource("/sounds/alert.wav");
            if (url != null) {
                clip = new AudioClip(url.toExternalForm());
                clip.setVolume(0.75);
                System.out.println("[AlertSoundPlayer] Loaded alert.wav");
            } else {
                useSynth = true;
                System.out.println("[AlertSoundPlayer] alert.wav not found — using synth beep.");
            }
        } catch (Exception e) {
            useSynth = true;
            System.out.println("[AlertSoundPlayer] Could not load sound: " + e.getMessage());
        }
    }

    /**
     * Play the alert sound. Safe to call from any thread.
     * Won't throw even if no audio device is present.
     */
    public void playAlert() {
        try {
            if (clip != null && !useSynth) {
                if (clip.isPlaying()) clip.stop();
                clip.play();
            } else {
                playBeep();
            }
        } catch (Exception e) {
            System.out.println("[AlertSoundPlayer] Playback error: " + e.getMessage());
        }
    }

    // ── Synthetic beep via javax.sound.sampled ────────────────────────────
    private void playBeep() {
        new Thread(() -> {
            try {
                javax.sound.sampled.AudioFormat fmt =
                    new javax.sound.sampled.AudioFormat(44100, 16, 1, true, false);
                javax.sound.sampled.DataLine.Info info =
                    new javax.sound.sampled.DataLine.Info(
                        javax.sound.sampled.SourceDataLine.class, fmt);
                javax.sound.sampled.SourceDataLine line =
                    (javax.sound.sampled.SourceDataLine)
                        javax.sound.sampled.AudioSystem.getLine(info);
                line.open(fmt);
                line.start();

                // Two-tone beep: 880 Hz then 1100 Hz, 0.15 s each
                for (int freq : new int[]{880, 1100}) {
                    int durationMs = 150;
                    int samples    = (int) (44100 * durationMs / 1000.0);
                    byte[] buf     = new byte[samples * 2];
                    for (int i = 0; i < samples; i++) {
                        double angle = 2 * Math.PI * freq * i / 44100;
                        short  val   = (short) (Short.MAX_VALUE * 0.5 * Math.sin(angle));
                        buf[i * 2]     = (byte)  (val & 0xFF);
                        buf[i * 2 + 1] = (byte) ((val >> 8) & 0xFF);
                    }
                    line.write(buf, 0, buf.length);
                }
                line.drain();
                line.close();
            } catch (Exception ignored) {}
        }, "AlertBeep").start();
    }
}
