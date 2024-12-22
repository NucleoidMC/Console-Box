package io.github.haykam821.consolebox.game;

import eu.pb4.mapcanvas.api.core.*;
import eu.pb4.mapcanvas.api.font.DefaultFonts;
import eu.pb4.mapcanvas.api.utils.CanvasUtils;
import io.github.haykam821.consolebox.ConsoleBox;
import io.github.haykam821.consolebox.game.audio.AudioChannel;
import io.github.haykam821.consolebox.game.audio.AudioController;
import io.github.haykam821.consolebox.game.audio.ToneDuty;
import io.github.haykam821.consolebox.game.audio.TonePan;
import io.github.haykam821.consolebox.game.palette.GamePalette;
import io.github.haykam821.consolebox.game.render.FramebufferRendering;
import io.github.kawamuray.wasmtime.Module;
import io.github.kawamuray.wasmtime.WasmFunctionError.I32ExitError;
import io.github.kawamuray.wasmtime.WasmFunctionError.TrapError;
import io.github.kawamuray.wasmtime.*;
import io.github.kawamuray.wasmtime.WasmFunctions.Consumer0;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.FilledMapItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class GameCanvas {
    private static final Logger LOGGER = LoggerFactory.getLogger("GameCanvas");

    private static final CanvasImage DEFAULT_BACKGROUND = readImage("default_background");
    private static final CanvasImage DEFAULT_OVERLAY = readImage("default_overlay");
    private static final int BACKGROUND_SCALE = 2;

    private static CanvasImage readImage(String path) {
        CanvasImage temp;
        try {
            temp = CanvasImage.from(ImageIO.read(
                    Files.newInputStream(FabricLoader.getInstance().getModContainer(ConsoleBox.MOD_ID).get().findPath("data/consolebox/background/" + path + ".png").get())));
        } catch (Throwable e) {
            temp = new CanvasImage(128, 128);

            e.printStackTrace();
        }
        return temp;
    }

    private static final int RENDER_SCALE = 1;
    private static final int MAP_SIZE = FilledMapItem.field_30907;
    private static final int SECTION_SIZE = MathHelper.ceil(HardwareConstants.SCREEN_WIDTH * RENDER_SCALE / (double) MAP_SIZE);
    private static final int SECTION_HEIGHT = 6;
    private static final int SECTION_WIDTH = 8;

    private static final Consumer0 EMPTY_CALLBACK = () -> {
    };
    private static final int DRAW_OFFSET_X = (SECTION_WIDTH * 64 - HardwareConstants.SCREEN_WIDTH / 2);
    private static final int DRAW_OFFSET_Y = (SECTION_HEIGHT * 64 - HardwareConstants.SCREEN_HEIGHT / 2);

    private final ConsoleBoxConfig config;

    private final Store<Void> store;
    private final GameMemory memory;

    private final GamePalette palette;
    private final CombinedPlayerCanvas canvas;

    private final WasmFunctions.Consumer0 startCallback;
    private WasmFunctions.Consumer0 updateCallback;
    private final AudioController audioController;

    public GameCanvas(ConsoleBoxConfig config, AudioController audioController) {
        this.config = config;
        this.audioController = audioController;
        this.store = Store.withoutData();
        this.memory = new GameMemory(this.store);

        Engine engine = this.store.engine();

        Linker linker = new Linker(this.store.engine());
        this.defineImports(linker);

        Module module = new Module(engine, config.getGameData());
        linker.module(this.store, "", module);

        this.palette = new GamePalette(this.memory);
        this.canvas = DrawableCanvas.create(SECTION_WIDTH, SECTION_HEIGHT);
        CanvasUtils.clear(this.canvas, CanvasColor.GRAY_HIGH);
        if (DEFAULT_BACKGROUND != null) {
            var background = DEFAULT_BACKGROUND;
            var width = background.getWidth() * BACKGROUND_SCALE;
            var height = background.getHeight() * BACKGROUND_SCALE;
            var repeatsX = Math.ceilDiv(this.canvas.getWidth(), width);
            var repeatsY = Math.ceilDiv(this.canvas.getHeight(), height);

            for (int x = 0; x < repeatsX; x++) {
                for (int y = 0; y < repeatsY; y++) {
                    CanvasUtils.draw(this.canvas, x * width, y * height, width, height, background);
                }
            }
        }

        if (DEFAULT_OVERLAY != null) {
            var background = DEFAULT_OVERLAY;
            var width = background.getWidth() * BACKGROUND_SCALE;
            var height = background.getHeight() * BACKGROUND_SCALE;
            CanvasUtils.draw(this.canvas, this.canvas.getWidth() / 2 - width / 2, this.canvas.getHeight() / 2 - height / 2, width, height, background);
        }

        var text = """
                        ↑ | [W]
                        → | [D]
                        ← | [A]
                        ↓ | [S]
                        """;

        if (this.config.swapXZ()) {
            text += """
                    X | [Shift]
                    Z | [Space]
                    """;
        } else {
            text += """
                    X | [Space]
                    Z | [Shift]
                    """;
        }

        DefaultFonts.VANILLA.drawText(this.canvas, text, DRAW_OFFSET_X - 78, DRAW_OFFSET_Y + HardwareConstants.SCREEN_HEIGHT - 59, 8, CanvasColor.BLACK_HIGH);
        DefaultFonts.VANILLA.drawText(this.canvas, text, DRAW_OFFSET_X - 79, DRAW_OFFSET_Y + HardwareConstants.SCREEN_HEIGHT - 60, 8, CanvasColor.WHITE_HIGH);

        this.startCallback = this.getCallback(linker, "start");
        this.updateCallback = this.getCallback(linker, "update");
    }

    private void defineImport(Linker linker, String name, Func func) {
        Extern extern = Extern.fromFunc(func);
        linker.define(this.store, "env", name, extern);
    }

    private void defineImports(Linker linker) {
        this.defineImport(linker, "blit", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::blit));
        this.defineImport(linker, "blitSub", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::blitSub));

        this.defineImport(linker, "line", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::line));
        this.defineImport(linker, "hline", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::hline));
        this.defineImport(linker, "vline", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::vline));

        this.defineImport(linker, "oval", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::oval));
        this.defineImport(linker, "rect", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::rect));

        this.defineImport(linker, "text", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::text));
        this.defineImport(linker, "textUtf8", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::textUtf8));
        this.defineImport(linker, "textUtf16", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::textUtf16));

        this.defineImport(linker, "tone", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::tone));

        this.defineImport(linker, "diskr", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::diskr));
        this.defineImport(linker, "diskw", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, WasmValType.I32, this::diskw));

        this.defineImport(linker, "trace", WasmFunctions.wrap(this.store, WasmValType.I32, this::trace));
        this.defineImport(linker, "traceUtf8", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, this::traceUtf8));
        this.defineImport(linker, "traceUtf16", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, this::traceUtf16));
        this.defineImport(linker, "tracef", WasmFunctions.wrap(this.store, WasmValType.I32, WasmValType.I32, this::tracef));

        Extern memoryExtern = this.memory.createExtern();
        linker.define(this.store, "env", "memory", memoryExtern);
    }

    // External functions
    private void blit(int spriteAddress, int x, int y, int width, int height, int flags) {
        this.blitSub(spriteAddress, x, y, width, height, 0, 0, width, flags);
    }

    private void blitSub(int spriteAddress, int x, int y, int width, int height, int sourceX, int sourceY, int stride, int flags) {
        ByteBuffer buffer = this.memory.getFramebuffer();
        int drawColors = this.memory.readDrawColors();
        boolean bpp2 = (flags & 1) > 0;
        boolean flipX = (flags & 2) > 0;
        boolean flipY = (flags & 4) > 0;
        boolean rotate = (flags & 8) > 0;

        FramebufferRendering.drawSprite(buffer, drawColors, this.memory.getBuffer(), spriteAddress, x, y, width, height, sourceX, sourceY, stride, bpp2, flipX, flipY, rotate);
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
        if (y + length <= 0 || x < 0 || x >= HardwareConstants.SCREEN_HEIGHT) {
            return;
        }

        byte strokeColor = (byte) (this.memory.readDrawColors() & 0b1111);

        if (strokeColor != 0) {
            strokeColor -= 1;
            strokeColor &= 0x3;

            ByteBuffer buffer = this.memory.getFramebuffer();

            int startY = Math.max(0, y);
            int endY = Math.min(HardwareConstants.SCREEN_HEIGHT, y + length);

            for (int dy = startY; dy < endY; dy++) {
                FramebufferRendering.drawPointUnclipped(buffer, strokeColor, x, dy);
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

    // This function needs to work on raw bytes, as Java strips invalid chars
    private void drawText(byte[] string, int x, int y) {
        ByteBuffer buffer = this.memory.getFramebuffer();
        int drawColors = this.memory.readDrawColors();
        FramebufferRendering.drawText(buffer, drawColors, string, x, y);
    }

    private void text(int string, int x, int y) {
        this.drawText(this.memory.readStringRaw(string), x, y);
    }

    private void textUtf8(int string, int length, int x, int y) {
        this.drawText(this.memory.readUnterminatedStringRaw8(string, length), x, y);
    }

    private void textUtf16(int string, int length, int x, int y) {
        this.drawText(this.memory.readUnterminatedStringRaw16LE(string, length), x, y);
    }

    private void tone(int frequency, int duration, int volume, int flags) {
        var channel = switch (flags & 0b11) {
            case 0 -> AudioChannel.PULSE_1;
            case 1 -> AudioChannel.PULSE_2;
            case 2 -> AudioChannel.TRIANGLE;
            default -> AudioChannel.NOISE;
        };

        var duty = switch ((flags >> 2) & 0b11) {
            case 0 -> ToneDuty.MODE_12_5;
            case 1 -> ToneDuty.MODE_25;
            case 2 -> ToneDuty.MODE_50;
            default -> ToneDuty.MODE_75;
        };

        var pan = switch ((flags >> 4) & 0b11) {
            case 1 -> TonePan.LEFT;
            case 2 -> TonePan.RIGHT;
            default -> TonePan.CENTER;
        };


        var freq1 = frequency & 0xFFFF;
        var freq2 = (frequency >> 16) & 0xFFFF;

        var sustainTime = duration & 0xFF;

        var volumeActual = volume & 0xFF;
        var volumePeak = (volume >> 8) & 0xFF;


        this.audioController.playSound(channel, duty, pan, freq1, freq2, sustainTime, volumeActual, volumePeak);

        // Intentionally empty as sound is unsupported
    }

    private int diskr(int address, int size) {
        //this.//memory.copyDisk(address, size);
        // Intentionally empty as persistent storage is unsupported
        return 0;
    }

    private int diskw(int address, int size) {
        // Intentionally empty as persistent storage is unsupported
        return 0;
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
                byte color = (byte) (buffer.get(colorAddress) >>> (index % 8) & 0b11);

                this.canvas.set(x + DRAW_OFFSET_X,
                        y + DRAW_OFFSET_Y,
                        this.palette.getColor(color));
                index += 2;
            }
        }
    }

    public void updateGamepad(int id, boolean forward, boolean left, boolean backward, boolean right, boolean isSneaking, boolean isJumping) {
        synchronized (this) {
            this.memory.updateGamepad(id, forward, left, backward, right, isSneaking, isJumping);
        }
    }

    public void tick(long lastTime) {
        synchronized (this) {
            try {
                this.update();
                this.palette.update();
                this.render();
            } catch (Throwable e) {
                this.drawError(e);
            }
            //DefaultFonts.VANILLA.drawText(this.canvas, "TIME: +" + lastTime, 0, 0, 8, CanvasColor.RED_HIGH);
            this.canvas.sendUpdates();
        }
    }

    private void drawError(Throwable e) {
        var width = DefaultFonts.VANILLA.getTextWidth("ERROR!", 16);

        CanvasUtils.fill(this.canvas, (HardwareConstants.SCREEN_WIDTH - width) / 2 - 5 + DRAW_OFFSET_X, 11 + DRAW_OFFSET_Y,
                (HardwareConstants.SCREEN_WIDTH - width) / 2 + width + 5 + DRAW_OFFSET_X, 16 * 2 + 5 + DRAW_OFFSET_Y, CanvasColor.BLUE_HIGH);
        //CanvasUtils.fill(this.canvas, 0, 0, HardwareConstants.SCREEN_HEIGHT, HardwareConstants.SCREEN_WIDTH, CanvasColor.BLUE_HIGH);
        DefaultFonts.VANILLA.drawText(this.canvas, "ERROR!", (HardwareConstants.SCREEN_WIDTH - width) / 2 + 1 + DRAW_OFFSET_X, 17 + DRAW_OFFSET_Y, 16, CanvasColor.BLACK_LOW);
        DefaultFonts.VANILLA.drawText(this.canvas, "ERROR!", (HardwareConstants.SCREEN_WIDTH - width) / 2 + DRAW_OFFSET_X, 16 + DRAW_OFFSET_Y, 16, CanvasColor.RED_HIGH);

        String message1;
        String message2;

        if (e instanceof TrapError trapError) {
            message1 = "Execution error! (TRAP)";
            message2 = trapError.trap() != null ? trapError.trap().name() : "<NULL>";
        } else if (e instanceof I32ExitError exitError) {
            message1 = "Execution error! (EXIT)";
            message2 = "Exit code " + exitError.exitCode();
        } else {
            message1 = "Runtime error!";
            message2 = e.getMessage();
        }

        List<String> message2Split = new ArrayList<>();

        var builder = new StringBuilder();

        for (var x : message2.toCharArray()) {
            if (DefaultFonts.VANILLA.getTextWidth(builder.toString() + x, 8) > HardwareConstants.SCREEN_WIDTH - 10) {
                message2Split.add(builder.toString());
                builder = new StringBuilder();
            }
            builder.append(x);
        }
        message2Split.add(builder.toString());

        CanvasUtils.fill(this.canvas, 0 + DRAW_OFFSET_X, 63 + DRAW_OFFSET_Y,
                HardwareConstants.SCREEN_WIDTH + DRAW_OFFSET_X, 65 + 8 + DRAW_OFFSET_Y, CanvasColor.BLUE_HIGH);

        DefaultFonts.VANILLA.drawText(this.canvas, message1, 5 + DRAW_OFFSET_X, 64 + DRAW_OFFSET_Y, 8, CanvasColor.WHITE_HIGH);

        CanvasUtils.fill(this.canvas, 0 + DRAW_OFFSET_X, 63 + 10 + DRAW_OFFSET_Y,
                HardwareConstants.SCREEN_WIDTH + DRAW_OFFSET_X, 65 + 10 + message2Split.size() * 10 + DRAW_OFFSET_Y, CanvasColor.BLUE_HIGH);
        for (int i = 0; i < message2Split.size(); i++) {
            DefaultFonts.VANILLA.drawText(this.canvas, message2Split.get(i), 5 + DRAW_OFFSET_X, 64 + 10 + 10 * i + DRAW_OFFSET_Y, 8, CanvasColor.WHITE_HIGH);
        }
    }

    public void start() {
        try {
            this.startCallback.accept();
            this.palette.update();
            this.render();
        } catch (Throwable e) {
            this.drawError(e);
            e.printStackTrace();
            this.updateCallback = EMPTY_CALLBACK;
        }
    }

    public BlockPos getDisplayPos() {
        return new BlockPos(-SECTION_WIDTH, SECTION_HEIGHT + 100, 0);
    }

    public Vec3d getSpawnPos() {
        BlockPos displayPos = this.getDisplayPos();

        return new Vec3d(displayPos.getX() + SECTION_WIDTH * 0.5, displayPos.getY() - SECTION_HEIGHT * 0.5f + 1, 1.5);
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
}
