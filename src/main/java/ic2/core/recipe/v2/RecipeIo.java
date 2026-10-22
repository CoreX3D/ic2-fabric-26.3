package ic2.core.recipe.v2;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStackTemplate;
import ic2.api.recipe.IRecipeInput;
import ic2.api.recipe.RecipeOutputWeighted;
import ic2.core.fluid.FluidHandler;
import ic2.core.fluid.Ic2FluidStack;
import ic2.core.recipe.input.RecipeInputFluidContainer;
import ic2.core.recipe.input.RecipeInputIngredient;
import ic2.core.recipe.input.RecipeInputItemStack;
import ic2.core.recipe.input.RecipeInputMultiple;
import ic2.core.util.StackUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

public class RecipeIo {
  public static DynamicOps<JsonElement> jsonOps(DynamicOps<?> ops) {
    return RecipeJson.jsonOps(ops);
  }

  public static DynamicOps<JsonElement> defaultOps() {
    return RecipeJson.defaultOps();
  }

  public static IRecipeInput parseInput(JsonElement json) {
    return parseInput(json, defaultOps());
  }

  public static IRecipeInput parseInput(JsonElement json, DynamicOps<JsonElement> ops) {
    if (json == null || json.isJsonNull()) throw new JsonSyntaxException("Missing IC2 ingredient");
    if (json.isJsonArray()) {
      return parseMultiple(json.getAsJsonArray(), 1, ops);
    }

    int count = 1;
    if (json.isJsonObject()) {
      count = GsonHelper.getAsInt(json.getAsJsonObject(), "count", 1);
    }
    if (count <= 0) throw new JsonSyntaxException("Ingredient count must be positive");

    if (json.isJsonObject()) {
      JsonObject object = json.getAsJsonObject();
      if (object.has("fluid")) {
        return new RecipeInputFluidContainer(
            asFluid(object.get("fluid")), GsonHelper.getAsInt(object, "amount"));
      }

      if (object.has("data") || object.has("components") || object.has("id")) {
        JsonObject definition = object.deepCopy();
        if (definition.has("data")) {
          // Historic data is a partial CUSTOM_DATA constraint, including an empty wildcard.
          CompoundTag data = asNbt(definition.remove("data"), "data");
          JsonObject components = definition.has("components")
              ? GsonHelper.getAsJsonObject(definition, "components") : new JsonObject();
          components.remove("!minecraft:custom_data");
          components.add("minecraft:custom_data", NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, data));
          definition.add("components", components);
        }
        return new RecipeInputItemStack(parseOutputTemplate(definition, ops));
      }

      if (object.has("any")) {
        JsonElement entry = object.get("any");
        if (entry.isJsonArray()) return parseMultiple(entry.getAsJsonArray(), count, ops);
        return new RecipeInputIngredient(Ingredient.CODEC.parse(ops, entry).getOrThrow(), count);
      }
    }

