package io.github.haykam821.consolebox.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.haykam821.consolebox.resource.ConsoleGameManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.plasmid.api.game.GameOpenException;

public record ConsoleBoxConfig(
	Identifier game,
	Vec3 spectatorSpawnOffset,
	int playerCount,
	boolean swapXZ,
	boolean save
) {
	private static final Vec3 DEFAULT_SPECTATOR_SPAWN_OFFSET = new Vec3(0, 2, 0);

	public static final MapCodec<ConsoleBoxConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> {
		return instance.group(
			Identifier.CODEC.fieldOf("game").forGetter(ConsoleBoxConfig::game),
			ExtraCodecs.VECTOR3F.xmap(Vec3::new, Vec3::toVector3f).optionalFieldOf("spectator_spawn_offset", DEFAULT_SPECTATOR_SPAWN_OFFSET).forGetter(ConsoleBoxConfig::spectatorSpawnOffset),
			Codec.intRange(1, 4).optionalFieldOf("players", 1).forGetter(ConsoleBoxConfig::playerCount),
			Codec.BOOL.optionalFieldOf("swap_x_z", false).forGetter(ConsoleBoxConfig::swapXZ),
			Codec.BOOL.optionalFieldOf("save", false).forGetter(ConsoleBoxConfig::save)
		).apply(instance, ConsoleBoxConfig::new);
	});

	public byte[] getGameData() throws GameOpenException {
		byte[] data = ConsoleGameManager.getGameData(this.game);

		if (data == null) {
			throw new GameOpenException(Component.translatable("text.consolebox.nonexistent_console_game", this.game));
		} else {
			return data;
		}
	}
}
