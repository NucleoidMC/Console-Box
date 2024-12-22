package io.github.haykam821.consolebox.game.audio;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.function.Consumer;

public class BaseAudioController implements AudioController {
    private Consumer<Packet<?>> consumer = (x) -> {};
    private Entity center;
    private Entity left;
    private Entity right;

    @Override
    public void playSound(AudioChannel channel, ToneDuty duty, TonePan pan, int freq1, int freq2, int sustainTime, int volumeActual, int volumePeak) {
        assert center != null;
        assert left != null;
        assert right != null;
        var sound = switch (channel) {
            case PULSE_1 -> SoundEvents.BLOCK_NOTE_BLOCK_FLUTE;
            case PULSE_2 -> SoundEvents.BLOCK_NOTE_BLOCK_HARP;
            case TRIANGLE -> SoundEvents.BLOCK_NOTE_BLOCK_BASS;
            case NOISE -> SoundEvents.BLOCK_NOTE_BLOCK_SNARE;
        };
        var pitch = freq1 / 500f;

        var volume = volumeActual / 100f;

        consumer.accept(new PlaySoundFromEntityS2CPacket(sound, SoundCategory.VOICE, switch (pan) {
            case CENTER -> this.center;
            case RIGHT -> this.right;
            case LEFT -> this.left;
        }, volume, pitch, 1));
    }

    public void setOutput(Entity center, Entity left, Entity right,  Consumer<Packet<?>> consumer) {
        this.center = center;
        this.left = left;
        this.right = right;
        this.consumer = consumer;
    }
}
