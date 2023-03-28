package io.github.haykam821.consolebox.game.audio;

import io.github.haykam821.consolebox.game.ConsoleBoxGame;

public class BaseAudioController implements AudioController {
    @Override
    public void playSound(AudioChannel channel, float pitch, int sustainTimeMs) {
    }

    public void setOutput(ConsoleBoxGame phase) {
    }
}
