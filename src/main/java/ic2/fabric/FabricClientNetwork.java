package ic2.fabric;

import ic2.core.IC2;
import ic2.core.network.Ic2Payload;
import io.netty.buffer.Unpooled;
import ic2.core.recipe.v2.RecipeManagerGetter;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.crafting.RecipeHolder;

@Environment(EnvType.CLIENT)
public final class FabricClientNetwork {
  private static ByteArrayOutputStream pendingRecipes;
  private FabricClientNetwork() {}

  public static void register() {
    ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
      pendingRecipes = null;
      RecipeManagerGetter.clearClientRecipes();
    });
    ClientPlayNetworking.registerGlobalReceiver(FabricRecipeSync.Payload.TYPE, (payload, context) -> {
      if (payload.first()) pendingRecipes = new ByteArrayOutputStream();
      if (pendingRecipes == null) throw new IllegalStateException("Missing IC2 recipe sync start");
      if ((long) pendingRecipes.size() + payload.data().length > FabricRecipeSync.MAX_SYNC_SIZE) {
        pendingRecipes = null;
        throw new IllegalStateException("IC2 recipe synchronization exceeds 64 MiB");
      }
      pendingRecipes.writeBytes(payload.data());
      if (payload.last()) {
        byte[] data = pendingRecipes.toByteArray();
        pendingRecipes = null;
        var registries = context.player().level().registryAccess();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), registries);
        try {
          int count = buffer.readVarInt();
          if (count < 0 || count > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid IC2 recipe count: " + count);
          }
          var recipes = new ArrayList<RecipeHolder<?>>(count);
          for (int i = 0; i < count; i++) recipes.add(RecipeHolder.STREAM_CODEC.decode(buffer));
          if (buffer.isReadable()) throw new IllegalArgumentException("Trailing IC2 recipe sync data");
          RecipeManagerGetter.setClientRecipes(recipes, registries);
        } finally {
          buffer.release();
        }
      }
    });
    ClientPlayNetworking.registerGlobalReceiver(Ic2Payload.TYPE, (payload, context) -> {
      var buffer = Unpooled.wrappedBuffer(payload.data());
      try {
        IC2.network.get(false).onPacket(buffer, context.player());
      } finally {
        buffer.release();
      }
    });
  }
}