    return new RecipeInputIngredient(
        RecipeJson.ingredient(json, ops), count);
  }

  private static RecipeInputMultiple parseMultiple(JsonArray array, int count, DynamicOps<JsonElement> ops) {
    if (array.isEmpty()) throw new JsonSyntaxException("Ingredient alternatives must not be empty");
    IRecipeInput[] inputs = new IRecipeInput[array.size()];

    for (int i = 0; i < array.size(); i++) {
      inputs[i] = parseInput(array.get(i), ops);
    }

    return new RecipeInputMultiple(count, inputs);
  }

  public static Ic2FluidStack parseFluidStack(JsonObject json) {
    Fluid fluid = asFluid(json.get("fluid"));
    int amountMb = GsonHelper.getAsInt(json, "amount");
    return FluidHandler.createFluidStackMb(fluid, amountMb, null);
  }

  public static Collection<ItemStack> parseOutputs(JsonElement json, String name) {
    return parseOutputs(json, name, defaultOps());
  }

  public static Collection<ItemStack> parseOutputs(JsonElement json, String name, DynamicOps<JsonElement> ops) {
    return new TemplateOutputList(parseOutputTemplates(json, name, ops));
  }

  public static List<ItemStackTemplate> parseOutputTemplates(JsonElement json, String name, DynamicOps<JsonElement> ops) {
    if (!json.isJsonArray()) {
      if (json.isJsonObject()) {
        return List.of(parseOutputTemplate(json.getAsJsonObject(), ops));
      } else {
        throw new JsonSyntaxException(
            "Expected " + name + " to be an array or object for output parsing.");
      }
    } else {
      JsonArray array = json.getAsJsonArray();
      ArrayList<ItemStackTemplate> output = new ArrayList<>(array.size());

      for (int i = 0; i < array.size(); i++) {
        output.addAll(parseOutputTemplates(array.get(i), "array element", ops));
      }

      return output;
    }
  }

  public static void parseWeightedOutputs(
      JsonElement json, String name, RecipeOutputWeighted randomOutput) {
    parseWeightedOutputs(json, name, randomOutput, defaultOps());
  }

  public static void parseWeightedOutputs(
      JsonElement json, String name, RecipeOutputWeighted randomOutput, DynamicOps<JsonElement> ops) {
    if (json.isJsonArray()) {
      JsonArray array = json.getAsJsonArray();

      for (int i = 0; i < array.size(); i++) {
        parseWeightedOutputs(array.get(i), "array element", randomOutput, ops);
      }
    } else {
      if (!json.isJsonObject()) {
        throw new JsonSyntaxException(
            "Expected " + name + " to be an array or object for output parsing.");
      }

      parseWeightedOutput(json.getAsJsonObject(), randomOutput, ops);
    }
  }

  public static ItemStack parseOutput(JsonObject json) {
    return parseOutput(json, defaultOps());
  }

  public static ItemStack parseOutput(JsonObject json, DynamicOps<JsonElement> ops) {
    return RecipeJson.result(json, ops);
  }

  public static ItemStackTemplate parseOutputTemplate(JsonObject json, DynamicOps<JsonElement> ops) {
    return RecipeJson.template(json, ops);
  }

  public static void parseWeightedOutput(JsonObject json, RecipeOutputWeighted randomOutput) {
    parseWeightedOutput(json, randomOutput, defaultOps());
  }

  private static void parseWeightedOutput(JsonObject json, RecipeOutputWeighted randomOutput, DynamicOps<JsonElement> ops) {
    int weight = GsonHelper.getAsInt(json, "weight", 1);
    if (weight <= 0) throw new JsonSyntaxException("Recipe output weight must be positive");
    ItemStackTemplate template = parseOutputTemplate(json, ops);
    if (randomOutput instanceof TemplateWeightedOutput templates) templates.addTemplate(template, weight);
    else randomOutput.addOutput(RecipeJson.materialize(template), weight);
  }

  public static JsonObject resultToJson(ItemStack stack) {
    return resultToJson(stack, defaultOps());
  }

  public static JsonObject resultToJson(ItemStack stack, DynamicOps<JsonElement> ops) {
    return RecipeJson.result(stack, ops);
  }

  public static JsonObject resultToJson(ItemStackTemplate template, DynamicOps<JsonElement> ops) {
    return RecipeJson.result(template, ops);
  }

  public static JsonElement inputToJson(IRecipeInput input, DynamicOps<JsonElement> ops) {
    if (input instanceof RecipeInputItemStack stack) return resultToJson(stack.input, ops);
    if (input instanceof RecipeInputIngredient ingredient) {
      JsonElement encoded = Ingredient.CODEC.encodeStart(ops, ingredient.getIngredient()).getOrThrow();
      if (input.getAmount() == 1) return encoded;
      JsonObject object = new JsonObject();
      object.add("any", encoded);
      object.addProperty("count", input.getAmount());
      return object;
    }
    if (input instanceof RecipeInputMultiple multiple) {
      JsonArray alternatives = new JsonArray();
      for (IRecipeInput child : multiple.inputs) alternatives.add(inputToJson(child, ops));
      if (input.getAmount() == 1) return alternatives;
      JsonObject object = new JsonObject();
      object.add("any", alternatives);
      object.addProperty("count", input.getAmount());
      return object;
    }
    return ((ic2.core.recipe.input.RecipeInputBase) input).toJson();
  }

  /** Item-only recipe-book placement must not substitute empty or differently filled containers. */
  public static boolean canPlaceInRecipeBook(IRecipeInput input) {
    if (input instanceof RecipeInputFluidContainer fluid) return fluid.canPlaceInRecipeBook();
    if (input instanceof RecipeInputMultiple multiple) {
      for (IRecipeInput child : multiple.inputs) if (!canPlaceInRecipeBook(child)) return false;
    }
    return true;
  }

  public static net.minecraft.world.item.crafting.display.SlotDisplay inputDisplay(IRecipeInput input) {
    if (input instanceof RecipeInputFluidContainer fluid) return fluid.display();
    if (input instanceof RecipeInputMultiple multiple) {
      return new net.minecraft.world.item.crafting.display.SlotDisplay.Composite(
          java.util.Arrays.stream(multiple.inputs).map(RecipeIo::inputDisplay).toList());
    }
    return input.getIngredient().display();
  }

  public static JsonArray outputsToJson(Collection<ItemStack> output, DynamicOps<JsonElement> ops) {
    JsonArray result = new JsonArray();
    for (ItemStackTemplate template : outputTemplates(output)) result.add(resultToJson(template, ops));
    return result;
  }

  public static List<ItemStackTemplate> outputTemplates(Collection<ItemStack> output) {
    if (output instanceof TemplateOutputList templates) return templates.templates();
    return output.stream().map(ItemStackTemplate::fromNonEmptyStack).toList();
  }

  public static void addMetadata(JsonObject json, CompoundTag metadata) {
    if (metadata != null) {
      JsonObject data = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, metadata).getAsJsonObject();
      data.entrySet().forEach(entry -> json.add(entry.getKey(), entry.getValue()));
    }
  }

  public static <T> RecordBuilder<T> encodeJson(JsonObject json, DynamicOps<T> ops, RecordBuilder<T> prefix) {
    json.entrySet().forEach(entry -> prefix.add(entry.getKey(), JsonOps.INSTANCE.convertTo(ops, entry.getValue())));
    return prefix;
  }

  public static JsonObject resultToJson(WeightedItemStack stack) {
    JsonObject json = new JsonObject();
    json.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.itemStack.getItem()).toString());
    if (stack.itemStack.getCount() != 1) {
      json.addProperty("count", stack.itemStack.getCount());
    }

    json.addProperty("weight", stack.weight);
    return json;
  }

  public static JsonObject fluidStackToJson(Ic2FluidStack stack) {
    JsonObject json = new JsonObject();
    json.addProperty("fluid", BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString());
    json.addProperty("amount", stack.getAmountMb());
    return json;
  }

  public static void writeInput(RegistryFriendlyByteBuf buf, IRecipeInput input) {
    if (input instanceof RecipeInputFluidContainer fluidContainer) {
      buf.writeByte(0);
      buf.writeVarInt(BuiltInRegistries.FLUID.getId(fluidContainer.fluid));
      buf.writeVarInt(fluidContainer.amount);
    } else if (input instanceof RecipeInputIngredient ingredient) {
      buf.writeByte(1);
      Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient.getIngredient());
      buf.writeVarInt(ingredient.getAmount());
    } else if (input instanceof RecipeInputItemStack stack) {
      buf.writeByte(2);
      ItemStackTemplate.STREAM_CODEC.encode(buf, stack.input);
    } else {
      if (!(input instanceof RecipeInputMultiple mult)) {
        throw new IllegalArgumentException(
            "Unkown RecipeInput type: " + input.getClass().getName());
      }

      buf.writeByte(3);
      buf.writeVarInt(mult.inputs.length);

      for (IRecipeInput i : mult.inputs) {
        writeInput(buf, i);
      }

      buf.writeVarInt(mult.getAmount());
    }
  }

  public static IRecipeInput readInput(RegistryFriendlyByteBuf buf) {
    return switch (buf.readByte()) {
      case 0 ->
          new RecipeInputFluidContainer(
              BuiltInRegistries.FLUID.byId(buf.readVarInt()), buf.readVarInt());
      case 1 ->
          new RecipeInputIngredient(Ingredient.CONTENTS_STREAM_CODEC.decode(buf), buf.readVarInt());
      case 2 -> new RecipeInputItemStack(ItemStackTemplate.STREAM_CODEC.decode(buf));
      case 3 -> {
        IRecipeInput[] inputs = new IRecipeInput[buf.readVarInt()];

        for (int i = 0; i < inputs.length; i++) {
          inputs[i] = readInput(buf);
        }

        yield new RecipeInputMultiple(buf.readVarInt(), inputs);
      }
      default -> throw new IllegalArgumentException("Unkown RecipeInput type.");
    };
  }

  public static void writeOutput(RegistryFriendlyByteBuf buf, Collection<ItemStack> output) {
    buf.writeVarInt(output.size());

    for (ItemStackTemplate template : outputTemplates(output)) {
      ItemStackTemplate.STREAM_CODEC.encode(buf, template);
    }
  }

  public static void writeIntegerOutput(RegistryFriendlyByteBuf buf, int output) {
    buf.writeInt(output);
  }

  public static void writeWeightedOutput(
      RegistryFriendlyByteBuf buf, RecipeOutputWeighted outputs) {
    if (outputs instanceof TemplateWeightedOutput templates) {
      buf.writeVarInt(templates.templates().size());
      for (var entry : templates.templates()) {
        ItemStackTemplate.STREAM_CODEC.encode(buf, entry.template());
        buf.writeInt(entry.weight());
      }
      return;
    }
    buf.writeVarInt(outputs.getOutputs().size());
    outputs.forEach(
        (stack, weight) -> {
          ItemStackTemplate.STREAM_CODEC.encode(buf, ItemStackTemplate.fromNonEmptyStack(stack));
          buf.writeInt(weight);
        });
  }

  public static Collection<ItemStack> readOutput(RegistryFriendlyByteBuf buf) {
    int amount = buf.readVarInt();
    List<ItemStackTemplate> stacks = new ArrayList<>(amount);

    for (int i = 0; i < amount; i++) {
      stacks.add(ItemStackTemplate.STREAM_CODEC.decode(buf));
    }

    return new TemplateOutputList(stacks);
  }

  public static Integer readIntegerOutput(RegistryFriendlyByteBuf buf) {
    return buf.readInt();
  }

  public static RecipeOutputWeighted readWeightedOutput(
      RegistryFriendlyByteBuf buf, RecipeOutputWeighted outputs) {
    int amount = buf.readVarInt();

    for (int i = 0; i < amount; i++) {
      ItemStackTemplate template = ItemStackTemplate.STREAM_CODEC.decode(buf);
      int weight = buf.readInt();
      if (outputs instanceof TemplateWeightedOutput templates) templates.addTemplate(template, weight);
      else outputs.addOutput(RecipeJson.materialize(template), weight);
    }

    return outputs;
  }

  public static void writeFluidStack(RegistryFriendlyByteBuf buf, Ic2FluidStack stack) {
    buf.writeVarInt(BuiltInRegistries.FLUID.getId(stack.getFluid()));
    buf.writeVarInt(stack.getAmountMb());
  }

  public static Ic2FluidStack readFluidStack(RegistryFriendlyByteBuf buf) {
    return FluidHandler.createFluidStackMb(
        BuiltInRegistries.FLUID.byId(buf.readVarInt()), buf.readVarInt(), null);
  }

  private static Fluid asFluid(JsonElement element) {
    if (element.isJsonPrimitive()) {
      String string = element.getAsString();
      return BuiltInRegistries.FLUID
          // TODO
          .getOptional(Identifier.parse(string))
          .orElseThrow(
              () ->
                  new JsonSyntaxException(
                      "Expected "
                          + "fluid"
                          + " to be an fluid, was unknown string '"
                          + string
                          + "'"));
    } else {
      throw new JsonSyntaxException(
          "Expected " + "fluid" + " to be an fluid, was " + GsonHelper.getType(element));
    }
  }

  private static CompoundTag asNbt(JsonElement element, String name) {
    Tag nbtElement = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, element);
    if (nbtElement instanceof CompoundTag nbt) {
      return nbt;
    } else {
      throw new JsonSyntaxException(
          "Expected "
              + name
              + " to be an NBT compound (an object), was "
              + GsonHelper.getType(element));
    }
  }

  @Nullable
  private static CompoundTag getNbt(JsonObject object) {
    return object.has("nbt") ? asNbt(object.get("nbt"), "nbt") : null;
  }
}
