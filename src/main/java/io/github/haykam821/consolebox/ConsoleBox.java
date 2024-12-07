package io.github.haykam821.consolebox;

import io.github.haykam821.consolebox.game.ConsoleBoxConfig;
import io.github.haykam821.consolebox.game.ConsoleBoxGame;
import io.github.haykam821.consolebox.resource.ConsoleGameManager;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.api.game.GameType;

public class ConsoleBox implements ModInitializer {
	public static final String MOD_ID = "consolebox";

	private static final Identifier CONSOLE_BOX_ID = ConsoleBox.identifier("console_box");
	public static final GameType<ConsoleBoxConfig> CONSOLE_BOX = GameType.register(CONSOLE_BOX_ID, ConsoleBoxConfig.CODEC, ConsoleBoxGame::open);

	@Override
	public void onInitialize() {
		ConsoleGameManager.register();
	}

	public static Identifier identifier(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
