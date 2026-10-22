package ic2.core.ref;

import com.google.gson.JsonObject;
import ic2.core.IC2;
import ic2.core.item.armor.jetpack.JetpackAttachmentRecipe;
import ic2.core.recipe.AdvRecipe;
import ic2.core.recipe.AdvShapelessRecipe;
import ic2.core.recipe.GradualRecipe;
import ic2.core.recipe.v2.BasicMachineRecipeSerializer;
import ic2.core.recipe.v2.Ic2RecipeSerializer;
import ic2.core.recipe.v2.RecipeHolder;
import ic2.api.recipe.IRecipeInput;
import ic2.api.recipe.ICannerBottleRecipeManager;
import ic2.api.recipe.ICannerEnrichRecipeManager;
import ic2.core.fluid.Ic2FluidStack;
import java.util.Collection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import ic2.core.recipe.v2.CannerBottleRecipeSerializer;
import ic2.core.recipe.v2.CannerEnrichRecipeSerializer;
import ic2.core.recipe.v2.IntegerOutputRecipeSerializer;
import ic2.core.recipe.v2.WeightedMachineRecipeSerializer;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.RecipeSerializer;

public final class Ic2RecipeSerializers {
  public static final RecipeSerializer<AdvRecipe> SHAPED =
      register("shaped", new AdvRecipe.Serializer());
  public static final RecipeSerializer<AdvShapelessRecipe> SHAPELESS =
      register("shapeless", new AdvShapelessRecipe.Serializer());
  public static final RecipeSerializer<GradualRecipe> GRADUAL =
      register("gradual", new GradualRecipe.Serializer());
  public static final RecipeSerializer<JetpackAttachmentRecipe> JETPACK_ATTACHMENT =
      register("jetpack_attachment", new JetpackAttachmentRecipe.Serializer());
  public static final RecipeSerializer<RecipeHolder<IRecipeInput, Collection<ItemStack>>> MACERATOR =
      register("macerator", new WeightedMachineRecipeSerializer(Ic2RecipeTypes.MACERATOR, null));
  public static final RecipeSerializer<RecipeHolder<IRecipeInput, Collection<ItemStack>>> EXTRACTOR =
      register("extractor", new BasicMachineRecipeSerializer(Ic2RecipeTypes.EXTRACTOR, null));
  public static final RecipeSerializer<RecipeHolder<IRecipeInput, Collection<ItemStack>>> COMPRESSOR =
      register("compressor", new BasicMachineRecipeSerializer(Ic2RecipeTypes.COMPRESSOR, null));
  public static final RecipeSerializer<RecipeHolder<IRecipeInput, Collection<ItemStack>>> CENTRIFUGE =
      register(
          "centrifuge",
          new BasicMachineRecipeSerializer(Ic2RecipeTypes.CENTRIFUGE, intMeta("minHeat")));
  public static final RecipeSerializer<RecipeHolder<IRecipeInput, Collection<ItemStack>>> BLOCK_CUTTER =
      register(
          "block_cutter",
          new BasicMachineRecipeSerializer(Ic2RecipeTypes.BLOCK_CUTTER, intMeta("hardness")));
  public static final RecipeSerializer<RecipeHolder<IRecipeInput, Collection<ItemStack>>> BLAST_FURNACE =
      register(
          "blast_furnace",
          new BasicMachineRecipeSerializer(Ic2RecipeTypes.BLAST_FURNACE, twoIntsMeta()));
  public static final RecipeSerializer<RecipeHolder<IRecipeInput, Collection<ItemStack>>> METAL_FORMER_EXTRUDING =
      register(
          "metal_former_extruding",
          new BasicMachineRecipeSerializer(Ic2RecipeTypes.METAL_FORMER_EXTRUDING, null));
  public static final RecipeSerializer<RecipeHolder<IRecipeInput, Collection<ItemStack>>> METAL_FORMER_CUTTING =
      register(
          "metal_former_cutting",
          new BasicMachineRecipeSerializer(Ic2RecipeTypes.METAL_FORMER_CUTTING, null));
  public static final RecipeSerializer<RecipeHolder<IRecipeInput, Collection<ItemStack>>> METAL_FORMER_ROLLING =
      register(
          "metal_former_rolling",
          new BasicMachineRecipeSerializer(Ic2RecipeTypes.METAL_FORMER_ROLLING, null));
  public static final RecipeSerializer<RecipeHolder<IRecipeInput, Collection<ItemStack>>> ORE_WASHER =
      register(
          "ore_washer",
          new BasicMachineRecipeSerializer(Ic2RecipeTypes.ORE_WASHER, intMeta("amount")));
  public static final RecipeSerializer<RecipeHolder<IRecipeInput, Integer>> MATTER_FABRICATOR =
      register(
          "matter_fabricator",
          new IntegerOutputRecipeSerializer(Ic2RecipeTypes.MATTER_FABRICATOR, null));
  public static final RecipeSerializer<RecipeHolder<ICannerBottleRecipeManager.Input, ItemStack>> CANNER_BOTTLE =
      register("canner_bottle", new CannerBottleRecipeSerializer());
  public static final RecipeSerializer<RecipeHolder<ICannerEnrichRecipeManager.Input, Ic2FluidStack>> CANNER_ENRICH =
      register("canner_enrich", new CannerEnrichRecipeSerializer());

  public static void init() {}

  private static <T extends Recipe<?>> RecipeSerializer<T> register(String name, Ic2RecipeSerializer<T> factory) {
    RecipeSerializer<T> serializer = factory.serializer();
    IC2.envProxy.registerRecipeSerializer(IC2.getIdentifier(name), serializer);
    return serializer;
  }

  private static Function<JsonObject, CompoundTag> intMeta(String name) {
    return json -> {
      int metaValue = GsonHelper.getAsInt(json, name);
      CompoundTag nbt = new CompoundTag();
      nbt.putInt(name, metaValue);
      return nbt;
    };
  }

  private static Function<JsonObject, CompoundTag> twoIntsMeta() {
    return json -> {
      int metaValue1 = GsonHelper.getAsInt(json, "fluid");
      int metaValue2 = GsonHelper.getAsInt(json, "duration");
      CompoundTag nbt = new CompoundTag();
      nbt.putInt("fluid", metaValue1);
      nbt.putInt("duration", metaValue2);
      return nbt;
    };
  }
}
