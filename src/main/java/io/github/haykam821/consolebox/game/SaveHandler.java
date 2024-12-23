package io.github.haykam821.consolebox.game;

import com.mojang.serialization.Codec;
import eu.pb4.playerdata.api.PlayerDataApi;
import eu.pb4.playerdata.api.storage.NbtCodecDataStorage;
import eu.pb4.playerdata.api.storage.PlayerDataStorage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.util.PlayerRef;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public interface SaveHandler {
    SaveHandler NO_OP = new NoOp();

    static SaveHandler player(ServerPlayerEntity player, GameSpace gameSpace, Identifier identifier) {
        return new Player(gameSpace, PlayerRef.of(player), identifier);
    }

    @Nullable
    ByteBuffer getData();
    boolean setData(@Nullable ByteBuffer data);

    boolean canUse();

    record NoOp() implements SaveHandler {
        @Override
        public @Nullable ByteBuffer getData() {
            return null;
        }

        @Override
        public boolean setData(@Nullable ByteBuffer data) {
            return false;
        }

        @Override
        public boolean canUse() {
            return false;
        }
    }

    record Player(GameSpace gameSpace, PlayerRef ref, Identifier game) implements SaveHandler {
        public static final PlayerDataStorage<Map<Identifier, ByteBuffer>> STORAGE = new NbtCodecDataStorage<>("consolebox_saves", Codec.unboundedMap(Identifier.CODEC, Codec.BYTE_BUFFER));

        @Nullable
        public ByteBuffer getData() {
            var player = ref.getEntity(gameSpace);
            if (player == null) {
                return null;
            }

            var x = PlayerDataApi.getCustomDataFor(player, STORAGE);
            if (x == null) {
                return null;
            }
            return x.get(this.game);
        }

        public boolean setData(@Nullable ByteBuffer data) {
            var player = ref.getEntity(gameSpace);
            if (player == null) {
                return false;
            }

            var x = PlayerDataApi.getCustomDataFor(player, STORAGE);
            if (x == null) {
                x = new HashMap<>();
                PlayerDataApi.setCustomDataFor(player, STORAGE, x);
            } else if (!(x instanceof HashMap<Identifier, ByteBuffer>)) {
                x = new HashMap<>(x);
                PlayerDataApi.setCustomDataFor(player, STORAGE, x);
            }
            if (data != null) {
                x.put(this.game, data);
            } else {
                x.remove(this.game);
            }
            return true;
        }

        @Override
        public boolean canUse() {
            return ref.isOnline(gameSpace);
        }
    }
}
