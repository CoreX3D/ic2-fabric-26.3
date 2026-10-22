package ic2.core.recipe;

import ic2.core.recipe.input.RecipeInputItemStack;
import ic2.core.ref.Ic2Items;
import java.util.Map;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CustomData;

/** Component restrictions that native item-type-only recipe-book accounting cannot represent. */
public final class RecipeBookStackRules {
  private RecipeBookStackRules() {}

  public static boolean canAccountStack(ItemStack stack) {
    if (stack.isEmpty()) return true;
    Item item = stack.getItem();
    // Only these four rods are ingredients of ordinary crafting recipes, and all six recipes
    // require data:{use:0}. Other reactor components and all manual recipe matching are untouched.
    if (item != Ic2Items.URANIUM_FUEL_ROD && item != Ic2Items.DUAL_URANIUM_FUEL_ROD
        && item != Ic2Items.MOX_FUEL_ROD && item != Ic2Items.DUAL_MOX_FUEL_ROD) return true;
    return FreshRodInputs.INPUTS.get(item).matches(stack);
  }

  /** Build templates lazily after the registry and item defaults have finished loading. */
  private static final class FreshRodInputs {
    private static final Map<Item, RecipeInputItemStack> INPUTS = Map.of(
        Ic2Items.URANIUM_FUEL_ROD, fresh(Ic2Items.URANIUM_FUEL_ROD),
        Ic2Items.DUAL_URANIUM_FUEL_ROD, fresh(Ic2Items.DUAL_URANIUM_FUEL_ROD),
        Ic2Items.MOX_FUEL_ROD, fresh(Ic2Items.MOX_FUEL_ROD),
        Ic2Items.DUAL_MOX_FUEL_ROD, fresh(Ic2Items.DUAL_MOX_FUEL_ROD));

    private static RecipeInputItemStack fresh(Item item) {
      CompoundTag data = new CompoundTag();
      // Reactor components write IntTag counters; recipe JSON may decode the same zero as ByteTag.
      // RecipeInputItemStack compares numeric values while preserving the required key contract.
      data.putInt("use", 0);
      return new RecipeInputItemStack(new ItemStackTemplate(item.builtInRegistryHolder(), 1,
          DataComponentPatch.builder().set(DataComponents.CUSTOM_DATA, CustomData.of(data)).build()));
    }
  }
}
