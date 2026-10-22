package ic2.core.recipe.v2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Codec;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;

/** Registry-aware bridge between IC's historic JSON and the native Minecraft recipe codecs. */
public final class RecipeJson {
  private RecipeJson() {}

  public static DynamicOps<JsonElement> jsonOps(DynamicOps<?> ops) {
    return ops instanceof RegistryOps<?> registries
        ? registries.withParent(JsonOps.INSTANCE) : defaultOps();
  }

  public static DynamicOps<JsonElement> defaultOps() {
    return RegistryOps.create(JsonOps.INSTANCE,
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
  }

  public static Ingredient ingredient(JsonElement json, DynamicOps<JsonElement> ops) {
    if (json.isJsonObject()) {
      JsonObject object = json.getAsJsonObject();
      if (object.has("item")) json = object.get("item");
      else if (object.has("tag")) json = new JsonPrimitive("#" + object.get("tag").getAsString());
    }
    return Ingredient.CODEC.parse(ops, json).getOrThrow();
  }

  public static ItemStack result(JsonObject json, DynamicOps<JsonElement> ops) {
    return materialize(template(json, ops));
  }

  /** Decode a recipe without reading item defaults, which are bound after datapack loading. */
  public static ItemStackTemplate template(JsonObject json, DynamicOps<JsonElement> ops) {
    JsonObject template = json.deepCopy();
    if (!template.has("id") && template.has("item")) template.add("id", template.remove("item"));
    template.remove("nbt");
    template.remove("weight");
    // IC machine outputs can intentionally exceed the vanilla codec's limit of 99. They remain
    // one logical output slot and are distributed by the machine's normal output insertion code.
    int count = template.has("count")
        ? Codec.INT.parse(ops, template.remove("count")).getOrThrow() : 1;
    if (count <= 0) throw new IllegalStateException("Recipe output count must be positive");
    ItemStackTemplate result = ItemStackTemplate.CODEC.parse(ops, template).getOrThrow().withCount(count);
    if (json.has("nbt") && !json.get("nbt").isJsonNull()) {
      if (!(JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json.get("nbt")) instanceof CompoundTag tag)) {
        throw new JsonSyntaxException("Recipe result nbt must be an object");
      }
      var split = result.components().split();
      var patch = DataComponentPatch.builder().set(split.added());
      split.removed().forEach(patch::remove);
      if (!tag.isEmpty()) patch.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
      else patch.remove(DataComponents.CUSTOM_DATA);
      result = new ItemStackTemplate(result.item(), count, patch.build());
    }
    return result;
  }

  /** Materialize only when item defaults are available; keep oversized IC output quantities. */
  public static ItemStack materialize(ItemStackTemplate template) {
    ItemStack stack = template.withCount(1).create();
    if (stack.isEmpty()) throw new IllegalStateException("Invalid IC2 recipe item components: " + template);
    stack.setCount(template.count());
    return stack;
  }

  public static JsonObject result(ItemStack stack, DynamicOps<JsonElement> ops) {
    return result(ItemStackTemplate.fromNonEmptyStack(stack), ops);
  }

  public static JsonObject result(ItemStackTemplate template, DynamicOps<JsonElement> ops) {
    JsonObject json = ItemStackTemplate.CODEC.encodeStart(ops, template.withCount(1))
        .getOrThrow().getAsJsonObject();
    if (template.count() != 1) json.addProperty("count", template.count());
    return json;
  }
}
