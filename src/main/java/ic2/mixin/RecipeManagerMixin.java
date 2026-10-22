package ic2.mixin;

import ic2.core.IC2;
import ic2.core.util.LogCategory;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Recipe parsing moved to the reloadable registry loader in Minecraft 26.3. */
@Mixin(RecipeManager.class)
public class RecipeManagerMixin {
  @Inject(method = "<init>", at = @At("RETURN"))
  private void logRecipeCount(HolderLookup.Provider registries, CallbackInfo callback) {
    IC2.log.debug(LogCategory.Recipe, "Loaded %s recipes from the reloadable recipe registry",
        ((RecipeManager) (Object) this).getRecipes().size());
  }
}
