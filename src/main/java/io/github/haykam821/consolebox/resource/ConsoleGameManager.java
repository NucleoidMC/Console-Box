package io.github.haykam821.consolebox.resource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.haykam821.consolebox.ConsoleBox;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

public class ConsoleGameManager implements SimpleSynchronousResourceReloadListener {
	private static final Identifier ID = new Identifier(ConsoleBox.MOD_ID, "console_games");
	private static final Logger LOGGER = LoggerFactory.getLogger("ConsoleGameManager");

	private static final String GAME_PREFIX = "console_games";
	private static final String GAME_EXTENSION = ".wasm";

	private static final Map<Identifier, byte[]> GAMES = new HashMap<>();

	@Override
	public void reload(ResourceManager manager) {
		GAMES.clear();
		manager.findResources(GAME_PREFIX, this::isGamePath).forEach(this::loadResource);
	}

	private void loadResource(Identifier path, Resource resource) {
		try {
			Identifier id = this.parsePath(path);
			GAMES.put(id, resource.getInputStream().readAllBytes());
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
		String path = id.getPath().substring(prefix.length(), id.getPath().length() - GAME_EXTENSION.length());

		return new Identifier(id.getNamespace(), path);
	}

	public static byte[] getGameData(Identifier id) {
		return GAMES.get(id);
	}

	public static void register() {
		ResourceManagerHelper serverData = ResourceManagerHelper.get(ResourceType.SERVER_DATA);
		serverData.registerReloadListener(new ConsoleGameManager());
	}
}
