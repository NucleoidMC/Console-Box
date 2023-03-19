package io.github.haykam821.consolebox.game;

import eu.pb4.mapcanvas.api.utils.VirtualDisplay;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.MuleEntity;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.GameMode;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;
import xyz.nucleoid.plasmid.game.GameActivity;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.game.player.PlayerOffer;
import xyz.nucleoid.plasmid.game.player.PlayerOfferResult;
import xyz.nucleoid.plasmid.game.rule.GameRuleType;
import xyz.nucleoid.stimuli.event.player.PlayerC2SPacketEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

import java.util.Set;

public class ConsoleBoxGame implements GamePlayerEvents.Add, GameActivityEvents.Destroy, GameActivityEvents.Tick, GameActivityEvents.Enable, GamePlayerEvents.Remove, GamePlayerEvents.Offer, PlayerDamageEvent, PlayerDeathEvent, PlayerC2SPacketEvent {
	private final Thread thread;

	private final GameSpace gameSpace;
	private final ServerWorld world;
	private final ConsoleBoxConfig config;

	private final GameCanvas canvas;
	private final VirtualDisplay display;

	private ServerPlayerEntity mainPlayer;
	private volatile boolean runs = true;

	public ConsoleBoxGame(GameSpace gameSpace, ServerWorld world, ConsoleBoxConfig config, GameCanvas canvas, VirtualDisplay display) {
		this.gameSpace = gameSpace;
		this.world = world;
		this.config = config;

		this.canvas = canvas;
		this.display = display;
		this.thread = new Thread(this::runThread);
	}

	public static void setRules(GameActivity activity) {
		activity.deny(GameRuleType.BLOCK_DROPS);
		activity.deny(GameRuleType.BREAK_BLOCKS);
		activity.deny(GameRuleType.CRAFTING);
		activity.deny(GameRuleType.DISMOUNT_VEHICLE);
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
			.setGenerator(new VoidChunkGenerator(context.server().getRegistryManager().get(Registry.BIOME_KEY)));

		GameCanvas canvas = new GameCanvas(config);
		canvas.start();

		return context.openWithWorld(worldConfig, (activity, world) -> {
			VirtualDisplay display = VirtualDisplay.builder(canvas.getCanvas(), canvas.getDisplayPos(), Direction.SOUTH)
				.invisible()
				.build();

			ConsoleBoxGame phase = new ConsoleBoxGame(activity.getGameSpace(), world, config, canvas, display);
			ConsoleBoxGame.setRules(activity);

			// Listeners
			activity.listen(GamePlayerEvents.ADD, phase);
			activity.listen(GameActivityEvents.ENABLE, phase);
			activity.listen(GameActivityEvents.DESTROY, phase);
			activity.listen(GameActivityEvents.TICK, phase);
			activity.listen(GamePlayerEvents.OFFER, phase);
			activity.listen(PlayerDamageEvent.EVENT, phase);
			activity.listen(PlayerDeathEvent.EVENT, phase);
			activity.listen(GamePlayerEvents.REMOVE, phase);
			activity.listen(PlayerC2SPacketEvent.EVENT, phase);
		});
	}

	// Listeners
	@Override
	public void onAddPlayer(ServerPlayerEntity player) {
		this.display.addPlayer(player);
		this.display.getCanvas().addPlayer(player);
		player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_MODE_CHANGED, GameMode.SPECTATOR.getId()));
	}

	@Override
	public void onDestroy(GameCloseReason reason) {
		this.display.destroy();
		this.display.getCanvas().destroy();
		this.runs = false;
	}

	@Override
	public ActionResult onPacket(ServerPlayerEntity player, Packet<?> packet) {
		if (packet instanceof PlayerInputC2SPacket playerInputC2SPacket) {
			var isJumping = this.config.swapXZ() ? playerInputC2SPacket.isSneaking() : playerInputC2SPacket.isJumping();
			var isSneaking = !this.config.swapXZ() ? playerInputC2SPacket.isSneaking() : playerInputC2SPacket.isJumping();

			this.canvas.updateGamepad(playerInputC2SPacket.getSideways(), playerInputC2SPacket.getForward(),
					isSneaking, isJumping);
		}

		return ActionResult.PASS;
	}

	@Override
	public void onTick() {
		if (this.mainPlayer == null) return;
		this.mainPlayer.networkHandler.sendPacket(
				new PlayerPositionLookS2CPacket(this.mainPlayer.getX(), this.mainPlayer.getY(), this.mainPlayer.getZ(), 180f, 0f,
				Set.of(), 0, false));
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
	public PlayerOfferResult onOfferPlayer(PlayerOffer offer) {
		if (this.mainPlayer == null) {
			Vec3d spawnPos = this.canvas.getSpawnPos();

			return offer.accept(this.world, spawnPos).and(() -> {
				this.mainPlayer = offer.player();

				this.spawnMount(spawnPos, this.mainPlayer);
				this.initializePlayer(this.mainPlayer, GameMode.ADVENTURE);
			});
		} else {
			Vec3d pos = this.mainPlayer.getPos().add(this.config.spectatorSpawnOffset());
			return offer.accept(this.world, pos).and(() -> {
				this.initializePlayer(offer.player(), GameMode.SPECTATOR);
			});
		}
	}

	@Override
	public ActionResult onDamage(ServerPlayerEntity player, DamageSource source, float damage) {
		return ActionResult.FAIL;
	}

	@Override
	public ActionResult onDeath(ServerPlayerEntity player, DamageSource source) {
		if (player == this.mainPlayer) {
			this.gameSpace.close(GameCloseReason.FINISHED);
		}
		return ActionResult.FAIL;
	}

	@Override
	public void onRemovePlayer(ServerPlayerEntity player) {
		this.display.removePlayer(player);
		this.display.getCanvas().removePlayer(player);

		if (player == this.mainPlayer) {
			this.gameSpace.close(GameCloseReason.FINISHED);
		}
	}

	// Utilities
	private Entity spawnMount(Vec3d playerPos, ServerPlayerEntity player) {
		MuleEntity mount = EntityType.MULE.create(this.world);

		double y = playerPos.getY() - mount.getMountedHeightOffset() - player.getHeightOffset() + player.getY() - player.getEyeY();
		mount.setPos(playerPos.getX(), y, playerPos.getZ());
		mount.setYaw(this.canvas.getSpawnAngle());

		mount.setAiDisabled(true);
		mount.setNoGravity(true);
		mount.setSilent(true);
		mount.setPersistent();

		// Prevent mount from being visible
		mount.addStatusEffect(this.createInfiniteStatusEffect(StatusEffects.INVISIBILITY));
		mount.setInvisible(true);

		// Remove mount hearts from HUD
		mount.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(0);

		this.world.spawnEntity(mount);
		player.startRiding(mount, true);

		return null;
	}

	private void initializePlayer(ServerPlayerEntity player, GameMode gameMode) {
		player.changeGameMode(gameMode);
		player.addStatusEffect(this.createInfiniteStatusEffect(StatusEffects.NIGHT_VISION));

		player.setYaw(this.canvas.getSpawnAngle());
		player.setPitch(Float.MIN_VALUE);
	}

	private StatusEffectInstance createInfiniteStatusEffect(StatusEffect statusEffect) {
		return new StatusEffectInstance(statusEffect, Integer.MAX_VALUE, 0, true, false);
	}
}