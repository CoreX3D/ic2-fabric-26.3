package ic2.core.uu;

import ic2.core.IC2;
import ic2.core.util.StackUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

public class RecipeResolver implements IRecipeResolver {
  private static final double transformCost = 1.0;

  private static List<List<LeanItemStack>> toDoubleStackList(List<Ingredient> list) {
    List<List<LeanItemStack>> ret = new ArrayList<>(list.size());

    for (Ingredient ingredient : list) {
      ItemStack[] arr = ingredient.items().map(holder -> new ItemStack(holder)).toArray(ItemStack[]::new);
      if (arr.length == 0) {
        continue;
      }

      List<LeanItemStack> toAdd = new ArrayList<>(arr.length);

      for (ItemStack stack : arr) {
        toAdd.add(new LeanItemStack(stack));
      }

      ret.add(toAdd);
    }

    return ret;
  }

  @Override
  public List<RecipeTransformation> getTransformations() {
    List<RecipeTransformation> ret = new ArrayList<>();
    if (IC2.envProxy.getServer() == null) {
      return ret;
    }

    var displayContext = SlotDisplayContext.fromLevel(IC2.envProxy.getServer().overworld());

    for (var wrapper : IC2.sideProxy.getRecipeManager().getRecipes()) {
      Recipe<?> irecipe = wrapper.value();
      var placement = irecipe.placementInfo();
      List<Ingredient> inputs = placement.slotsToIngredientIndex().intStream()
          .filter(index -> index >= 0).mapToObj(placement.ingredients()::get).toList();
      if (inputs.isEmpty()) continue;
      var outputs = new java.util.LinkedHashSet<LeanItemStack>();
      for (var display : irecipe.display()) {
        for (ItemStack output : display.result().resolveForStacks(displayContext)) {
          if (!StackUtil.isEmpty(output)) outputs.add(new LeanItemStack(output));
        }
      }
      for (LeanItemStack output : outputs) {
        ret.add(new RecipeTransformation(transformCost, toDoubleStackList(inputs), output));
      }
    }

    return ret;
  }
}
