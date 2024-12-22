package io.github.haykam821.consolebox.game.audio;

public interface AudioController {
    AudioController NOOP = (a, duty, pan, b, c, sustainTime, volumeActual, volumePeak) -> {};

    void playSound(AudioChannel channel, ToneDuty duty, TonePan pan, int freq1, int freq2, int sustainTime, int volumeActual, int volumePeak);
}
