package io.github.haykam821.consolebox.game;

import com.dylibso.chicory.experimental.aot.AotMachine;
import com.dylibso.chicory.experimental.hostmodule.annotations.HostModule;
import com.dylibso.chicory.experimental.hostmodule.annotations.WasmExport;
import com.dylibso.chicory.runtime.*;
import com.dylibso.chicory.wasm.ChicoryException;
import com.dylibso.chicory.wasm.MalformedException;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import com.dylibso.chicory.wasm.types.ValueType;
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
import io.github.haykam821.consolebox.mixin.ParserAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.FilledMapItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;


@HostModule("env")
public class GameCanvas {
    private static final Logger LOGGER = LoggerFactory.getLogger("GameCanvas");

    private static final CanvasImage DEFAULT_BACKGROUND = readImage("default_background");
    private static final CanvasImage DEFAULT_OVERLAY = readImage("default_overlay");
    private static final int BACKGROUND_SCALE = 2;
    private Throwable error;

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

    private static final ExportFunction EMPTY_CALLBACK = (long... longs) -> new long[0];
    private static final int DRAW_OFFSET_X = (SECTION_WIDTH * 64 - HardwareConstants.SCREEN_WIDTH / 2);
    private static final int DRAW_OFFSET_Y = (SECTION_HEIGHT * 64 - HardwareConstants.SCREEN_HEIGHT / 2);

    private final ConsoleBoxConfig config;

    private final GameMemory memory;

    private final GamePalette palette;
    private final CombinedPlayerCanvas canvas;

    private final ExportFunction startCallback;
    private ExportFunction updateCallback;
    private final AudioController audioController;
    private SaveHandler saveHandler = SaveHandler.NO_OP;

