package ic2.compat;

import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import java.util.List;

public interface Ic2CraftingRecipe extends CraftingRecipe {
  default int getIc2RecipeWidth() { return 3; }

  default int getIc2RecipeHeight() { return 3; }

  NonNullList<Ingredient> getIngredients();

  ItemStack getResultItem(HolderLookup.Provider registries);

  boolean canCraftInDimensions(int width, int height);

  default ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
    return assemble(input);
  }

  @Override
  default String group() { return ""; }

  @Override
  default boolean showNotification() { return !isSpecial(); }

  @Override
  default PlacementInfo placementInfo() {
    return isSpecial() ? PlacementInfo.NOT_PLACEABLE : PlacementInfo.create(getIngredients());
  }

  @Override
  default List<RecipeDisplay> display() {
    ItemStack result = getResultItem(null);
    if (isSpecial() || result.isEmpty()) return List.of();
    return List.of(new ShapelessCraftingRecipeDisplay(
        getIngredients().stream().map(Ingredient::display).toList(),
        new SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate.fromNonEmptyStack(result)),
        new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
  }

  default int getHeight() {
    return this.getIc2RecipeHeight();
  }

  default int getWidth() {
    return this.getIc2RecipeWidth();
  }
}
