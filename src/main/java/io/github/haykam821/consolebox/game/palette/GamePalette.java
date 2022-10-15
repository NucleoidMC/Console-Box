package io.github.haykam821.consolebox.game.palette;

import java.util.Arrays;

import eu.pb4.mapcanvas.api.core.CanvasColor;
import io.github.haykam821.consolebox.game.GameMemory;

/**
 * Manages bindings between the raw palette and canvas colors.
 */
public final class GamePalette {
	private final GameMemory memory;

	public GamePalette(GameMemory memory) {
		this.memory = memory;
	}

	private final PaletteEntry[] entries = {
		new PaletteEntry(null, 0),
		new PaletteEntry(null, 0),
		new PaletteEntry(null, 0),
		new PaletteEntry(null, 0)
	};

	private void updateEntry(int index, int raw) {
		PaletteEntry entry = this.entries[index];

		if (entry.color() == null || entry.raw() != raw) {
			this.entries[index] = new PaletteEntry(raw);
		}
	}

	public void update() {
		for (int index = 0; index < this.entries.length; index++) {
			this.updateEntry(index, memory.readPaletteColor(index));
		}
	}

	public CanvasColor getColor(int index) {
		return this.entries[index].color();
	}

	@Override
	public String toString() {
		return "GamePalette" + Arrays.toString(this.entries);
	}
}
