package ic2.core.uu;

import ic2.core.IC2;
import ic2.core.util.LogCategory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.ItemStack;
import ic2.core.recipe.v2.RecipeManagerGetter;

public class VanillaSmeltingResolver implements IRecipeResolver {
  private static final double transformCost = 14.0;

  @Override
  public List<RecipeTransformation> getTransformations() {
    List<RecipeTransformation> ret = new ArrayList<>();
    if (IC2.envProxy.getServer() == null) {
      return ret;
    }

    for (var wrapper : RecipeManagerGetter.getRecipes(IC2.sideProxy.getRecipeManager(), RecipeType.SMELTING)) {
      try {
        SmeltingRecipe recipe = wrapper.value();
        List<List<LeanItemStack>> inputs = RecipeUtil.convertIngredients(List.of(recipe.input()));
        ItemStack sample = recipe.input().items().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (sample.isEmpty()) continue;
        LeanItemStack output = new LeanItemStack(recipe.assemble(new SingleRecipeInput(sample)));
        ret.add(new RecipeTransformation(14.0, inputs, output));
      } catch (IllegalArgumentException e) {
        IC2.log.warn(LogCategory.Uu, e, "invalid recipe");
      }
    }

    return ret;
  }
}
