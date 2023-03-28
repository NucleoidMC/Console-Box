package io.github.haykam821.consolebox.game.audio;

public interface AudioController {
    AudioController NOOP = (a, b, c) -> {};

    void playSound(AudioChannel channel, float freq1, int sustainTimeMs);
}
