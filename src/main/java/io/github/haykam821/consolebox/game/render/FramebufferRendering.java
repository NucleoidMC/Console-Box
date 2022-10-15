package io.github.haykam821.consolebox.game.render;

import java.nio.ByteBuffer;

import io.github.haykam821.consolebox.game.HardwareConstants;

/**
 * Rendering utilities for drawing to a framebuffer.
 * 
 * <p>Most logic here is ported from the
 * <a href="https://github.com/aduros/wasm4/tree/a5a7d1ef24d83da4452f933a970b800abbaa8563/runtimes">official WASM-4 runtimes</a>,
 * which are licensed under the ISC license.
 */
public final class FramebufferRendering {
	private FramebufferRendering() {
		return;
	}

	public static void drawPoint(ByteBuffer buffer, byte color, int x, int y) {
		int index = HardwareConstants.SCREEN_WIDTH * y + x;
		int address = index >>> 2;

		int shift = 6 - (index % 4) * 2;
		int mask = 0x3 << shift;

		buffer.put(address, (byte) ((color << shift) | (buffer.get(address) & ~mask)));
	}

	public static void drawPointUnclipped(ByteBuffer buffer, byte color, int x, int y) {
		if (x >= 0 && x < HardwareConstants.SCREEN_WIDTH && y >= 0 && y < HardwareConstants.SCREEN_HEIGHT) {
			FramebufferRendering.drawPoint(buffer, color, x, y);
		}
	}

	public static void drawHLineFast(ByteBuffer buffer, byte color, int startX, int y, int endX) {
		int fillEnd = endX - (endX & 3);
		int fillStart = Math.min((startX + 3) & ~3, fillEnd);

		if (fillEnd - fillStart > 3) {
			for (int xx = startX; xx < fillStart; xx++) {
				FramebufferRendering.drawPoint(buffer, color, xx, y);
			}

			int from = (HardwareConstants.SCREEN_WIDTH * y + fillStart) >>> 2;
			int to = (HardwareConstants.SCREEN_WIDTH * y + fillEnd) >>> 2;
			byte fillColor = (byte) (color * 0b01010101);

			for (int index = from; index < to; index++) {
				buffer.put(index, fillColor);
			}

			startX = fillEnd;
		}

		for (int xx = startX; xx < endX; xx++) {
			FramebufferRendering.drawPoint(buffer, color, xx, y);
		}
	}

	public static void drawHLineUnclipped(ByteBuffer buffer, byte color, int startX, int y, int endX) {
		if (y >= 0 && y < HardwareConstants.SCREEN_HEIGHT) {
			if (startX < 0) {
				startX = 0;
			}
			if (endX > HardwareConstants.SCREEN_WIDTH) {
				endX = HardwareConstants.SCREEN_WIDTH;
			}
			if (startX < endX) {
				FramebufferRendering.drawHLineFast(buffer, color, startX, y, endX);
			}
		}
	}

	public static void drawLine(ByteBuffer buffer, int drawColors, int x1, int y1, int x2, int y2) {
		byte dc0 = (byte) (drawColors & 0xf);
		if (dc0 == 0) {
			return;
		}
		byte strokeColor = (byte) ((dc0 - 1) & 0x3);

		if (y1 > y2) {
			int swap = x1;
			x1 = x2;
			x2 = swap;

			swap = y1;
			y1 = y2;
			y2 = swap;
		}

		int dx = Math.abs(x2 - x1), sx = x1 < x2 ? 1 : -1;
		int dy = y2 - y1;
		int err = (dx > dy ? dx : -dy) / 2, e2;

		for (;;) {
			FramebufferRendering.drawPointUnclipped(buffer, strokeColor, x1, y1);
			if (x1 == x2 && y1 == y2) {
				break;
			}
			e2 = err;
			if (e2 > -dx) {
				err -= dy;
				x1 += sx;
			}
			if (e2 < dy) {
				err += dx;
				y1++;
			}
		}
	}

