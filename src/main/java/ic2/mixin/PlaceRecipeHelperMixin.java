package ic2.mixin;

import ic2.core.recipe.AdvRecipe;
import net.minecraft.recipebook.PlaceRecipeHelper;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla recognizes only its own ShapedRecipe when selecting placement dimensions. */
@Mixin(PlaceRecipeHelper.class)
public interface PlaceRecipeHelperMixin {
  @Inject(method = "placeRecipe(IILnet/minecraft/world/item/crafting/Recipe;Ljava/lang/Iterable;"
      + "Lnet/minecraft/recipebook/PlaceRecipeHelper$Output;)V", at = @At("HEAD"), cancellable = true)
  private static <T> void ic2$placeShapedRecipe(int gridWidth, int gridHeight, Recipe<?> recipe,
      Iterable<T> ingredients, PlaceRecipeHelper.Output<T> output, CallbackInfo callback) {
    if (recipe instanceof AdvRecipe shaped) {
      PlaceRecipeHelper.placeRecipe(gridWidth, gridHeight, shaped.inputWidth, shaped.inputHeight,
          ingredients, output);
      callback.cancel();
    }
  }
}
