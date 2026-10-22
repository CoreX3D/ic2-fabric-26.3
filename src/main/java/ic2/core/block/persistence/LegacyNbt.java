package ic2.core.block.persistence;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

/** Preserves IC2's existing flat NBT format at Minecraft's ValueInput/ValueOutput boundary. */
public final class LegacyNbt {
  private LegacyNbt() {}

  public static final MapCodec<CompoundTag> COMPOUND = new MapCodec<>() {
    @Override
    public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops, MapLike<T> input) {
      return CompoundTag.CODEC.parse(ops, ops.createMap(input.entries()));
    }

    @Override
    public <T> RecordBuilder<T> encode(CompoundTag input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
      input.forEach((key, value) -> prefix.add(key, NbtOps.INSTANCE.convertTo(ops, value)));
      return prefix;
    }

    @Override
    public <T> Stream<T> keys(DynamicOps<T> ops) {
      return Stream.empty();
    }
  };

  public static ItemStack readStack(HolderLookup.Provider registries, CompoundTag nbt) {
    return ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), nbt)
        .result().orElse(ItemStack.EMPTY);
  }

  public static CompoundTag writeStack(HolderLookup.Provider registries, ItemStack stack) {
    return writeStack(registries, stack, new CompoundTag());
  }

  public static CompoundTag writeStack(
      HolderLookup.Provider registries, ItemStack stack, CompoundTag prefix) {
    if (stack.isEmpty()) {
      return prefix;
    }
    Tag encoded = ItemStack.CODEC.encodeStart(
        registries.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
    return prefix.merge((CompoundTag) encoded);
  }

  /** Keep the writer's exact registry context, including for detached block entities. */
  public static HolderLookup.Provider withContext(
      HolderLookup.Provider fallback, DynamicOps<?> outputOps) {
    if (!(outputOps instanceof RegistryOps<?> contextualOps)) {
      return fallback;
    }
    return new HolderLookup.Provider() {
      @Override
      public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
        return fallback.listRegistryKeys();
      }

      @Override
      public <T> Optional<? extends HolderLookup.RegistryLookup<T>> lookup(
          ResourceKey<? extends Registry<? extends T>> key) {
        return fallback.lookup(key);
      }

      @Override
      public <T> RegistryOps<T> createSerializationContext(DynamicOps<T> ops) {
        return contextualOps.withParent(ops);
      }
    };
  }
}