	public static void drawRect(ByteBuffer buffer, byte fillColor, byte strokeColor, int x, int y, int width, int height) {
		int startX = Math.max(0, x);
		int startY = Math.max(0, y);
		int endXUnclamped = x + width;
		int endYUnclamped = y + height;
		int endX = Math.min(endXUnclamped, HardwareConstants.SCREEN_WIDTH);
		int endY = Math.min(endYUnclamped, HardwareConstants.SCREEN_HEIGHT);

		if (fillColor != 0) {
			fillColor -= 1;
			fillColor &= 0x3;

			for (int yy = startY; yy < endY; ++yy) {
				FramebufferRendering.drawHLineFast(buffer, fillColor, startX, yy, endX);
			}
		}

		if (strokeColor != 0) {
			strokeColor -= 1;
			strokeColor &= 0x3;

			// Left edge
			if (x >= 0 && x < HardwareConstants.SCREEN_WIDTH) {
				for (int yy = startY; yy < endY; ++yy) {
					FramebufferRendering.drawPoint(buffer, strokeColor, x, yy);
				}
			}

			// Right edge
			if (endXUnclamped >= 0 && endXUnclamped <= HardwareConstants.SCREEN_WIDTH) {
				for (int yy = startY; yy < endY; ++yy) {
					FramebufferRendering.drawPoint(buffer, strokeColor, endXUnclamped - 1, yy);
				}
			}

			// Top edge
			if (y >= 0 && y < HardwareConstants.SCREEN_HEIGHT) {
				FramebufferRendering.drawHLineFast(buffer, strokeColor, startX, y, endX);
			}

			// Bottom edge
			if (endYUnclamped >= 0 && endYUnclamped <= HardwareConstants.SCREEN_HEIGHT) {
				FramebufferRendering.drawHLineFast(buffer, strokeColor, startX, endYUnclamped - 1, endX);
			}
		}
	}

	public static void drawOval(ByteBuffer buffer, int drawColors, int startX, int startY, int width, int height) {
		int dc0 = drawColors & 0xf;
		int dc1 = (drawColors >> 4) & 0xf;

		if (dc1 == 0xf) {
			return;
		}

		byte strokeColor = (byte) ((dc1 - 1) & 0x3);
		byte fillColor = (byte) ((dc0 - 1) & 0x3);

		int a = width - 1;
		int b = height - 1;
		int b1 = b % 2; // Compensates for precision loss when dividing

		int north = startY + height / 2; // Precision loss here
		int west = startX;
		int east = startX + width - 1;
		int south = north - b1; // Compensation here. Moves the bottom line up by
								// one (overlapping the top line) for even heights

		// Error increments. Also known as the decision parameters
		int dx = 4 * (1 - a) * b * b;
		int dy = 4 * (b1 + 1) * a * a;

		// Error of 1 step
		int err = dx + dy + b1 * a * a;

		a *= 8 * a;
		b1 = 8 * b * b;

		do {
			FramebufferRendering.drawPointUnclipped(buffer, strokeColor, east, north);
			FramebufferRendering.drawPointUnclipped(buffer, strokeColor, west, north);
			FramebufferRendering.drawPointUnclipped(buffer, strokeColor, west, south);
			FramebufferRendering.drawPointUnclipped(buffer, strokeColor, east, south);
			int start = west + 1;
			int len = east - start;
			if (dc0 != 0 && len > 0) { // Only draw fill if the length from west to east is not 0
				FramebufferRendering.drawHLineUnclipped(buffer, fillColor, start, north, east);
				FramebufferRendering.drawHLineUnclipped(buffer, fillColor, start, south, east);
			}
			int err2 = 2 * err;
			if (err2 <= dy) {
				// Move vertical scan
				north += 1;
				south -= 1;
				dy += a;
				err += dy;
			}
			if (err2 >= dx || 2 * err > dy) {
				// Move horizontal scan
				west += 1;
				east -= 1;
				dx += b1;
				err += dx;
			}
		} while (west <= east);

		// Make sure north and south have moved the entire way so top/bottom aren't missing
		while (north - south < height) {
			FramebufferRendering.drawPointUnclipped(buffer, strokeColor, west - 1, north);
			FramebufferRendering.drawPointUnclipped(buffer, strokeColor, east + 1, north);
			north += 1;
			FramebufferRendering.drawPointUnclipped(buffer, strokeColor, west - 1, south); 
			FramebufferRendering.drawPointUnclipped(buffer, strokeColor, east + 1, south);
			south -= 1;
		}
	}

