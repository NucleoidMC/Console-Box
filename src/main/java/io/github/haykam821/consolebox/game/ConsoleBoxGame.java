package io.github.haykam821.consolebox.game;

import eu.pb4.mapcanvas.api.utils.VirtualDisplay;
import io.github.haykam821.consolebox.game.audio.BaseAudioController;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.MuleEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerLoadedC2SPacket;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.SetCameraEntityS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.DimensionTypes;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;
import xyz.nucleoid.plasmid.api.game.*;
import xyz.nucleoid.plasmid.api.game.common.PlayerLimiter;
import xyz.nucleoid.plasmid.api.game.common.config.PlayerLimiterConfig;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptor;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptorResult;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerC2SPacketEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public class ConsoleBoxGame implements GamePlayerEvents.Add, GameActivityEvents.Destroy, GameActivityEvents.Tick, GameActivityEvents.Enable, GamePlayerEvents.Remove, GamePlayerEvents.Accept, PlayerDamageEvent, PlayerDeathEvent, PlayerC2SPacketEvent {
    private final Thread thread;
    private final GameSpace gameSpace;
    private final ServerWorld world;
    private final ConsoleBoxConfig config;
    private final GameCanvas canvas;
    private final VirtualDisplay display;
    private final Entity cameraEntity;
    private final ServerPlayerEntity[] players = new ServerPlayerEntity[4];
    private volatile boolean runs = true;
    private int playerCount = 0;

    public ConsoleBoxGame(GameSpace gameSpace, ServerWorld world, ConsoleBoxConfig config, GameCanvas canvas, Entity cameraEntity, VirtualDisplay display) {
        this.gameSpace = gameSpace;
        this.world = world;
        this.config = config;

        this.cameraEntity = cameraEntity;
        this.canvas = canvas;
        this.display = display;
        this.thread = new Thread(this::runThread);
    }

    public static void setRules(GameActivity activity) {
        activity.deny(GameRuleType.BLOCK_DROPS);
        activity.deny(GameRuleType.BREAK_BLOCKS);
        activity.deny(GameRuleType.CRAFTING);
        activity.deny(GameRuleType.DISMOUNT_VEHICLE);
        activity.deny(GameRuleType.STOP_SPECTATING_ENTITY);
        activity.deny(GameRuleType.FALL_DAMAGE);
        activity.deny(GameRuleType.FIRE_TICK);
        activity.deny(GameRuleType.FLUID_FLOW);
        activity.deny(GameRuleType.HUNGER);
        activity.deny(GameRuleType.ICE_MELT);
        activity.deny(GameRuleType.MODIFY_ARMOR);
        activity.deny(GameRuleType.MODIFY_INVENTORY);
        activity.deny(GameRuleType.PICKUP_ITEMS);
        activity.deny(GameRuleType.PLACE_BLOCKS);
        activity.deny(GameRuleType.PLAYER_PROJECTILE_KNOCKBACK);
        activity.deny(GameRuleType.PORTALS);
        activity.deny(GameRuleType.PVP);
        activity.deny(GameRuleType.SWAP_OFFHAND);
        activity.deny(GameRuleType.THROW_ITEMS);
        activity.deny(GameRuleType.TRIDENTS_LOYAL_IN_VOID);
        activity.deny(GameRuleType.UNSTABLE_TNT);
    }

    public static GameOpenProcedure open(GameOpenContext<ConsoleBoxConfig> context) {
        ConsoleBoxConfig config = context.config();

        RuntimeWorldConfig worldConfig = new RuntimeWorldConfig()
                .setDimensionType(DimensionTypes.OVERWORLD_CAVES)
                .setGenerator(new VoidChunkGenerator(context.server()));


        var audioController = new BaseAudioController();
        GameCanvas canvas = new GameCanvas(config, audioController);

        return context.openWithWorld(worldConfig, (activity, world) -> {
            VirtualDisplay display = VirtualDisplay.builder(canvas.getCanvas(), canvas.getDisplayPos(), Direction.SOUTH)
                    .invisible()
                    .build();

            var camera = EntityType.ITEM_DISPLAY.create(world, SpawnReason.LOAD);
            assert camera != null;
            camera.setInvisible(true);
            camera.setPosition(canvas.getSpawnPos());
            camera.setYaw(canvas.getSpawnAngle());
            world.spawnEntity(camera);

            var leftAudio = EntityType.ITEM_DISPLAY.create(world, SpawnReason.LOAD);
            assert leftAudio != null;
            leftAudio.setInvisible(true);
            leftAudio.setPosition(canvas.getSpawnPos().add(2, 0, 0));
            world.spawnEntity(leftAudio);

            var rightAudio = EntityType.ITEM_DISPLAY.create(world, SpawnReason.LOAD);
            assert rightAudio != null;
            rightAudio.setInvisible(true);
            rightAudio.setPosition(canvas.getSpawnPos().add(-2, 0, 0));
            world.spawnEntity(rightAudio);

            ConsoleBoxGame phase = new ConsoleBoxGame(activity.getGameSpace(), world, config, canvas, camera, display);
            audioController.setOutput(camera, leftAudio, rightAudio, activity.getGameSpace().getPlayers()::sendPacket);
            ConsoleBoxGame.setRules(activity);

            PlayerLimiter.addTo(activity, new PlayerLimiterConfig(phase.players.length));

            // Listeners
            activity.listen(GamePlayerEvents.ADD, phase);
            activity.listen(GameActivityEvents.ENABLE, phase);
            activity.listen(GameActivityEvents.DESTROY, phase);
            activity.listen(GameActivityEvents.TICK, phase);
            activity.listen(GamePlayerEvents.ACCEPT, phase);
            activity.listen(PlayerDamageEvent.EVENT, phase);
            activity.listen(PlayerDeathEvent.EVENT, phase);
            activity.listen(GamePlayerEvents.REMOVE, phase);
            activity.listen(PlayerC2SPacketEvent.EVENT, phase);

            canvas.start();
        });
    }

    // Listeners
    @Override
    public void onAddPlayer(ServerPlayerEntity player) {
        this.display.addPlayer(player);
        this.display.getCanvas().addPlayer(player);
        player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_MODE_CHANGED, GameMode.SPECTATOR.getId()));
        player.networkHandler.sendPacket(new SetCameraEntityS2CPacket(this.cameraEntity));
    }

    @Override
    public void onDestroy(GameCloseReason reason) {
        this.display.destroy();
        this.display.getCanvas().destroy();
        this.runs = false;
    }

    @Override
    public EventResult onPacket(ServerPlayerEntity player, Packet<?> packet) {
        int id = -1;
        for (int i = 0; i < 4; i++) {
            if (this.players[i] == player) {
                id = i;
                break;
            }
        }

        if (id == -1) {
            return EventResult.PASS;
        }

        if (packet instanceof PlayerInputC2SPacket playerInputC2SPacket) {
            PlayerInput input = playerInputC2SPacket.input();

            var isJumping = this.config.swapXZ() ? input.sneak() : input.jump();
            var isSneaking = !this.config.swapXZ() ? input.sneak() : input.jump();

            this.canvas.updateGamepad(id, input.forward(), input.left(), input.backward(), input.right(),
                    isSneaking, isJumping);
        } else if (packet instanceof PlayerLoadedC2SPacket) {
            player.networkHandler.sendPacket(new SetCameraEntityS2CPacket(this.cameraEntity));
        }

        return EventResult.PASS;
    }

    @Override
    public void onTick() {
        for (var player : this.gameSpace.getPlayers()) {
            if (player.getCameraEntity() != this.cameraEntity && this.cameraEntity.age > 2) {
                player.setCameraEntity(this.cameraEntity);
            }
        }
    }


    @Override
    public void onEnable() {
        this.thread.start();
    }

    private void runThread() {
        try {
            long time;
            long lastTime = 0;
            while (this.runs) {
                time = System.currentTimeMillis();
                this.canvas.tick(lastTime);
                lastTime = System.currentTimeMillis() - time;

                Thread.sleep(Math.max(1000 / 60 - System.currentTimeMillis() + time, 1));
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    // /game open {type:"consolebox:console_box", game:"consolebox:cart"}
    @Override
    public JoinAcceptorResult onAcceptPlayers(JoinAcceptor acceptor) {
        Vec3d spawnPos = this.canvas.getSpawnPos();

        if (acceptor.intent().canPlay()) {
            for (int i = 0; i < players.length; i++) {
                final var x = i;
                if (this.players[x] == null) {
                    return acceptor.teleport(this.world, spawnPos).thenRunForEach(player -> {
                        this.players[x] = player;
                        this.playerCount++;
                        this.spawnMount(spawnPos.add(0, 10, 0), this.players[x]);
                        this.initializePlayer(this.players[x], GameMode.SPECTATOR);
                    });
                }
            }
        }

        Vec3d pos = spawnPos.add(this.config.spectatorSpawnOffset());
        return acceptor.teleport(this.world, pos).thenRunForEach(player -> {
            this.initializePlayer(player, GameMode.SPECTATOR);
        });
    }

    @Override
    public EventResult onDamage(ServerPlayerEntity player, DamageSource source, float damage) {
        return EventResult.DENY;
    }

    @Override
    public EventResult onDeath(ServerPlayerEntity player, DamageSource source) {
        return EventResult.DENY;
    }

    @Override
    public void onRemovePlayer(ServerPlayerEntity player) {
        this.display.removePlayer(player);
        this.display.getCanvas().removePlayer(player);

        if (player.getVehicle() != null) {
            player.getVehicle().discard();
        }

        if (player == this.players[0]) {
            this.gameSpace.close(GameCloseReason.FINISHED);
        } else {
            for (int i = 0; i < 4; i++) {
                if (this.players[i] == player) {
                    this.players[i] = null;
                    this.canvas.updateGamepad(i, false, false, false, false, false, false);
                    this.playerCount--;
                    break;
                }
            }
        }
    }

    // Utilities
    private void spawnMount(Vec3d playerPos, ServerPlayerEntity player) {
        MuleEntity mount = EntityType.MULE.create(this.world, SpawnReason.JOCKEY);
        mount.calculateDimensions();
        double y = playerPos.getY() - 1.25f;
        mount.setPos(playerPos.getX(), y, playerPos.getZ() + 2);
        mount.setYaw(this.canvas.getSpawnAngle());

        mount.setAiDisabled(true);
        mount.setNoGravity(true);
        mount.setSilent(true);
        mount.setPersistent();
        mount.getAttributeInstance(EntityAttributes.SCALE).setBaseValue(0);

        // Prevent mount from being visible
        mount.addStatusEffect(this.createInfiniteStatusEffect(StatusEffects.INVISIBILITY));
        mount.setInvisible(true);

        // Remove mount hearts from HUD
        mount.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(0);

        this.world.spawnEntity(mount);
        player.startRiding(mount, true);

    }

    private void initializePlayer(ServerPlayerEntity player, GameMode gameMode) {
        player.changeGameMode(gameMode);
        player.setInvisible(true);
        player.setInvulnerable(true);
        player.addStatusEffect(this.createInfiniteStatusEffect(StatusEffects.NIGHT_VISION));
        player.addStatusEffect(this.createInfiniteStatusEffect(StatusEffects.INVISIBILITY));

        player.setYaw(this.canvas.getSpawnAngle());
        player.setPitch(Float.MIN_VALUE);
    }

    private StatusEffectInstance createInfiniteStatusEffect(RegistryEntry<StatusEffect> statusEffect) {
        return new StatusEffectInstance(statusEffect, StatusEffectInstance.INFINITE, 0, true, false);
    }
}