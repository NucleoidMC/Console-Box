package io.github.haykam821.consolebox.game;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.pb4.mapcanvas.api.core.CombinedPlayerCanvas;
import eu.pb4.mapcanvas.api.core.DrawableCanvas;
import eu.pb4.mapcanvas.api.core.PlayerCanvas;
import eu.pb4.mapcanvas.api.utils.CanvasUtils;
import io.github.haykam821.consolebox.game.palette.GamePalette;
import io.github.haykam821.consolebox.game.render.FramebufferRendering;
import io.github.kawamuray.wasmtime.Engine;
import io.github.kawamuray.wasmtime.Extern;
import io.github.kawamuray.wasmtime.Func;
import io.github.kawamuray.wasmtime.Linker;
import io.github.kawamuray.wasmtime.Module;
import io.github.kawamuray.wasmtime.Store;
import io.github.kawamuray.wasmtime.WasmFunctions;
import io.github.kawamuray.wasmtime.WasmFunctions.Consumer0;
import io.github.kawamuray.wasmtime.WasmValType;
import net.minecraft.item.FilledMapItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class GameCanvas {
	private static final Logger LOGGER = LoggerFactory.getLogger("GameCanvas");

	private static final int RENDER_SCALE = 5;
	private static final int MAP_SIZE = FilledMapItem.field_30907;
	private static final int SECTION_SIZE = MathHelper.ceil(HardwareConstants.SCREEN_WIDTH * RENDER_SCALE / (double) MAP_SIZE);

	private static final Consumer0 EMPTY_CALLBACK = () -> {};

	private final ConsoleBoxConfig config;

	private final Store<Void> store;
	private final GameMemory memory;

	private final GamePalette palette;
	private final CombinedPlayerCanvas canvas;

	private final WasmFunctions.Consumer0 startCallback;
	private final WasmFunctions.Consumer0 updateCallback;

	public GameCanvas(ConsoleBoxConfig config) {
		this.config = config;

		this.store = Store.withoutData();
		this.memory = new GameMemory(this.store);

		Engine engine = this.store.engine();
		Module module = new Module(engine, config.getGameData());

		Linker linker = new Linker(this.store.engine());
		this.defineImports(linker);
		linker.module(this.store, "", module);

		this.palette = new GamePalette(this.memory);
		this.canvas = DrawableCanvas.create(SECTION_SIZE, SECTION_SIZE);

		this.startCallback = this.getCallback(linker, "start");
		this.updateCallback = this.getCallback(linker, "update");
	}

	private void defineImports(Linker linker) {
		GameCanvas.defineImport(linker, "blit", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::blit));
		GameCanvas.defineImport(linker, "blitSub", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::blitSub));

		GameCanvas.defineImport(linker, "line", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::line));
		GameCanvas.defineImport(linker, "hline", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::hline));
		GameCanvas.defineImport(linker, "vline", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::vline));

		GameCanvas.defineImport(linker, "oval", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::oval));
		GameCanvas.defineImport(linker, "rect", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::rect));

		GameCanvas.defineImport(linker, "text", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::text));
		GameCanvas.defineImport(linker, "textUtf8", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::textUtf8));
		GameCanvas.defineImport(linker, "textUtf16", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::textUtf16));

		GameCanvas.defineImport(linker, "tone", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::tone));

		GameCanvas.defineImport(linker, "diskr", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, this::diskr));
		GameCanvas.defineImport(linker, "diskw", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, this::diskw));

		GameCanvas.defineImport(linker, "trace", WasmFunctions.wrap(this.store, WasmValType.I32, this::trace));
		GameCanvas.defineImport(linker, "traceUtf8", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, this::traceUtf8));
		GameCanvas.defineImport(linker, "traceUtf16", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, this::traceUtf16));
		GameCanvas.defineImport(linker, "tracef", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, this::tracef));

		Extern memoryExtern = this.memory.createExtern();
		linker.define("env", "memory", memoryExtern);
	}

	// External functions
	private void blit(int spriteAddress, int x, int y, int width, int height, int flags) {
		this.blitSub(spriteAddress, x, y, width, height, 0, 0, width, flags);
	}

	private void blitSub(int spriteAddress, int x, int y, int width, int height, int sourceX, int sourceY, int stride, int flags) {
		ByteBuffer buffer = this.memory.getFramebuffer();
		int drawColors = this.memory.readDrawColors();

		ByteBuffer sprite = this.memory.readSprite(spriteAddress, width, height);

		boolean bpp2 = (flags & 1) > 0;
		boolean flipX = (flags & 2) > 0;
		boolean flipY = (flags & 4) > 0;
		boolean rotate = (flags & 8) > 0;

		FramebufferRendering.drawSprite(buffer, drawColors, sprite, x, y, width, height, sourceX, sourceY, stride, bpp2, flipX, flipY, rotate);
	}

	private void line(int x1, int y1, int x2, int y2) {
		ByteBuffer buffer = this.memory.getFramebuffer();
		int drawColors = this.memory.readDrawColors();

		FramebufferRendering.drawLine(buffer, drawColors, x1, y1, x2, y2);
	}

	private void hline(int x, int y, int length) {
		byte strokeColor = (byte) (this.memory.readDrawColors() & 0b1111);

		if (strokeColor != 0) {
			strokeColor -= 1;
			strokeColor &= 0x3;

			ByteBuffer buffer = this.memory.getFramebuffer();
			FramebufferRendering.drawHLineUnclipped(buffer, strokeColor, x, y, x + length);
		}
	}

	private void vline(int x, int y, int length) {
		if (y + length <= 0 || x < 0 || x >= HardwareConstants.SCREEN_WIDTH) {
			return;
		}

		byte strokeColor = (byte) (this.memory.readDrawColors() & 0b1111);

		if (strokeColor != 0) {
			strokeColor -= 1;
			strokeColor &= 0x3;

			ByteBuffer buffer = this.memory.getFramebuffer();

			int startY = Math.max(0, y);
			int endY = Math.min(HardwareConstants.SCREEN_HEIGHT, y + length);
			FramebufferRendering.drawHLineUnclipped(buffer, strokeColor, x, y, x + length);

			for (int dy = startY; dy < endY; dy++) {
				FramebufferRendering.drawPoint(buffer, strokeColor, x, dy);
			}
		}
	}

	private void oval(int x, int y, int width, int height) {
		ByteBuffer buffer = this.memory.getFramebuffer();
		int drawColors = this.memory.readDrawColors();

		FramebufferRendering.drawOval(buffer, drawColors, x, y, width, height);
	}

	private void rect(int x, int y, int width, int height) {
		ByteBuffer buffer = this.memory.getFramebuffer();
		int drawColors = this.memory.readDrawColors();

		byte fillColor = (byte) (drawColors & 0b1111);
		byte strokeColor = (byte) (drawColors >>> 4 & 0b1111);

		FramebufferRendering.drawRect(buffer, fillColor, strokeColor, x, y, width, height);
	}

	private void drawText(String string, int x, int y) {
		ByteBuffer buffer = this.memory.getFramebuffer();
		int drawColors = this.memory.readDrawColors();

		FramebufferRendering.drawText(buffer, drawColors, string, x, y);
	}

	private void text(int string, int x, int y) {
		this.drawText(this.memory.readString(string), x, y);
	}

	private void textUtf8(int string, int length, int x, int y) {
		this.drawText(this.memory.readUnterminatedString(string, length, StandardCharsets.UTF_8), x, y);
	}

	private void textUtf16(int string, int length, int x, int y) {
		this.drawText(this.memory.readUnterminatedString(string, length, StandardCharsets.UTF_16LE), x, y);
	}

	private void tone(int frequency, int duration, int volume, int flags) {
		// Intentionally empty as sound is unsupported
	}

	private void diskr(int address, int size) {
		// Intentionally empty as persistent storage is unsupported
	}

	private void diskw(int address, int size) {
		// Intentionally empty as persistent storage is unsupported
	}

	private void trace(int string) {
		LOGGER.trace("From game: {}", this.memory.readString(string));
	}

	private void traceUtf8(int string, int length) {
		LOGGER.trace("From game: {}", this.memory.readUnterminatedString(string, length, StandardCharsets.UTF_8));
	}

	private void traceUtf16(int string, int length) {
		LOGGER.trace("From game: {}", this.memory.readUnterminatedString(string, length, StandardCharsets.UTF_16LE));
	}

	private void tracef(int string, int stack) {
		LOGGER.trace("From game (unformatted): {}", this.memory.readString(string));
	}

	// Behavior
	private void update() {
		if (!this.memory.readSystemPreserveFramebuffer()) {
			ByteBuffer buffer = this.memory.getFramebuffer();
			for (int index = 0; index < buffer.limit(); index++) {
				buffer.put(index, (byte) 0x0);
			}
		}

		this.updateCallback.accept();
	}

	public void render() {
		ByteBuffer buffer = this.memory.getFramebuffer();
		int index = 0;

		for (int y = 0; y < HardwareConstants.SCREEN_HEIGHT; y++) {
			for (int x = 0; x < HardwareConstants.SCREEN_WIDTH; x++) {
				int colorAddress = index >>> 3;
				byte color = (byte) (buffer.get(colorAddress) >>> (6 - index % 8) & 0b11);

				CanvasUtils.fill(this.canvas, x * RENDER_SCALE, y * RENDER_SCALE, (x + 1) * RENDER_SCALE, (y + 1) * RENDER_SCALE, this.palette.getColor(color));
				index += 2;
			}
		}

		this.canvas.sendUpdates();
	}

	public void tick(ServerPlayerEntity player) {
		this.memory.updateGamepad(player);

		for (int i = 0; i < this.config.updatesPerSecond(); i++) {
			this.update();
		}

		this.palette.update();
		this.render();
	}

	public void start() {
		this.startCallback.accept();

		this.palette.update();
		this.render();
	}

	public BlockPos getDisplayPos() {
		return new BlockPos(0, SECTION_SIZE, 0);
	}

	public Vec3d getSpawnPos() {
		BlockPos displayPos = this.getDisplayPos();
		double x = HardwareConstants.SCREEN_WIDTH * RENDER_SCALE / 2d / MAP_SIZE;

		return new Vec3d(x, displayPos.getY() / 2d, displayPos.getY() * 1.2);
	}

	public int getSpawnAngle() {
		return 180;
	}

	public PlayerCanvas getCanvas() {
		return this.canvas;
	}

	private Consumer0 getCallback(Linker linker, String name) {
		return linker.get(this.store, "", name)
			.map(extern -> WasmFunctions.consumer(this.store, extern.func()))
			.orElse(EMPTY_CALLBACK);
	}

	private static void defineImport(Linker linker, String name, Func func) {
		Extern extern = Extern.fromFunc(func);
		linker.define("env", name, extern);
	}
}
