package ic2.core.item;

import ic2.core.util.StackUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Commits selected fields without overwriting charge changed by nested electric-item operations. */
public final class ItemNbt {
  private ItemNbt() {}

  public static void commitFields(ItemStack stack, CompoundTag changed, String... keys) {
    CompoundTag current = StackUtil.getOrCreateNbtData(stack);
    for (String key : keys) {
      Tag value = changed.get(key);
      if (value == null) current.remove(key);
      else current.put(key, value.copy());
    }
    StackUtil.setTag(stack, current);
  }
}
