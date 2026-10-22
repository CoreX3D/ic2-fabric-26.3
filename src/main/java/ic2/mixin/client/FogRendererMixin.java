package ic2.mixin.client;

import ic2.core.event.EventHandlerClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies IC2 fluid color and spherical fog distances to the data uploaded by the 26.3 renderer. */
@Mixin(FogRenderer.class)
public class FogRendererMixin {
  @Inject(
      method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/fog/FogData;",
      at = @At("RETURN"))
  private void ic2$setupFluidFog(
      Camera camera,
      int renderDistance,
      DeltaTracker deltaTracker,
      float darkenWorldAmount,
      ClientLevel level,
      CallbackInfoReturnable<FogData> callback) {
    BlockState state = level.getBlockState(camera.blockPosition());
    FogData fog = callback.getReturnValue();
    int color = EventHandlerClient.onRenderFogColor(state);
    if (color != -1) {
      fog.color.set(
          (color >>> 16 & 0xFF) / 255.0F,
          (color >>> 8 & 0xFF) / 255.0F,
          (color & 0xFF) / 255.0F,
          fog.color.w);
    }
    float density = EventHandlerClient.onSetupFogDensity(state);
    if (density >= 0.0F) {
      // Environmental fog uses spherical distance; render-distance fog remains independent.
      fog.environmentalStart = -8.0F;
      fog.environmentalEnd = density * 0.5F;
      fog.skyEnd = fog.environmentalEnd;
      fog.cloudEnd = fog.environmentalEnd;
    }
  }
}
