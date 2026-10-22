package ic2.core.recipe.v2;

import ic2.api.recipe.MachineRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.level.Level;

public record RecipeHolder<I, O>(
    MachineRecipe<I, O> recipe,
    Identifier id,
    RecipeSerializer<?> serializer,
    RecipeType<?> type)
    implements Recipe<RecipeInput> {
  public boolean matches(RecipeInput inventory, Level world) {
    return false;
  }

  public ItemStack assemble(RecipeInput inventory) {
    return ItemStack.EMPTY;
  }

  public boolean canCraftInDimensions(int width, int height) {
    return false;
  }

  public ItemStack getResultItem(HolderLookup.Provider registryAccess) {
    return ItemStack.EMPTY;
  }

  public boolean isSpecial() {
    return true;
  }

  public Identifier getId() {
    return this.id;
  }

  @SuppressWarnings("unchecked")
  public RecipeSerializer<? extends Recipe<RecipeInput>> getSerializer() {
    return (RecipeSerializer<? extends Recipe<RecipeInput>>) this.serializer;
  }

  @SuppressWarnings("unchecked")
  public RecipeType<? extends Recipe<RecipeInput>> getType() {
    return (RecipeType<? extends Recipe<RecipeInput>>) this.type;
  }

  public boolean showNotification() { return false; }

  public String group() { return ""; }

  public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }

  public RecipeBookCategory recipeBookCategory() { return RecipeBookCategories.CRAFTING_MISC; }
}
