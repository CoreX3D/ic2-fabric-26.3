package ic2.core.item;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Shared logic dispatched by vanilla server ticks and the Fabric client tick callback. */
public interface Ic2InventoryTickItem {
  void tickInventory(ItemStack stack, Level level, Entity entity, int slot, boolean selected);

  static int findInventorySlot(Entity entity, ItemStack stack) {
    if (entity instanceof Player player) {
      for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
        if (player.getInventory().getItem(i) == stack) return i;
      }
    }
    return -1;
  }
}
