package io.github.haykam821.consolebox.game.palette;

import eu.pb4.mapcanvas.api.core.CanvasColor;
import eu.pb4.mapcanvas.api.utils.CanvasUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.util.Util;

public record PaletteEntry(CanvasColor color, int raw) {
	private static final Int2ObjectMap<CanvasColor> OVERRIDES = Util.make(new Int2ObjectOpenHashMap<>(), map -> {
		map.put(0xE0F8CF, CanvasColor.PALE_YELLOW_NORMAL);
		map.put(0x86C06C, CanvasColor.PALE_GREEN_NORMAL);
		map.put(0x306850, CanvasColor.EMERALD_GREEN_LOWEST);
		map.put(0x071821, CanvasColor.TEAL_LOWEST);
	});

	public PaletteEntry(int raw) {
		this(OVERRIDES.containsKey(raw) ? OVERRIDES.get(raw) : CanvasUtils.findClosestColor(raw), raw);
	}
}