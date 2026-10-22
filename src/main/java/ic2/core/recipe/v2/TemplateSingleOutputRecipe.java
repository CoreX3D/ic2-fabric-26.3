package ic2.core.recipe.v2;

import ic2.api.recipe.MachineRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/** Single-stack machine recipe whose item defaults are resolved only when used. */
final class TemplateSingleOutputRecipe<I> extends MachineRecipe<I, ItemStack> {
  private final ItemStackTemplate template;

  TemplateSingleOutputRecipe(I input, ItemStackTemplate template) {
    super(input, null);
    this.template = template;
  }

  ItemStackTemplate template() { return this.template; }

  @Override
  public ItemStack getOutput() { return RecipeJson.materialize(this.template); }
}
