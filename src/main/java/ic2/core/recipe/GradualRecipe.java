package ic2.core.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import ic2.core.recipe.v2.RecipeIo;
import ic2.core.recipe.v2.RecipeJson;
import ic2.core.recipe.v2.Ic2RecipeSerializer;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import ic2.core.item.reactor.AbstractDamageableReactorComponent;
import ic2.core.ref.Ic2RecipeSerializers;
import ic2.core.util.StackUtil;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Shapeless recipe that repairs a damageable reactor component by reducing its custom use counter
 * for each supplied charge material.
 */
public class GradualRecipe implements ic2.compat.Ic2CraftingRecipe {
  private static final Identifier FALLBACK_ID =
      Identifier.fromNamespaceAndPath("ic2", "gradual_recipe");

  private final Identifier id;
  private final AbstractDamageableReactorComponent item;
  private final ItemStackTemplate chargeMaterial;
  private final int amount;
  private final boolean hidden;

  public GradualRecipe(
      Identifier id,
      AbstractDamageableReactorComponent item,
      ItemStack chargeMaterial,
      int amount,
      boolean hidden) {
    this(id, item, ItemStackTemplate.fromNonEmptyStack(chargeMaterial), amount, hidden);
  }

  public GradualRecipe(
      Identifier id,
      AbstractDamageableReactorComponent item,
      ItemStackTemplate chargeMaterial,
      int amount,
      boolean hidden) {
    this.id = id;
    this.item = item;
    this.chargeMaterial = chargeMaterial;
    this.amount = amount;
    this.hidden = hidden;
  }

  @Override
  public boolean matches(@NotNull CraftingInput input, @NotNull Level level) {
    return !this.assemble(input).isEmpty();
  }

  public @NotNull ItemStack assemble(@NotNull CraftingInput input) {
    ItemStack gridItem = ItemStack.EMPTY;
    int chargeMaterials = 0;
    ItemStack chargeMaterial = RecipeJson.materialize(this.chargeMaterial);

    for (int slot = 0; slot < input.size(); slot++) {
      ItemStack stack = input.getItem(slot);
      if (StackUtil.isEmpty(stack)) {
        continue;
      }

      if (StackUtil.isEmpty(gridItem) && stack.getItem() == this.item) {
        gridItem = stack;
      } else if (StackUtil.checkItemEquality(stack, chargeMaterial)) {
        chargeMaterials++;
      } else {
        return ItemStack.EMPTY;
      }
    }

    if (StackUtil.isEmpty(gridItem) || chargeMaterials <= 0) {
      return ItemStack.EMPTY;
    }

    int currentUse = this.item.getUse(gridItem);
    if (currentUse <= 0) {
      return ItemStack.EMPTY;
    }

    ItemStack result = gridItem.copy();
    // ItemStack.copy() shares the immutable CustomData component, while legacy IC2 helpers mutate
    // its backing tag in place. Reattach a private tag before changing the result's use counter.
    CompoundTag tag = StackUtil.getTag(result);
    StackUtil.setTag(result, tag == null ? null : tag.copy());
    result.setCount(1);
    int use = currentUse - this.amount * chargeMaterials;
    if (use > this.item.getMaxUse()) {
      use = this.item.getMaxUse();
    } else if (use < 0) {
      use = 0;
    }

    this.item.setUse(result, use);
    return result;
  }

  @Override
  public @NotNull ItemStack assemble(
      @NotNull CraftingInput input, HolderLookup.Provider registryAccess) {
    return this.assemble(input);
  }

  @Override
  public boolean canCraftInDimensions(int width, int height) {
    return width * height >= 2;
  }

  @Override
  public @NotNull ItemStack getResultItem(@NotNull HolderLookup.Provider registryAccess) {
    return new ItemStack(this.item);
  }

  @Override
  public @NotNull NonNullList<Ingredient> getIngredients() {
    NonNullList<Ingredient> ingredients = NonNullList.create();
    if (!this.hidden) {
      ingredients.add(Ingredient.of(this.item));
      ingredients.add(Ingredient.of(this.chargeMaterial.item().value()));
    }

    return ingredients;
  }

  @Override
  public boolean isSpecial() {
    return true;
  }

