package ic2.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ic2.fabric.DensityDrivenFluid;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mirrors native fluid tessellation for IC2's nonpositive-density fluids only. */
@Mixin(FluidRenderer.class)
public abstract class FluidRendererMixin {
  // Chunk meshes are compiled concurrently. A dynamically scoped value also unwinds
  // if another renderer fails, unlike a mutable flag on the shared FluidRenderer.
  @Unique private static final ScopedValue<Integer> ic2$gas = ScopedValue.newInstance();

  @Unique private static boolean ic2$isUpward() {
    return ic2$gas.isBound() && ic2$gas.get() >= 0;
  }

  @WrapMethod(method = "tesselate")
  private void ic2$renderGas(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output,
      BlockState blockState, FluidState fluidState, Operation<Void> original) {
    boolean upward = DensityDrivenFluid.isUpward(fluidState.getType());
    if (!upward && !ic2$gas.isBound()) {
      original.call(level, pos, output, blockState, fluidState);
      return;
    }
    ScopedValue.where(ic2$gas, upward ? pos.getY() & 15 : -1)
        .run(() -> original.call(level, pos, output, blockState, fluidState));
  }

  @ModifyExpressionValue(method = "tesselate", at = {
      @At(value = "FIELD", target = "Lnet/minecraft/core/Direction;UP:Lnet/minecraft/core/Direction;"),
      @At(value = "FIELD", target = "Lnet/minecraft/core/Direction;DOWN:Lnet/minecraft/core/Direction;")})
  private Direction ic2$verticalFace(Direction direction) {
    return ic2$isUpward() ? direction.getOpposite() : direction;
  }

  // A bare getHeight selector chooses its first (three-argument) overload, which
  // delegates without reading above(). Select the actual height sampler explicitly.
  @Redirect(method = {"tesselate",
      "getHeight(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)F",
      "getLightCoords"}, at = @At(value = "INVOKE",
      target = "Lnet/minecraft/core/BlockPos;above()Lnet/minecraft/core/BlockPos;"), require = 3, allow = 3)
  private BlockPos ic2$surfaceNeighbor(BlockPos pos) {
    return ic2$isUpward() ? pos.below() : pos.above();
  }

  @Redirect(method = "tesselate", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/core/BlockPos;below()Lnet/minecraft/core/BlockPos;"))
  private BlockPos ic2$ceilingNeighbor(BlockPos pos) {
    return ic2$isUpward() ? pos.above() : pos.below();
  }

  @Inject(method = "isFaceOccludedByState", at = @At("HEAD"), cancellable = true)
  private static void ic2$gasOcclusion(Direction direction, float height, BlockState state,
      CallbackInfoReturnable<Boolean> result) {
    if (!ic2$isUpward()) return;
    VoxelShape occluder = state.getFaceOcclusionShape(direction.getOpposite());
    if (occluder == Shapes.empty()) {
      result.setReturnValue(false);
    } else if (occluder == Shapes.block()) {
      result.setReturnValue(direction != Direction.DOWN || height == 1.0f);
    } else {
      // Query the real neighboring face and compare the actual upper gas volume.
      // Merely flipping emitted vertices would wrongly cull against bottom slabs.
      VoxelShape gas = Shapes.box(0, 1.0 - height, 0, 1, 1, 1);
      result.setReturnValue(Shapes.blockOccludes(gas, occluder, direction));
    }
  }

  @Redirect(method = "tesselate", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/world/level/CardinalLighting;up()F", ordinal = 0))
  private float ic2$surfaceShade(CardinalLighting lighting) {
    return ic2$isUpward() ? lighting.down() : lighting.up();
  }

  @Redirect(method = "tesselate", at = @At(value = "INVOKE",
      target = "Lnet/minecraft/world/level/CardinalLighting;down()F"))
  private float ic2$ceilingShade(CardinalLighting lighting) {
    return ic2$isUpward() ? lighting.up() : lighting.down();
  }

  @WrapMethod(method = "addFace")
  private void ic2$mirrorFace(VertexConsumer builder,
      float x0, float y0, float z0, float u0, float v0,
      float x1, float y1, float z1, float u1, float v1,
      float x2, float y2, float z2, float u2, float v2,
      float x3, float y3, float z3, float u3, float v3,
      int color, int lightCoords, boolean addBackFace, Operation<Void> original) {
    if (ic2$isUpward()) {
      float mirror = 2 * ic2$gas.get() + 1;
      // Reflect local section coordinates, reversing winding but keeping each
      // UV attached to its vertex. Side V then matches Forge ModelFluid's gas UV.
      original.call(builder,
          x0, mirror - y0, z0, u0, v0,
          x3, mirror - y3, z3, u3, v3,
          x2, mirror - y2, z2, u2, v2,
          x1, mirror - y1, z1, u1, v1,
          color, lightCoords, addBackFace);
    } else {
      original.call(builder, x0, y0, z0, u0, v0, x1, y1, z1, u1, v1,
          x2, y2, z2, u2, v2, x3, y3, z3, u3, v3, color, lightCoords, addBackFace);
    }
  }

  @ModifyArg(method = "vertex", at = @At(value = "INVOKE",
      target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;addVertex(FFFIFFIIFFF)V"), index = 9)
  private float ic2$mirrorNormal(float normalY) {
    return ic2$isUpward() ? -normalY : normalY;
  }

}
