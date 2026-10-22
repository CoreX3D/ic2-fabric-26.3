package ic2.core.util;

import ic2.api.info.IInfoProvider;
import ic2.core.IC2;
import ic2.core.fluid.Ic2FluidStack;
import ic2.core.init.IC2Config;
import ic2.core.ref.Ic2Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

public class ItemInfo implements IInfoProvider {
  @Override
  public double getEnergyValue(ItemStack stack) {
    if (StackUtil.isEmpty(stack)) {
      return 0.0;
    } else if (StackUtil.checkItemEquality(stack, Items.REDSTONE)) {
      return 800.0;
    } else if (StackUtil.checkItemEquality(stack, Ic2Items.SINGLE_USE_BATTERY)) {
      return 1200.0;
    } else {
      return StackUtil.checkItemEquality(stack, Ic2Items.ENERGIUM_DUST) ? 16000.0 : 0.0;
    }
  }

  @Override
  public int getFuelValue(ItemStack stack, boolean allowLava) {
    if (!isAllowedFuel(stack, allowLava)) return 0;
    int ret = IC2.envProxy.getBurnTime(stack);
    return isLava(stack) ? ret / 10 : ret;
  }

  @Override
  public boolean isFuel(ItemStack stack, boolean allowLava) {
    if (!isAllowedFuel(stack, allowLava)) return false;
    CookingFuel fuel = stack.get(DataComponents.COOKING_FUEL);
    // Remote clients receive fuel components but do not own a MinecraftServer. Match
    // vanilla's component-based slot prediction; only the server consumes actual fuel.
    if (IC2.envProxy.getServer() == null && fuel != null
        && fuel.burnTime() instanceof ResolvableInt.Reference) return true;
    return getFuelValue(stack, allowLava) > 0;
  }

  private static boolean isAllowedFuel(ItemStack stack, boolean allowLava) {
    if (StackUtil.isEmpty(stack)) {
      return false;
    }

    if ((StackUtil.checkItemEquality(stack, Ic2Items.SCRAP)
            || StackUtil.checkItemEquality(stack, Ic2Items.SCRAP_BOX))
        && !IC2Config.misc.allowBurningScrap.get()) {
      return false;
    }

    return allowLava || !isLava(stack);
  }

  private static boolean isLava(ItemStack stack) {
    Ic2FluidStack liquid = Ic2FluidStack.get(stack);
    return liquid != null && !liquid.isEmpty() && liquid.getFluid() == Fluids.LAVA;
  }
}