    public GameCanvas(ConsoleBoxConfig config, AudioController audioController) {


        this.config = config;
        this.audioController = audioController;
        this.memory = new GameMemory();

        var importBuilder = ImportValues.builder();
        this.defineImports(importBuilder);
        WasmModule.Builder moduleBuilder = WasmModule.builder();
        try {
            var parser = new Parser();
            parser.parse(new ByteArrayInputStream(config.getGameData()), (s) -> ParserAccessor.callOnSection(moduleBuilder, s));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        moduleBuilder.setMemorySection(null);
        var module = moduleBuilder.build();


        Instance instance = Instance.builder(module)
                .withImportValues(importBuilder.build())
                .withMachineFactory(AotMachine::new)
                .build();

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

        this.startCallback = this.getCallback(instance, "start");
        this.updateCallback = this.getCallback(instance, "update");
    }

    private void defineImports(ImportValues.Builder linker) {
        linker.addFunction(toHostFunctions());
        linker.addMemory(new ImportMemory("env", "memory", this.memory.memory()));
    }

    // External functions
    @WasmExport("blit")
    public void blit(int spriteAddress, int x, int y, int width, int height, int flags) {
        this.blitSub(spriteAddress, x, y, width, height, 0, 0, width, flags);
    }

    @WasmExport("blitSub")
    public void blitSub(int spriteAddress, int x, int y, int width, int height, int sourceX, int sourceY, int stride, int flags) {
        ByteBuffer buffer = this.memory.getFramebuffer();
        int drawColors = this.memory.readDrawColors();
        boolean bpp2 = (flags & 1) > 0;
        boolean flipX = (flags & 2) > 0;
        boolean flipY = (flags & 4) > 0;
        boolean rotate = (flags & 8) > 0;

        FramebufferRendering.drawSprite(buffer, drawColors, this.memory.getBuffer(), spriteAddress, x, y, width, height, sourceX, sourceY, stride, bpp2, flipX, flipY, rotate);
    }

    @WasmExport("line")
    public void line(int x1, int y1, int x2, int y2) {
        ByteBuffer buffer = this.memory.getFramebuffer();
        int drawColors = this.memory.readDrawColors();

        FramebufferRendering.drawLine(buffer, drawColors, x1, y1, x2, y2);
    }

    @WasmExport("hline")
    public void hline(int x, int y, int length) {
        byte strokeColor = (byte) (this.memory.readDrawColors() & 0b1111);

        if (strokeColor != 0) {
            strokeColor -= 1;
            strokeColor &= 0x3;

            ByteBuffer buffer = this.memory.getFramebuffer();
            FramebufferRendering.drawHLineUnclipped(buffer, strokeColor, x, y, x + length);
        }
    }

    @WasmExport("vline")
    public void vline(int x, int y, int length) {
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

    @WasmExport("oval")
    public void oval(int x, int y, int width, int height) {
        ByteBuffer buffer = this.memory.getFramebuffer();
        int drawColors = this.memory.readDrawColors();

        FramebufferRendering.drawOval(buffer, drawColors, x, y, width, height);
    }

    @WasmExport("rect")
    public void rect(int x, int y, int width, int height) {
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

    @WasmExport("text")
    public void text(int string, int x, int y) {
        this.drawText(this.memory.readStringRaw(string), x, y);
    }

    @WasmExport("textUtf8")
    public void textUtf8(int string, int length, int x, int y) {
        this.drawText(this.memory.readUnterminatedStringRaw8(string, length), x, y);
    }

    @WasmExport("textUtf16")
    public void textUtf16(int string, int length, int x, int y) {
        this.drawText(this.memory.readUnterminatedStringRaw16LE(string, length), x, y);
    }

    @WasmExport("tone")
    public void tone(int frequency, int duration, int volume, int flags) {
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

    @WasmExport("diskr")
    public int diskr(int address, int size) {
        if (!this.saveHandler.canUse()) {
            return 0;
        }
        var data = this.saveHandler.getData();
        if (data == null) {
            return 0;
        }

        this.memory.getBuffer().put(address, data, 0, size);
        // Intentionally empty as persistent storage is unsupported
        return 0;
    }

    @WasmExport("diskw")
    public int diskw(int address, int size) {
        if (!this.saveHandler.canUse()) {
            return 0;
        }
        try {
            var bytes = new byte[Math.min(size, 1024)];
            this.memory.getBuffer().get(address, bytes);
            this.saveHandler.setData(ByteBuffer.wrap(bytes));
        } catch (Throwable e) {
            this.error = e;
        }
        return 0;
    }

    @WasmExport("trace")
    public void trace(int string) {
        LOGGER.trace("From game: {}", this.memory.readString(string));
    }

    @WasmExport("traceUtf8")
    public void traceUtf8(int string, int length) {
        LOGGER.trace("From game: {}", this.memory.readUnterminatedString(string, length, StandardCharsets.UTF_8));
    }

    @WasmExport("traceUtf16")
    public void traceUtf16(int string, int length) {
        LOGGER.trace("From game: {}", this.memory.readUnterminatedString(string, length, StandardCharsets.UTF_16LE));
    }

    @WasmExport("tracef")
    public void tracef(int string, int stack) {
        LOGGER.trace("From game (unformatted): {}", this.memory.readString(string));
    }

    private HostFunction[] toHostFunctions() {
        return GameCanvas_ModuleFactory.toHostFunctions(this);
    }

    // Behavior
    private void update() {
        if (!this.memory.readSystemPreserveFramebuffer()) {
            ByteBuffer buffer = this.memory.getFramebuffer();
            for (int index = 0; index < buffer.limit(); index++) {
                buffer.put(index, (byte) 0x0);
            }
        }

        this.updateCallback.apply();
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
        /*this.canvas.set(Short.reverseBytes(this.memory.getBuffer().getShort(0x001a)) + DRAW_OFFSET_X,
                Short.reverseBytes(this.memory.getBuffer().getShort(0x001c)) + DRAW_OFFSET_Y, CanvasColor.RED_HIGH);*/
    }

    public void updateGamepad(int id, boolean forward, boolean left, boolean backward, boolean right, boolean isSneaking, boolean isJumping) {
        synchronized (this) {
            this.memory.updateGamepad(id, forward, left, backward, right, isSneaking, isJumping);
        }
    }

    public void updateMousePosition(int id, int mouseX, int mouseY) {
        synchronized (this) {
            //if (this.mouse == null) {
            ////    this.mouse = this.canvas.createIcon(MapDecorationTypes.PLAYER, (mouseX + DRAW_OFFSET_X) * 2, (mouseY + DRAW_OFFSET_Y) * 2, (byte) 0, null);
            //} else {
            //    this.mouse.move((mouseX + DRAW_OFFSET_X) * 2, (mouseY + DRAW_OFFSET_Y) * 2, this.mouse.getRotation());
            //    System.out.println((mouseX + DRAW_OFFSET_X) * 2);
            //    System.out.println((mouseY + DRAW_OFFSET_Y) * 2);
            //}
            this.memory.updateMousePosition(id, mouseX, mouseY);
        }
    }

    public void updateMouseState(int id, boolean leftClick, boolean rightClick, boolean middleClick) {
        synchronized (this) {
            this.memory.updateMouseState(id, leftClick, rightClick, middleClick);
        }
    }

    public void tick(long lastTime) {
        synchronized (this) {
            if (this.error != null) {
                this.drawError(error);
            } else {
                try {
                    this.update();
                    this.palette.update();
                    this.render();
                } catch (Throwable e) {
                    this.error = e;
                }
            }
            CanvasUtils.fill(this.canvas, DRAW_OFFSET_X, DRAW_OFFSET_Y - 18, DRAW_OFFSET_X + 80, DRAW_OFFSET_Y - 8, CanvasColor.BLACK_NORMAL);
            DefaultFonts.VANILLA.drawText(this.canvas, "TIME: " + lastTime, DRAW_OFFSET_X + 2, DRAW_OFFSET_Y - 17, 8, CanvasColor.WHITE_HIGH);
            this.canvas.sendUpdates();
        }
    }

    public void clearError() {
        this.error = null;
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

        if (e instanceof TrapException trapError) {
            message1 = "Execution error! (TRAP)";
            message2 = trapError.getMessage();
        } /*else if (e instanceof I32ExitError exitError) {
            message1 = "Execution error! (EXIT)";
            message2 = "Exit code " + exitError.exitCode();
        } */else {
            message1 = "Runtime error!";
            message2 = e.getMessage();
        }

        List<String> message2Split = new ArrayList<>();

        var builder = new StringBuilder();

        for (var x : message2.toCharArray()) {
            if (x == '\n' || DefaultFonts.VANILLA.getTextWidth(builder.toString() + x, 8) > HardwareConstants.SCREEN_WIDTH - 10) {
                message2Split.add(builder.toString());
                builder = new StringBuilder();
            }
            if (x != '\n') {
                builder.append(x);
            }
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
        synchronized (this) {
            try {
                this.startCallback.apply();
                this.palette.update();
                this.render();
            } catch (Throwable e) {
                this.error = e;
                this.drawError(e);
                e.printStackTrace();
                this.updateCallback = EMPTY_CALLBACK;
            }
        }
    }

    public void setSaveHandler(SaveHandler handler) {
        this.saveHandler = handler;
    }


    public BlockPos getDisplayPos() {
        return new BlockPos(-SECTION_WIDTH, SECTION_HEIGHT + 100, 0);
    }

    public Vec3d getSpawnPos() {
        BlockPos displayPos = this.getDisplayPos();

        return new Vec3d(displayPos.getX() + SECTION_WIDTH * 0.5, displayPos.getY() - SECTION_HEIGHT * 0.5f + 1, 1.3);
    }

    public int getSpawnAngle() {
        return 180;
    }

    public PlayerCanvas getCanvas() {
        return this.canvas;
    }

    private ExportFunction getCallback(Instance instance, String name) {
        try {
            return instance.export(name);
        } catch (Throwable e) {
            return EMPTY_CALLBACK;
        }
    }
}
