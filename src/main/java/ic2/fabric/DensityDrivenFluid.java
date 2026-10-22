package ic2.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/** A lightweight marker safe to query while vanilla fluid registries are bootstrapping. */
public interface DensityDrivenFluid {
  boolean flowsUpward();

  /** Whether flow may replace the target fluid under the original IC2 density rules. */
  boolean canDisplace(Fluid existing);

  static boolean isUpward(Fluid fluid) {
    return fluid instanceof DensityDrivenFluid directional && directional.flowsUpward();
  }

  static boolean canReplace(
      FluidState existing, BlockGetter level, BlockPos pos, Fluid incoming, Direction direction) {
    if (incoming instanceof DensityDrivenFluid density
        && (Fluids.WATER.isSame(existing.getType()) || Fluids.LAVA.isSame(existing.getType()))) {
      return density.canDisplace(existing.getType());
    }
    return existing.canBeReplacedWith(level, pos, incoming, direction);
  }
}
