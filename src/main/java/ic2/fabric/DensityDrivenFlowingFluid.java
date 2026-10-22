package ic2.fabric;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Vanilla's slope search with IC2's vertical direction and displacement rules.
 *
 * <p>Keeping this override on the IC2 fluid itself also keeps optimizers of the vanilla
 * {@link FlowingFluid#getSpread} implementation from substituting a downward-only search for gases.
 */
public abstract class DensityDrivenFlowingFluid extends FlowingFluid implements DensityDrivenFluid {
  @Override
  protected Map<Direction, FluidState> getSpread(
      ServerLevel level, BlockPos pos, BlockState state) {
    DensityDrivenFluidAccess access = (DensityDrivenFluidAccess) this;
    SpreadContext context = new SpreadContext(level, access);
    Map<Direction, FluidState> spread = new EnumMap<>(Direction.class);
    int shortest = 1000;
    for (Direction direction : Direction.Plane.HORIZONTAL) {
      BlockPos target = pos.relative(direction);
      BlockState targetState = context.blockState(target);
      FluidState existing = targetState.getFluidState();
      if (!access.ic2$canMaybePassThrough(
          level, pos, state, direction, target, targetState, existing)) {
        continue;
      }
      FluidState incoming = getNewLiquid(level, target, targetState);
      if (!access.ic2$canHoldSpecificFluid(level, target, targetState, incoming.getType())) {
        continue;
      }
      int distance = context.isHole(target) ? 0
          : context.slopeDistance(target, 1, direction.getOpposite(), targetState);
      if (distance < shortest) {
        spread.clear();
      }
      if (distance <= shortest) {
        if (DensityDrivenFluid.canReplace(existing, level, target, incoming.getType(), direction)) {
          spread.put(direction, incoming);
        }
        shortest = distance;
      }
    }
    return spread;
  }

  private final class SpreadContext {
    private final ServerLevel level;
    private final DensityDrivenFluidAccess access;
    private final Map<BlockPos, BlockState> states = new HashMap<>();
    private final Map<BlockPos, Boolean> holes = new HashMap<>();

    private SpreadContext(ServerLevel level, DensityDrivenFluidAccess access) {
      this.level = level;
      this.access = access;
    }

    private BlockState blockState(BlockPos pos) {
      return states.computeIfAbsent(pos.immutable(), level::getBlockState);
    }

    private boolean isHole(BlockPos pos) {
      return holes.computeIfAbsent(pos.immutable(), queried -> {
        BlockPos target = flowsUpward() ? queried.above() : queried.below();
        return access.ic2$isDensityHole(
            level, queried, blockState(queried), target, blockState(target));
      });
    }

    private int slopeDistance(BlockPos pos, int distance, Direction excluded, BlockState state) {
      int shortest = 1000;
      for (Direction direction : Direction.Plane.HORIZONTAL) {
        if (direction == excluded) {
          continue;
        }
        BlockPos target = pos.relative(direction);
        BlockState targetState = blockState(target);
        if (!access.ic2$canMaybePassThrough(level, pos, state, direction,
            target, targetState, targetState.getFluidState())
            || !access.ic2$canHoldSpecificFluid(level, target, targetState, getFlowing())) {
          continue;
        }
        if (isHole(target)) {
          return distance;
        }
        if (distance < getSlopeFindDistance(level)) {
          shortest = Math.min(shortest,
              slopeDistance(target, distance + 1, direction.getOpposite(), targetState));
        }
      }
      return shortest;
    }
  }
}
