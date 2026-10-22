package ic2.api.info;

import net.minecraft.world.item.ItemStack;

public interface IInfoProvider {
  double getEnergyValue(ItemStack var1);

  int getFuelValue(ItemStack var1, boolean var2);

  /** Fuel-slot prediction may run before server-backed burn times can be evaluated. */
  default boolean isFuel(ItemStack stack, boolean allowLava) {
    return getFuelValue(stack, allowLava) > 0;
  }
}