  @Override
  public List<net.minecraft.world.item.crafting.display.RecipeDisplay> display() {
    return List.of();
  }

  public Identifier getId() {
    return this.id;
  }

  @Override
  public @NotNull RecipeSerializer<? extends CraftingRecipe> getSerializer() {
    return Ic2RecipeSerializers.GRADUAL;
  }

  @Override
  public @NotNull CraftingBookCategory category() {
    return CraftingBookCategory.MISC;
  }

  public static class Serializer extends Ic2RecipeSerializer<GradualRecipe> {
    private static final MapCodec<GradualRecipe> CODEC =
        new MapCodec<>() {
          @Override
          public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.of("item", "charge_material", "amount", "hidden").map(ops::createString);
          }

          @Override
          public <T> DataResult<GradualRecipe> decode(DynamicOps<T> ops, MapLike<T> input) {
            try {
              JsonObject json = new JsonObject();
              input
                  .entries()
                  .forEach(
                      entry -> {
                        JsonElement key = ops.convertTo(JsonOps.INSTANCE, entry.getFirst());
                        json.add(
                            key.getAsString(), ops.convertTo(JsonOps.INSTANCE, entry.getSecond()));
                      });
              return DataResult.success(fromJson(json, RecipeIo.jsonOps(ops)));
            } catch (RuntimeException e) {
              return DataResult.error(
                  () -> "Failed to parse IC2 gradual recipe: " + e.getMessage());
            }
          }

          @Override
          public <T> RecordBuilder<T> encode(
              GradualRecipe recipe, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            JsonObject json = new JsonObject();
            json.addProperty("item", BuiltInRegistries.ITEM.getKey(recipe.item).toString());
            json.add("charge_material",
                RecipeIo.resultToJson(recipe.chargeMaterial, RecipeIo.jsonOps(ops)));
            json.addProperty("amount", recipe.amount);
            json.addProperty("hidden", recipe.hidden);
            return RecipeIo.encodeJson(json, ops, prefix);
          }
        };

    private static final StreamCodec<RegistryFriendlyByteBuf, GradualRecipe> STREAM_CODEC =
        StreamCodec.of(Serializer::toNetwork, Serializer::fromNetwork);

    @Override
    public MapCodec<GradualRecipe> codec() {
      return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, GradualRecipe> streamCodec() {
      return STREAM_CODEC;
    }

    private static GradualRecipe fromJson(JsonObject json, DynamicOps<JsonElement> ops) {
      Item item = GsonHelper.getAsItem(json, "item").value();
      if (!(item instanceof AbstractDamageableReactorComponent component)) {
        throw new IllegalArgumentException(
            "Gradual recipe item must be a damageable reactor component: " + item);
      }

      ItemStackTemplate chargeMaterial =
          RecipeIo.parseOutputTemplate(GsonHelper.getAsJsonObject(json, "charge_material"), ops);
      int amount = GsonHelper.getAsInt(json, "amount");
      boolean hidden = GsonHelper.getAsBoolean(json, "hidden", false);
      return new GradualRecipe(FALLBACK_ID, component, chargeMaterial, amount, hidden);
    }

    private static GradualRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
      Item item = BuiltInRegistries.ITEM.byId(buffer.readVarInt());
      if (!(item instanceof AbstractDamageableReactorComponent component)) {
        throw new IllegalStateException(
            "Gradual recipe item is not a damageable reactor component: " + item);
      }

      ItemStackTemplate chargeMaterial = ItemStackTemplate.STREAM_CODEC.decode(buffer);
      int amount = buffer.readVarInt();
      boolean hidden = buffer.readBoolean();
      return new GradualRecipe(FALLBACK_ID, component, chargeMaterial, amount, hidden);
    }

    private static void toNetwork(RegistryFriendlyByteBuf buffer, GradualRecipe recipe) {
      buffer.writeVarInt(BuiltInRegistries.ITEM.getId(recipe.item));
      ItemStackTemplate.STREAM_CODEC.encode(buffer, recipe.chargeMaterial);
      buffer.writeVarInt(recipe.amount);
      buffer.writeBoolean(recipe.hidden);
    }


  }
}
