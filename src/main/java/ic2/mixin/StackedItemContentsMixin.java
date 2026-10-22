package ic2.mixin;

import ic2.core.recipe.RecipeBookStackRules;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep invalid component-constrained rods out of both inventory and crafting-grid book counts. */
@Mixin(StackedItemContents.class)
public abstract class StackedItemContentsMixin {
  @Inject(method = "accountStack(Lnet/minecraft/world/item/ItemStack;I)V",
      at = @At("HEAD"), cancellable = true)
  private void ic2$skipIneligibleFuelRods(ItemStack stack, int maxCount, CallbackInfo callback) {
    if (!RecipeBookStackRules.canAccountStack(stack)) callback.cancel();
  }
}
