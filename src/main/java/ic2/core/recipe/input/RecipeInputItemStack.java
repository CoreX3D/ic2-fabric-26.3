package ic2.core.recipe.input;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import ic2.core.util.StackUtil;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.core.component.DataComponents;
import ic2.core.recipe.v2.RecipeJson;

public class RecipeInputItemStack extends RecipeInputBase {
  public final ItemStackTemplate input;

  public RecipeInputItemStack(ItemStack input) {
    if (input == null || input.isEmpty()) {
      throw new IllegalArgumentException("invalid input stack");
    }

    this.input = ItemStackTemplate.fromNonEmptyStack(input);
  }

  public RecipeInputItemStack(ItemStackTemplate input) {
    if (input.count() <= 0) throw new IllegalArgumentException("invalid input count");
    this.input = input;
  }

  @Override
  public boolean matches(ItemStack subject) {
    if (subject.isEmpty() || this.input.item().value() != subject.getItem()) return false;

    // Compare explicit native components without requiring the recipe's default component map.
    var patch = this.input.components().split();
    for (var component : patch.added()) {
      if (component.type() != DataComponents.CUSTOM_DATA
          && !java.util.Objects.equals(component.value(), subject.get(component.type()))) return false;
    }
    for (var removed : patch.removed()) {
      if (subject.has(removed)) return false;
    }

    var requiredData = patch.added().get(DataComponents.CUSTOM_DATA);
    CompoundTag requiredNbt = requiredData == null ? null : requiredData.copyTag();
    if (requiredNbt == null || requiredNbt.isEmpty()) return true;

    var subjectData = subject.get(DataComponents.CUSTOM_DATA);
    CompoundTag subjectNbt = subjectData == null ? null : subjectData.copyTag();
    if (subjectNbt == null) {
      // Subject has no NBT — check that all required keys map to default values (0 for numbers,
      // etc.)
      for (String key : requiredNbt.keySet()) {
        if (!isDefaultTag(requiredNbt.get(key))) return false;
      }

      return true;
    }

    // Check required ⊆ subject (partial match)
    for (String key : requiredNbt.keySet()) {
      if (!subjectNbt.contains(key)) return false;
      if (!matchesRequiredTag(requiredNbt.get(key), subjectNbt.get(key))) return false;
    }

    return true;
  }

  private static boolean matchesRequiredTag(Tag required, Tag actual) {
    if (required instanceof NumericTag expectedNumber && actual instanceof NumericTag actualNumber) {
      // JSON codecs use the smallest NBT number type, while item counters normally use IntTag.
      // Match numeric values without losing precision for long counters or accepting strings.
      try {
        return new BigDecimal(expectedNumber.box().toString())
            .compareTo(new BigDecimal(actualNumber.box().toString())) == 0;
      } catch (NumberFormatException nonFiniteNumber) {
        return required.equals(actual);
      }
    }
    return required.equals(actual);
  }

  private static boolean isDefaultTag(Tag tag) {
    if (tag instanceof NumericTag num) {
      return num.doubleValue() == 0.0;
    }

    if (tag instanceof StringTag str) {
      return str.value().isEmpty();
    }

    if (tag instanceof ByteArrayTag byteArray) {
      return byteArray.isEmpty();
    }

    if (tag instanceof IntArrayTag intArray) {
      return intArray.isEmpty();
    }

    if (tag instanceof LongArrayTag longArray) {
      return longArray.isEmpty();
    }

    if (tag instanceof ListTag list) {
      return list.isEmpty();
    }

    if (tag instanceof CompoundTag compound) {
      return compound.isEmpty();
    }

    return false;
  }

  @Override
  public int getAmount() {
    return this.input.count();
  }

  @Override
  protected List<ItemStack> listStacks() {
    return List.of(RecipeJson.materialize(this.input));
  }

  @Override
  public Ingredient getIngredient() {
    return Ingredient.of(this.input.item().value());
  }

  @Override
  public String toString() {
    return "RInputItemStack<" + this.input + ">";
  }

  @Override
  public boolean equals(Object obj) {
    RecipeInputItemStack other;
    return obj != null
        && this.getClass() == obj.getClass()
        && (other = (RecipeInputItemStack) obj).input.equals(this.input)
        && other.getAmount() == this.getAmount();
  }

  @Override
  public JsonElement toJson() {
    return RecipeJson.result(this.input, RecipeJson.defaultOps());
  }
}
