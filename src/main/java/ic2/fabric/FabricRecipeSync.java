package ic2.fabric;

import io.netty.buffer.Unpooled;
import java.util.Arrays;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Supplies complete recipes needed by IC2's client machine and crafting interfaces. */
public final class FabricRecipeSync {
  public static final int CHUNK_SIZE = 512 * 1024;
  public static final int MAX_SYNC_SIZE = 64 * 1024 * 1024;

  private FabricRecipeSync() {}

  public record Payload(boolean first, boolean last, byte[] data) implements CustomPacketPayload {
    public static final Type<Payload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("ic2", "recipes"));
    public static final StreamCodec<FriendlyByteBuf, Payload> CODEC = StreamCodec.of(
        (buffer, payload) -> {
          buffer.writeBoolean(payload.first());
          buffer.writeBoolean(payload.last());
          buffer.writeByteArray(payload.data());
        },
        buffer -> new Payload(buffer.readBoolean(), buffer.readBoolean(),
            buffer.readByteArray(CHUNK_SIZE)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
  }

  public static void register() {
    PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
    ServerPlayConnectionEvents.JOIN.register(
        (handler, sender, server) -> send(handler.player, encode(server)));
    ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
      if (success) {
        byte[] recipes = encode(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player, recipes);
      }
    });
  }

  private static byte[] encode(MinecraftServer server) {
    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
    try {
      var recipes = server.getRecipeManager().getRecipes();
      buffer.writeVarInt(recipes.size());
      for (var recipe : recipes) RecipeHolder.STREAM_CODEC.encode(buffer, recipe);
      if (buffer.readableBytes() > MAX_SYNC_SIZE) {
        throw new IllegalStateException("IC2 recipe synchronization exceeds 64 MiB");
      }
      byte[] bytes = new byte[buffer.readableBytes()];
      buffer.readBytes(bytes);
      return bytes;
    } finally {
      buffer.release();
    }
  }

  private static void send(ServerPlayer player, byte[] recipes) {
    for (int offset = 0; offset < recipes.length; offset += CHUNK_SIZE) {
      int end = Math.min(offset + CHUNK_SIZE, recipes.length);
      ServerPlayNetworking.send(player, new Payload(offset == 0, end == recipes.length,
          Arrays.copyOfRange(recipes, offset, end)));
    }
  }
}
