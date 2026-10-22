package ic2.core.recipe.v2;

import java.util.AbstractList;
import java.util.Collection;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/** Machine output definitions can be decoded and re-encoded before item defaults are bound. */
final class TemplateOutputList extends AbstractList<ItemStack> {
  private final List<ItemStackTemplate> templates;

  TemplateOutputList(Collection<ItemStackTemplate> templates) {
    this.templates = List.copyOf(templates);
  }

  List<ItemStackTemplate> templates() { return this.templates; }

  @Override
  public ItemStack get(int index) { return RecipeJson.materialize(this.templates.get(index)); }

  @Override
  public int size() { return this.templates.size(); }
}
