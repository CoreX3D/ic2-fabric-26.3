package ic2.core.recipe.v2;

import ic2.api.recipe.RecipeOutputWeighted;
import ic2.core.IC2;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/** Weighted machine definitions retain components and quantities without early ItemStacks. */
final class TemplateWeightedOutput extends RecipeOutputWeighted {
  record Entry(ItemStackTemplate template, int weight) {}
  private final List<Entry> entries = new ArrayList<>();

  TemplateWeightedOutput addTemplate(ItemStackTemplate template, int weight) {
    if (weight <= 0) throw new IllegalArgumentException("Recipe output weight must be positive");
    this.entries.add(new Entry(template, weight));
    return this;
  }

  List<Entry> templates() { return List.copyOf(this.entries); }

  @Override
  public RecipeOutputWeighted addOutput(ItemStack output, int weight) {
    return addTemplate(ItemStackTemplate.fromNonEmptyStack(output), weight);
  }

  @Override
  public ItemStack drawOutput() {
    if (this.entries.isEmpty()) return null;
    int totalWeight = this.entries.stream().mapToInt(Entry::weight).reduce(0, Math::addExact);
    int value = IC2.random.nextInt(totalWeight);
    for (Entry entry : this.entries) {
      value -= entry.weight();
      if (value < 0) return RecipeJson.materialize(entry.template());
    }
    throw new IllegalStateException("Invalid weighted IC2 recipe");
  }

  @Override
  public void clear() { this.entries.clear(); }

  @Override
  public List<ItemStack> getOutputs() {
    return new TemplateOutputList(this.entries.stream().map(Entry::template).toList());
  }

  @Override
  public List<Integer> getWeights() { return this.entries.stream().map(Entry::weight).toList(); }

  @Override
  public void forEach(BiConsumer<ItemStack, Integer> consumer) {
    for (Entry entry : this.entries) consumer.accept(RecipeJson.materialize(entry.template()), entry.weight());
  }
}
