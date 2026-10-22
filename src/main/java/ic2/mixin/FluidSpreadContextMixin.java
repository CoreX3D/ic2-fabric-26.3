package ic2.mixin;

import ic2.fabric.DensityDrivenFluid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Makes horizontal slope search look for an escape above a gas rather than below it. */
@Mixin(targets = "net.minecraft.world.level.material.FlowingFluid$SpreadContext")
public abstract class FluidSpreadContextMixin {
  @Shadow @Final FlowingFluid this$0;

  @Redirect(method = "lambda$isHole$0", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/core/BlockPos;below()Lnet/minecraft/core/BlockPos;"))
  private BlockPos ic2$gasEscape(BlockPos pos) {
    return DensityDrivenFluid.isUpward(this.this$0) ? pos.above() : pos.below();
  }
}
