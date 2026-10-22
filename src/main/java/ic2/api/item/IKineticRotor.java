package ic2.api.item;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public interface IKineticRotor {
  int getDiameter(ItemStack var1);

  Identifier getRotorRenderTexture(ItemStack var1);

  float getEfficiency(ItemStack var1);

  int getMinWindStrength(ItemStack var1);

  int getMaxWindStrength(ItemStack var1);

  boolean isAcceptedType(ItemStack var1, IKineticRotor.GearboxType var2);

  enum GearboxType {
    WATER,
    WIND
  }
}