	private static byte getInbound(ByteBuffer buffer, int index) {
		return index < 0 || index >= buffer.limit() ? 0 : buffer.get(index);
	}

	public static void drawSprite(ByteBuffer buffer, int drawColors, ByteBuffer sprite, int startX, int startY, int width, int height, int sourceX, int sourceY, int stride, boolean bpp2, boolean flipX, boolean flipY, boolean rotate) {
		// Clip rectangle to screen
		int clipXMin, clipYMin, clipXMax, clipYMax;
		if (rotate) {
			flipX = !flipX;
			clipXMin = Math.max(0, startY) - startY;
			clipYMin = Math.max(0, startX) - startX;
			clipXMax = Math.min(width, HardwareConstants.SCREEN_HEIGHT - startY);
			clipYMax = Math.min(height, HardwareConstants.SCREEN_WIDTH - startX);
		} else {
			clipXMin = Math.max(0, startX) - startX;
			clipYMin = Math.max(0, startY) - startY;
			clipXMax = Math.min(width, HardwareConstants.SCREEN_WIDTH - startX);
			clipYMax = Math.min(height, HardwareConstants.SCREEN_HEIGHT - startY);
		}

		// Iterate pixels in rectangle
		for (int y = clipYMin; y < clipYMax; y++) {
			for (int x = clipXMin; x < clipXMax; x++) {
				// Calculate sprite target coords
				int tx = startX + (rotate ? y : x);
				int ty = startY + (rotate ? x : y);

				// Calculate sprite source coords
				int sx = sourceX + (flipX ? width - x - 1 : x);
				int sy = sourceY + (flipY ? height - y - 1 : y);

				// Sample the sprite to get a color index
				int colorIdx;
				int bitIndex = sy * stride + sx;
				if (bpp2) {
					int colorByte = FramebufferRendering.getInbound(sprite, bitIndex >>> 2);
					int shift = 6 - ((bitIndex & 0x03) << 1);
					colorIdx = ((colorByte >>> shift)) & 0b11;
				} else {
					int colorByte = FramebufferRendering.getInbound(sprite, bitIndex >>> 3);
					int shift = 7 - (bitIndex & 0x7);
					colorIdx = (colorByte >>> shift) & 0b1;
				}

				// Get the final color using the drawColors indirection
				int dc = (drawColors >>> (colorIdx << 2)) & 0x0f;
				if (dc != 0) {
					FramebufferRendering.drawPoint(buffer, (byte) ((dc - 1) & 0x3), tx, ty);
				}
			}
		}
	}

	public static void drawText(ByteBuffer buffer, int drawColors, String string, int x, int y) {
		int currentX = x;

		for (int index = 0; index < string.length(); index++) {
			char character = string.charAt(index);

			if (character == '\0') {
				return;
			} else if (character == '\n') {
				y += GameFont.CHARACTER_HEIGHT;
				currentX = x;
			} else {
				int sourceY = (character - 32) << 3;
				FramebufferRendering.drawSprite(buffer, drawColors, GameFont.FONT, currentX, y, GameFont.CHARACTER_WIDTH, GameFont.CHARACTER_HEIGHT, 0, sourceY, GameFont.CHARACTER_WIDTH, false, false, false, false);

				currentX += GameFont.CHARACTER_WIDTH;
			}
		}
	}
}
