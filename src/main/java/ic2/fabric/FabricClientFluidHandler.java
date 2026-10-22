package ic2.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRendering;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRenderHandler;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Client-side fluid handler.
 *
 * <p>NeoForge resolved sprites and tints through {@code IClientFluidTypeExtensions}; on Fabric the
 * sprite ids and tint are the ones IC2 declared in {@code EnvFluidHandler#createFluid} (see {@link
 * FabricFluidHandler}), because vanilla 1.21.1 has no fluid type to hang them off. Anything IC2 did
 * not declare falls back to the missing-texture sprite and an opaque tint, matching NeoForge's
 * default extensions.
 */
@Environment(EnvType.CLIENT)
public final class FabricClientFluidHandler extends FabricFluidHandler {
  private static final FluidRendering.DefaultRenderer VANILLA_GAS_GEOMETRY = new FluidRendering.DefaultRenderer() {};
  private static final FluidRenderHandler GAS_RENDER_HANDLER = new FluidRenderHandler() {
    @Override public void renderFluid(net.minecraft.client.renderer.block.FluidRenderer renderer,
        BlockPos pos, BlockAndTintGetter level,
        net.minecraft.client.renderer.block.FluidRenderer.Output output,
        net.minecraft.world.level.block.state.BlockState blockState,
        net.minecraft.world.level.material.FluidState fluidState) {
      // Sodium supplies a different default renderer which bypasses vanilla's
      // tessellator. Only upward IC2 gases need our mirrored native geometry.
      // This public Fabric default method enters its recursion guard and emits
      // through the caller's Output, including Sodium's chunk mesh consumer.
      // The renderer is supplied lazily at render time; never create it at init.
      VANILLA_GAS_GEOMETRY.render(renderer, this, level, pos, output, blockState, fluidState);
    }
  };

  public static void registerRendering() {
    for (Fluid fluid : BuiltInRegistries.FLUID) {
      Identifier still = stillSprite(fluid);
      if (still == null || !(fluid instanceof FlowingFluid flowing) || fluid != flowing.getSource()) continue;
      int tint = color(fluid);
      FluidModel.Unbaked model = new FluidModel.Unbaked(
          new Material(still, true), new Material(flowingSprite(fluid), true), null,
          state -> tint);
      if (DensityDrivenFluid.isUpward(fluid)) {
        FluidRenderingRegistry.register(fluid, flowing.getFlowing(), model, GAS_RENDER_HANDLER);
      } else {
        FluidRenderingRegistry.register(fluid, flowing.getFlowing(), model);
      }
      FluidVariantRendering.register(fluid, new FluidVariantRenderHandler() {
        @Override public int getColor(FluidVariant variant, BlockAndTintGetter level, BlockPos pos) {
          return tint;
        }
      });
    }
  }

  @Override
  public Identifier getStillSpriteId(Fluid fluid) {
    Identifier sprite = FabricFluidHandler.stillSprite(fluid);
    return sprite != null ? sprite : MissingTextureAtlasSprite.getLocation();
  }

  @Override
  public Identifier getFlowingSpriteId(Fluid fluid) {
    Identifier sprite = FabricFluidHandler.flowingSprite(fluid);
    return sprite != null ? sprite : MissingTextureAtlasSprite.getLocation();
  }

  @Override
  public int getColor(Fluid fluid) {
    return FabricFluidHandler.color(fluid);
  }
}
