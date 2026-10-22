import ic2.core.block.persistence.LegacyNbt;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** Standalone validation against the actual 26.3 runtime, with no game world or mod stubs. */
public final class LegacyNbtRoundTrip {
  public static void main(String[] args) {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    CompoundTag saved = new CompoundTag();
    saved.putBoolean("active", true);
    saved.putShort("progress", (short) 153);
    CompoundTag components = new CompoundTag();
    CompoundTag energy = new CompoundTag();
    energy.putDouble("storage", 47123.25);
    components.put("energy", energy);
    saved.put("ic2_components", components);
    CompoundTag slot = new CompoundTag();
    ListTag contents = new ListTag();
    CompoundTag prefix = new CompoundTag();
    prefix.putByte("Index", (byte) 2);
    prefix.putString("id", "minecraft:copper_ingot");
    prefix.putInt("count", 17);
    contents.add(prefix);
    slot.put("Contents", contents);
    CompoundTag slots = new CompoundTag();
    slots.put("input", slot);
    saved.put("InvSlots", slots);

    var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
    output.putString("id", "ic2:macerator");
    output.store(LegacyNbt.COMPOUND, saved);
    CompoundTag stored = output.buildResult();
    check("ic2:macerator".equals(stored.getStringOr("id", "")), "metadata overwritten");
    check(stored.getCompound("InvSlots").isPresent(), "inventory was nested or lost");
    var input = TagValueInput.create(ProblemReporter.DISCARDING, registries, stored);
    CompoundTag restored = input.read(LegacyNbt.COMPOUND).orElseThrow();
    restored.remove("id");
    check(saved.equals(restored), "flat NBT map changed during ValueInput/ValueOutput round trip");

    var contextual = LegacyNbt.withContext(RegistryAccess.EMPTY,
        registries.createSerializationContext(NbtOps.INSTANCE));
    CompoundTag restoredStack = restored.getCompoundOrEmpty("InvSlots")
        .getCompoundOrEmpty("input").getListOrEmpty("Contents").getCompoundOrEmpty(0);
    check(restoredStack.getIntOr("count", 0) == 17, "stack count lost");
    check(restoredStack.getByteOr("Index", (byte) -1) == 2, "inventory index lost");
    var itemHolder = contextual.createSerializationContext(NbtOps.INSTANCE)
        .getter(net.minecraft.core.registries.Registries.ITEM).orElseThrow()
        .getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,
            net.minecraft.resources.Identifier.parse("minecraft:copper_ingot")));
    check(itemHolder.value() == Items.COPPER_INGOT, "detached registry context lost");
    System.out.println("PASS: flat NBT, component energy, inventory payload and index, detached registry context");
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
