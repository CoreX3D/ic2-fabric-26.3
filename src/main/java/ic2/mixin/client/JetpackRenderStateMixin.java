package ic2.mixin.client;

import ic2.core.item.armor.jetpack.LayerJetpackOverride;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extracts attached jetpacks independently of vanilla's humanoid-armor filtering. */
@Mixin(LivingEntityRenderer.class)
public abstract class JetpackRenderStateMixin {
  @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
      at = @At("TAIL"))
  private void ic2$extractJetpack(LivingEntity entity, LivingEntityRenderState state,
      float partialTick, CallbackInfo ci) {
    LayerJetpackOverride.extract(entity, state);
  }
}
