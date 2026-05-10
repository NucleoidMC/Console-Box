package io.github.haykam821.consolebox.game.audio;

import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

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
            case PULSE_1, PULSE_2 -> switch (duty) {
                case MODE_50, MODE_12_5 -> SoundEvents.NOTE_BLOCK_HARP;
                default -> SoundEvents.NOTE_BLOCK_FLUTE;
            };
            case TRIANGLE -> SoundEvents.NOTE_BLOCK_BASS;
            case NOISE -> SoundEvents.NOTE_BLOCK_SNARE;
        };
        var pitch = freq1 / 500f;

        var volume = volumeActual / 100f;

        consumer.accept(new ClientboundSoundEntityPacket(sound, SoundSource.VOICE, switch (pan) {
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
