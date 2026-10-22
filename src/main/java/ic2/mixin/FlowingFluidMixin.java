package ic2.mixin;

import ic2.fabric.DensityDrivenFluid;
import ic2.fabric.DensityDrivenFluidAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mirrors vanilla's vertical fluid rules for the IC2 fluids with nonpositive density. */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin implements DensityDrivenFluidAccess {
  @Shadow
  private boolean canMaybePassThrough(BlockGetter level, BlockPos from, BlockState fromState,
      Direction direction, BlockPos to, BlockState toState, FluidState existing) {
    throw new AssertionError();
  }

  @Shadow
  private static boolean canHoldSpecificFluid(
      BlockGetter level, BlockPos pos, BlockState state, Fluid incoming) {
    throw new AssertionError();
  }

  @Shadow
  private static boolean canHoldFluid(
      BlockGetter level, BlockPos pos, BlockState state, Fluid incoming) {
    throw new AssertionError();
  }

  @Shadow
  private static boolean canPassThroughWall(Direction direction, BlockGetter level,
      BlockPos from, BlockState fromState, BlockPos to, BlockState toState) {
    throw new AssertionError();
  }

  @Shadow
  private boolean isWaterHole(BlockGetter level, BlockPos from, BlockState fromState,
      BlockPos to, BlockState toState) {
    throw new AssertionError();
  }

  @Override
  @Unique
  public boolean ic2$canMaybePassThrough(BlockGetter level, BlockPos from, BlockState fromState,
      Direction direction, BlockPos to, BlockState toState, FluidState existing) {
    return canMaybePassThrough(level, from, fromState, direction, to, toState, existing);
  }

  @Override
  @Unique
  public boolean ic2$canHoldSpecificFluid(
      BlockGetter level, BlockPos pos, BlockState state, Fluid incoming) {
    return canHoldSpecificFluid(level, pos, state, incoming);
  }

  @Override
  @Unique
  public boolean ic2$isDensityHole(BlockGetter level, BlockPos from, BlockState fromState,
      BlockPos to, BlockState toState) {
    FlowingFluid fluid = (FlowingFluid) (Object) this;
    Direction direction = DensityDrivenFluid.isUpward(fluid) ? Direction.UP : Direction.DOWN;
    return canPassThroughWall(direction, level, from, fromState, to, toState)
        && (toState.getFluidState().getType().isSame(fluid)
            || canHoldFluid(level, to, toState, fluid.getFlowing()));
  }

  @Inject(method = "canHoldSpecificFluid", at = @At("HEAD"), cancellable = true)
  private static void ic2$protectDenserVanillaLiquid(
      BlockGetter level, BlockPos pos, BlockState state, Fluid incoming,
      CallbackInfoReturnable<Boolean> cir) {
    if (incoming instanceof DensityDrivenFluid density
        && !density.canDisplace(state.getFluidState().getType())) {
      cir.setReturnValue(false);
    }
  }

  @Redirect(method = "spread", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/world/level/material/FluidState;canBeReplacedWith(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/core/Direction;)Z"))
  private boolean ic2$vanillaLiquidDisplacement(
      FluidState existing, BlockGetter level, BlockPos pos, Fluid incoming, Direction direction) {
    return DensityDrivenFluid.canReplace(existing, level, pos, incoming, direction);
  }

  @Redirect(method = "spread", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/world/level/material/FlowingFluid;isWaterHole(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
  private boolean ic2$verticalHole(FlowingFluid fluid, BlockGetter level,
      BlockPos from, BlockState fromState, BlockPos to, BlockState toState) {
    return fluid instanceof DensityDrivenFluid
        ? ic2$isDensityHole(level, from, fromState, to, toState)
        : isWaterHole(level, from, fromState, to, toState);
  }

  @Redirect(method = "spread", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/core/BlockPos;below()Lnet/minecraft/core/BlockPos;"))
  private BlockPos ic2$flowTarget(BlockPos pos) {
    return DensityDrivenFluid.isUpward((FlowingFluid) (Object) this) ? pos.above() : pos.below();
  }

  @Redirect(method = {"spread", "getNewLiquid"}, at = @At(value = "FIELD",
      target = "Lnet/minecraft/core/Direction;DOWN:Lnet/minecraft/core/Direction;"))
  private Direction ic2$downstreamDirection() {
    return DensityDrivenFluid.isUpward((FlowingFluid) (Object) this) ? Direction.UP : Direction.DOWN;
  }

  @Redirect(method = {"getNewLiquid", "isSolidFace"}, at = @At(value = "FIELD",
      target = "Lnet/minecraft/core/Direction;UP:Lnet/minecraft/core/Direction;"))
  private Direction ic2$upstreamDirection() {
    return DensityDrivenFluid.isUpward((FlowingFluid) (Object) this) ? Direction.DOWN : Direction.UP;
  }

  @Redirect(method = "hasSameAbove", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/core/BlockPos;above()Lnet/minecraft/core/BlockPos;"))
  private static BlockPos ic2$upstreamHeightNeighbor(
      BlockPos queried, FluidState state, BlockGetter level, BlockPos pos) {
    return DensityDrivenFluid.isUpward(state.getType()) ? queried.below() : queried.above();
  }
}
