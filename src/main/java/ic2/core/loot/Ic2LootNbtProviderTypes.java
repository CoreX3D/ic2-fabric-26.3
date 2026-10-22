package ic2.core.loot;

import ic2.core.IC2;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.storage.loot.providers.nbt.NbtProvider;

public class Ic2LootNbtProviderTypes {
  public static final MapCodec<? extends NbtProvider> BLOCK_NBT = register(Ic2BlockNbtProvider.CODEC);

  public static void init() {}

  private static MapCodec<? extends NbtProvider> register(
      com.mojang.serialization.MapCodec<? extends NbtProvider> codec) {
    return Registry.register(
        BuiltInRegistries.LOOT_NBT_PROVIDER_TYPE,
        IC2.getIdentifier("block_nbt"),
        codec);
  }
}
