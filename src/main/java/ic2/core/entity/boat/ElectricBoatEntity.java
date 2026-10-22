package ic2.core.entity.boat;

import ic2.api.entity.boat.AbstractBoatEntity;
import ic2.api.entity.boat.BoatType;
import ic2.api.item.ElectricItem;
import ic2.core.IC2;
import ic2.core.ref.Ic2BoatTypes;
import ic2.core.ref.Ic2Items;
import ic2.core.util.StackUtil;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.NotNull;

public class ElectricBoatEntity extends AbstractBoatEntity {
  private boolean hasPower = false;

  public ElectricBoatEntity(EntityType<? extends AbstractBoatEntity> entityType, Level world) {
    super(entityType, world, () -> Ic2Items.ELECTRIC_BOAT);
  }

  @SuppressWarnings("unused")
  public ElectricBoatEntity(
      EntityType<? extends AbstractBoatEntity> entityType,
      Level world,
      double x,
      double y,
      double z) {
    /* This method are used by reflection. */
    super(entityType, world, () -> Ic2Items.ELECTRIC_BOAT, x, y, z);
  }

  @Override
  public BoatType getOverrideBoatType() {
    return Ic2BoatTypes.ELECTRIC;
  }

  public boolean fireImmune() {
    return true;
  }

  public boolean isOnFire() {
    return false;
  }

  @Override
  public boolean canFloatOn(FluidState fluidState) {
    return super.canFloatOn(fluidState) || fluidState.is(FluidTags.LAVA);
  }

  @Override
  public double getAccelerationFactor() {
    return this.hasPower ? 1.5 : 0.25;
  }

  public void tick() {
    this.clearFire();
    for (Entity passenger : this.getPassengers()) passenger.clearFire();
    hasPower = false;
    if (getControllingPassenger() instanceof Player player && IC2.keyboard.isForwardKeyDown(player)) {
      int powerCost = 4;
      for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
        ItemStack stack = player.getItemBySlot(slot);
        if (!StackUtil.isEmpty(stack)
            && ElectricItem.manager.discharge(stack, powerCost, Integer.MAX_VALUE, true, true, true)
                == powerCost) {
          if (!this.level().isClientSide()) {
            ElectricItem.manager.discharge(stack, powerCost, Integer.MAX_VALUE, true, true, false);
          }
          hasPower = true;
          break;
        }
      }
    }

    super.tick();

    for (Entity passenger : this.getPassengers()) passenger.clearFire();
  }
}
