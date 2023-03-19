package io.github.haykam821.consolebox.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.haykam821.consolebox.resource.ConsoleGameManager;
import net.minecraft.SharedConstants;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;
import xyz.nucleoid.plasmid.game.GameOpenException;

public record ConsoleBoxConfig(
	Identifier game,
	Vec3d spectatorSpawnOffset,
	boolean swapXZ
) {
	private static final Vec3d DEFAULT_SPECTATOR_SPAWN_OFFSET = new Vec3d(0, 2, 0);

	public static final Codec<ConsoleBoxConfig> CODEC = RecordCodecBuilder.create(instance -> {
		return instance.group(
			Identifier.CODEC.fieldOf("game").forGetter(ConsoleBoxConfig::game),
			Vec3f.CODEC.xmap(Vec3d::new, Vec3f::new).optionalFieldOf("spectator_spawn_offset", DEFAULT_SPECTATOR_SPAWN_OFFSET).forGetter(ConsoleBoxConfig::spectatorSpawnOffset),
			Codec.BOOL.optionalFieldOf("swap_x_z", false).forGetter(ConsoleBoxConfig::swapXZ)
		).apply(instance, ConsoleBoxConfig::new);
	});

	public byte[] getGameData() throws GameOpenException {
		byte[] data = ConsoleGameManager.getGameData(this.game);

		if (data == null) {
			throw new GameOpenException(Text.translatable("text.consolebox.nonexistent_console_game", this.game));
		} else {
			return data;
		}
	}
}
