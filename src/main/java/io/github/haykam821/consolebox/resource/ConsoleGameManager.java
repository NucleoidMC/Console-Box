package io.github.haykam821.consolebox.resource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.haykam821.consolebox.ConsoleBox;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public class ConsoleGameManager implements SimpleSynchronousResourceReloadListener {
	private static final Identifier ID = ConsoleBox.identifier("console_games");
	private static final Logger LOGGER = LoggerFactory.getLogger("ConsoleGameManager");

	private static final String GAME_PREFIX = "console_games";
	private static final String GAME_EXTENSION = ".wasm";

	private static final Map<Identifier, byte[]> GAMES = new HashMap<>();

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		GAMES.clear();
		manager.listResources(GAME_PREFIX, this::isGamePath).forEach(this::loadResource);
	}

	private void loadResource(Identifier path, Resource resource) {
		try {
			Identifier id = this.parsePath(path);
			GAMES.put(id, resource.open().readAllBytes());
		} catch (IOException exception) {
			LOGGER.error("Failed to load console game '{}'", path, exception);
		}
	}

	@Override
	public Identifier getFabricId() {
		return ID;
	}

	private boolean isGamePath(Identifier path) {
		return path.getPath().endsWith(GAME_EXTENSION);
	}

	private Identifier parsePath(Identifier id) {
		String prefix = GAME_PREFIX + "/";

		return id.withPath(path -> {
			return path.substring(prefix.length(), path.length() - GAME_EXTENSION.length());
		});
	}

	public static byte[] getGameData(Identifier id) {
		return GAMES.get(id);
	}

	public static void register() {
		ResourceManagerHelper serverData = ResourceManagerHelper.get(PackType.SERVER_DATA);
		serverData.registerReloadListener(new ConsoleGameManager());
	}
}
