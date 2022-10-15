package io.github.haykam821.consolebox.game;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import io.github.haykam821.consolebox.mixin.MemoryTypeAccessor;
import io.github.kawamuray.wasmtime.Extern;
import io.github.kawamuray.wasmtime.Memory;
import io.github.kawamuray.wasmtime.MemoryType;
import io.github.kawamuray.wasmtime.Store;

public final class GameMemory {
	private static final int PALETTE_ADDRESS = 0x0004;
	private static final int DRAW_COLORS_ADDRESS = 0x0014;

	private static final int SYSTEM_FLAGS_ADDRESS = 0x001F;

	private static final int FRAMEBUFFER_ADDRESS = 0x00A0;
	private static final int FRAMEBUFFER_SIZE = HardwareConstants.SCREEN_AREA >>> 2;

	private final Memory memory;

	private final ByteBuffer buffer;
	private final ByteBuffer framebuffer;

	protected GameMemory(Store<Void> store) {
		this.memory = GameMemory.createMemory(store, HardwareConstants.MEMORY_PAGES);

		this.buffer = memory.buffer(store);
		this.framebuffer = this.buffer.slice(FRAMEBUFFER_ADDRESS, FRAMEBUFFER_SIZE);

		this.initializeMemory();
	}

	public Extern createExtern() {
		return Extern.fromMemory(this.memory);
	}

	public ByteBuffer getFramebuffer() {
		return this.framebuffer;
	}

	public int readColor(int start) {
		int r = this.buffer.get(start) & 0xFF;
		int g = this.buffer.get(start + 1) & 0xFF;
		int b = this.buffer.get(start + 2) & 0xFF;

		return r | g << 8 | b << 16;
	}

	public int readPaletteColor(int index) {
		return this.readColor(PALETTE_ADDRESS + index * 4);
	}

	public byte readSystemFlags() {
		return this.buffer.get(SYSTEM_FLAGS_ADDRESS);
	}

	public int readDrawColors() {
		return (this.buffer.get(DRAW_COLORS_ADDRESS + 1) << 8) + this.buffer.get(DRAW_COLORS_ADDRESS);
	}

	public boolean readSystemPreserveFramebuffer() {
		return (this.readSystemFlags() & 1) > 0;
	}

	public String readString(int start) {
		int length = 0;

		while (this.buffer.hasRemaining()) {
			byte character = this.buffer.get(start + length);

			if (character == 0x00) {
				byte[] bytes = new byte[length];
				this.buffer.get(start, bytes, 0, length);

				return new String(bytes, StandardCharsets.US_ASCII);
			} else {
				length += 1;
			}
		}

		return "";
	}

	public String readUnterminatedString(int start, int length, Charset charset) {
		return charset.decode(this.buffer.slice(start, length)).toString();
	}

	public ByteBuffer readSprite(int start, int width, int height) {
		return this.buffer.slice(start, width * height);
	}

	private void initializeMemory() {
		this.buffer.putInt(PALETTE_ADDRESS, 0xCFF8E000); // Dark green
		this.buffer.putInt(PALETTE_ADDRESS + 4, 0x6CC08600); // Light green
		this.buffer.putInt(PALETTE_ADDRESS + 8, 0x50683000); // Dull green
		this.buffer.putInt(PALETTE_ADDRESS + 12, 0x21180700); // Dark teal

		this.buffer.put(DRAW_COLORS_ADDRESS, (byte) 0x12);
		this.buffer.put(DRAW_COLORS_ADDRESS + 1, (byte) 0x03);
	}

	@Override
	public String toString() {
		return "GameMemory{" + this.memory + "}";
	}

	private static Memory createMemory(Store<Void> store, int pages) {
		MemoryType memoryType = new MemoryType(pages, false);
		((MemoryTypeAccessor) (Object) memoryType).setMaximum(pages);

		return new Memory(store, memoryType);
	}
}
