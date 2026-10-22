package ic2.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/** Access to vanilla predicates without copying block, container, or collision-shape rules. */
public interface DensityDrivenFluidAccess {
  boolean ic2$canMaybePassThrough(BlockGetter level, BlockPos from, BlockState fromState,
      Direction direction, BlockPos to, BlockState toState, FluidState existing);

  boolean ic2$canHoldSpecificFluid(
      BlockGetter level, BlockPos pos, BlockState state, Fluid incoming);

  boolean ic2$isDensityHole(BlockGetter level, BlockPos from, BlockState fromState,
      BlockPos to, BlockState toState);
}
