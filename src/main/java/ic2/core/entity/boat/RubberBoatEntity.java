package ic2.core.entity.boat;

import ic2.api.entity.boat.AbstractBoatEntity;
import ic2.api.entity.boat.BoatType;
import ic2.core.ref.Ic2BoatTypes;
import ic2.core.ref.Ic2Items;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class RubberBoatEntity extends AbstractBoatEntity {
  public RubberBoatEntity(EntityType<? extends AbstractBoatEntity> entityType, Level world) {
    super(entityType, world, () -> Ic2Items.RUBBER_BOAT);
  }

  @SuppressWarnings("unused")
  public RubberBoatEntity(
      EntityType<? extends AbstractBoatEntity> entityType,
      Level world,
      double x,
      double y,
      double z) {
    /* This method are used by reflection. */
    super(entityType, world, () -> Ic2Items.RUBBER_BOAT, x, y, z);
  }

  @Override
  public BoatType getOverrideBoatType() {
    return Ic2BoatTypes.RUBBER;
  }

  @Override
  protected Item getBrokenBoatItem() {
    return Ic2Items.BROKEN_RUBBER_BOAT;
  }

  public void tick() {
    super.tick();
    if (!this.level().isClientSide()
        && this.level().getFluidState(this.blockPosition()).is(FluidTags.LAVA)) {
      this.hurt(this.damageSources().lava(), Float.MAX_VALUE);
    }
  }
}
