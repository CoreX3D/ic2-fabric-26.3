import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import ic2.core.recipe.v2.RecipeJson;
import ic2.core.recipe.v2.RecipeRegistryAccess;
import ic2.core.recipe.v2.RecipeIo;
import ic2.core.recipe.v2.BasicMachineRecipeSerializer;
import ic2.core.recipe.AdvRecipe;
import ic2.core.recipe.AdvShapelessRecipe;
import ic2.core.recipe.input.RecipeInputFluidContainer;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.RecipeType;

/** Runs real Minecraft codecs and registries without loading incomplete unrelated IC2 classes. */
public final class RecipeCompatibilityProbe {
  private static int checks;

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
    checks++;
  }

  public static void main(String[] args) {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    var ops = RecipeJson.jsonOps(RegistryOps.create(JsonOps.INSTANCE, registries));

    // 26.3 loads recipe registries before binding item component defaults. Exercise that phase,
    // rather than validating only after VanillaRegistries has made ItemStacks constructible.
    boolean defaultsPending = false;
    try { Items.COPPER_INGOT.builtInRegistryHolder().components(); }
    catch (RuntimeException expected) { defaultsPending = expected.getMessage().contains("not bound"); }
    check(defaultsPending, "Regression fixture starts before component binding");
    var deferredDefinition = JsonParser.parseString(
        "{\"item\":\"minecraft:copper_ingot\",\"count\":112,\"components\":{\"minecraft:max_stack_size\":16},\"nbt\":{\"charge\":1200}}")
        .getAsJsonObject();
    ItemStackTemplate deferred = RecipeJson.template(deferredDefinition, ops);
    check(deferred.count() == 112, "Oversized IC output is decoded before component binding");
    check(RecipeJson.template(RecipeJson.result(deferred, ops), ops).equals(deferred),
        "Unbound template JSON round trip preserves quantity and native/custom components");
    var deferredOutputs = RecipeIo.parseOutputs(deferredDefinition, "result", ops);
    check(deferredOutputs.size() == 1 && RecipeIo.outputsToJson(deferredOutputs, ops).get(0)
        .getAsJsonObject().get("count").getAsInt() == 112,
        "Machine output remains a single lazy slot before component binding");
    var shapedSerializer = new AdvRecipe.Serializer();
    var shaped = shapedSerializer.codec().codec().parse(ops, JsonParser.parseString(
        "{\"pattern\":[\"AB\"],\"key\":{\"A\":{\"item\":\"minecraft:copper_ingot\",\"data\":{\"charge\":0}},\"B\":[\"minecraft:iron_ingot\",\"minecraft:gold_ingot\"]},\"result\":{\"item\":\"minecraft:copper_ingot\",\"components\":{\"minecraft:max_stack_size\":16}},\"consuming\":true}")).getOrThrow();
    check(shaped.consuming && !shaped.hidden && shaped.getIngredients().size() == 2,
        "Shaped recipe and partial-NBT ingredients decode before component binding");
    check(!shaped.display().isEmpty() && shaped.placementInfo() != null,
        "Shaped display and placement do not materialize unbound stacks");
    check(shapedSerializer.codec().codec().parse(ops,
        shapedSerializer.codec().codec().encodeStart(ops, shaped).getOrThrow()).getOrThrow().consuming,
        "Shaped JSON round trip before component binding");
    var shapelessSerializer = new AdvShapelessRecipe.Serializer();
    var shapeless = shapelessSerializer.codec().codec().parse(ops, JsonParser.parseString(
        "{\"ingredients\":[{\"item\":\"minecraft:copper_ingot\",\"data\":{\"charge\":0}}],\"result\":{\"item\":\"minecraft:iron_ingot\",\"count\":2},\"hidden\":false}")).getOrThrow();
    check(shapeless.getIngredients().size() == 1 && !shapeless.display().isEmpty()
        && shapeless.placementInfo() != null, "Shapeless display and placement before component binding");
    var fluidShaped = shapedSerializer.codec().codec().parse(ops, JsonParser.parseString(
        "{\"pattern\":[\"A\"],\"key\":{\"A\":{\"fluid\":\"minecraft:water\",\"amount\":1000}},\"result\":{\"item\":\"minecraft:ice\"}}")).getOrThrow();
    check(!fluidShaped.display().isEmpty() && !fluidShaped.placementInfo().isImpossibleToPlace(),
        "Fluid shaped display and placement do not create unbound stacks");
    var fluidShapeless = shapelessSerializer.codec().codec().parse(ops, JsonParser.parseString(
        "{\"ingredients\":[{\"fluid\":\"minecraft:lava\",\"amount\":1000}],\"result\":{\"item\":\"minecraft:obsidian\"}}")).getOrThrow();
    check(!fluidShapeless.display().isEmpty() && !fluidShapeless.placementInfo().isImpossibleToPlace(),
        "Fluid shapeless display and placement do not create unbound stacks");
    var oversizedFluid = new RecipeInputFluidContainer(net.minecraft.world.level.material.Fluids.WATER, 1001);
    check(!oversizedFluid.canPlaceInRecipeBook()
        && oversizedFluid.display() == net.minecraft.world.item.crafting.display.SlotDisplay.Empty.INSTANCE,
        "A one-bucket example never satisfies a larger fluid quantity");
    var noFluid = new RecipeInputFluidContainer(null, 1000);
    check(noFluid.canPlaceInRecipeBook() && noFluid.getIngredient().items()
        .allMatch(holder -> holder.value() instanceof net.minecraft.world.item.BucketItem bucket
            && bucket.getContent() == net.minecraft.world.level.material.Fluids.EMPTY),
        "Null-fluid book examples contain empty native buckets only");
    check(!new RecipeInputFluidContainer(net.minecraft.world.level.material.Fluids.EMPTY, 1000)
        .canPlaceInRecipeBook(), "The empty fluid registry entry cannot represent a filled container");
    var machineSerializer = new BasicMachineRecipeSerializer(RecipeType.SMELTING, null);
    var machine = machineSerializer.codec().codec().parse(ops, JsonParser.parseString(
        "{\"ingredient\":\"minecraft:copper_ingot\",\"result\":{\"item\":\"minecraft:iron_ingot\",\"count\":112}}")).getOrThrow();
    check(machineSerializer.codec().codec().encodeStart(ops, machine).getOrThrow()
        .getAsJsonObject().getAsJsonArray("result").get(0).getAsJsonObject().get("count").getAsInt() == 112,
        "Machine codec round trip remains lazy before binding");
    var pendingBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
    try {
      shapedSerializer.streamCodec().encode(pendingBuffer, shaped);
      var received = shapedSerializer.streamCodec().decode(pendingBuffer);
      check(received.consuming && received.input.length == 2 && received.output.equals(shaped.output)
          && !pendingBuffer.isReadable(), "Shaped wire template round trip before binding");
      RecipeIo.writeOutput(pendingBuffer, deferredOutputs);
      var receivedOutput = RecipeIo.readOutput(pendingBuffer);
      check(RecipeIo.outputTemplates(receivedOutput).getFirst().equals(deferred)
          && !pendingBuffer.isReadable(), "Oversized machine output wire round trip before binding");
    } finally { pendingBuffer.release(); }

    var bootstrapRegistries = net.minecraft.data.registries.VanillaRegistries.createWorldLookup();
    BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(bootstrapRegistries)
        .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
    check(deferredOutputs.iterator().next().getCount() == 112,
        "Lazy output materializes the original quantity after component binding");
    check(deferredOutputs.iterator().next().getMaxStackSize() == 16
        && deferredOutputs.iterator().next().get(DataComponents.CUSTOM_DATA).copyTag().getIntOr("charge", 0) == 1200,
        "Materialized machine output preserves native and custom components");
    ItemStack requiredZero = new ItemStack(Items.COPPER_INGOT);
    check(shaped.input[0].matches(requiredZero), "Legacy zero partial-NBT matches an untagged input");
    check(shaped.getResultItem().getMaxStackSize() == 16,
        "Crafting output materializes component patches after binding");
    var fluidIngredient = fluidShaped.getIngredients().getFirst();
    check(fluidIngredient.test(new ItemStack(Items.WATER_BUCKET))
        && !fluidIngredient.test(new ItemStack(Items.LAVA_BUCKET))
        && !fluidIngredient.test(new ItemStack(Items.BUCKET)),
        "Fluid recipe-book placement accepts correct filled buckets only");
    var nativeInput = RecipeIo.parseInput(JsonParser.parseString(
        "{\"item\":\"minecraft:copper_ingot\",\"components\":{\"minecraft:max_stack_size\":16}}"), ops);
    check(!nativeInput.matches(requiredZero) && nativeInput.matches(shaped.getResultItem()),
        "Native ingredient component constraints are enforced");
    var emptyLegacyInput = RecipeIo.parseInput(JsonParser.parseString(
        "{\"item\":\"minecraft:copper_ingot\",\"data\":{}}"), ops);
    check(emptyLegacyInput.matches(deferredOutputs.iterator().next()),
        "Empty legacy data remains a wildcard for custom data");
    var noCustomDataInput = RecipeIo.parseInput(JsonParser.parseString(
        "{\"item\":\"minecraft:copper_ingot\",\"components\":{\"!minecraft:custom_data\":{}}}"), ops);
    check(noCustomDataInput.matches(requiredZero) && !noCustomDataInput.matches(deferredOutputs.iterator().next()),
        "Explicit removed ingredient components remain constraints");

    check(RecipeJson.ingredient(JsonParser.parseString("{\"item\":\"minecraft:copper_ingot\"}"), ops)
        .test(new ItemStack(Items.COPPER_INGOT)), "Legacy item ingredient");
    check(RecipeJson.ingredient(JsonParser.parseString("\"minecraft:copper_ingot\""), ops)
        .test(new ItemStack(Items.COPPER_INGOT)), "Native item ingredient");

    var legacyResult = JsonParser.parseString(
        "{\"item\":\"minecraft:copper_ingot\",\"count\":6,\"nbt\":{\"charge\":1200}}")
        .getAsJsonObject();
    ItemStack legacy = RecipeJson.result(legacyResult, ops);
    check(legacy.is(Items.COPPER_INGOT) && legacy.getCount() == 6, "Legacy result/count");
    check(legacy.get(DataComponents.CUSTOM_DATA).copyTag().getIntOr("charge", 0) == 1200,
        "Legacy NBT becomes custom_data");
    check(legacyResult.has("item") && !legacyResult.has("id"), "Decoding does not mutate input JSON");
    ItemStack roundTrip = RecipeJson.result(RecipeJson.result(legacy, ops), ops);
    check(ItemStack.isSameItemSameComponents(legacy, roundTrip) && roundTrip.getCount() == 6,
        "Legacy result round trip preserves components/count");

    ItemStack components = RecipeJson.result(JsonParser.parseString(
        "{\"id\":\"minecraft:copper_ingot\",\"count\":3,\"components\":{\"minecraft:max_stack_size\":16}}")
        .getAsJsonObject(), ops);
    check(components.getMaxStackSize() == 16 && components.getCount() == 3,
        "Native result preserves component patches");
    boolean rejected = false;
    try {
      RecipeJson.result(JsonParser.parseString("{\"item\":\"minecraft:copper_ingot\",\"count\":0}")
          .getAsJsonObject(), ops);
    } catch (IllegalStateException expected) {
      rejected = true;
    }
    check(rejected, "Invalid zero output count is rejected");

    // A separate loading registry proves decoding uses its RegistryOps instead of global tags.
    var loadingItems = new MappedRegistry<Item>(Registries.ITEM, Lifecycle.stable());
    var copperKey = ResourceKey.create(Registries.ITEM, Identifier.parse("minecraft:copper_ingot"));
    var copper = loadingItems.register(copperKey, Items.COPPER_INGOT, RegistrationInfo.BUILT_IN);
    copper.bindComponents(Items.COPPER_INGOT.builtInRegistryHolder().components());
    var copperTag = TagKey.create(Registries.ITEM, Identifier.parse("c:ingots/copper"));
    loadingItems.bindTags(Map.of(copperTag, List.of(copper)));
    loadingItems.freeze();
    var loadingProvider = HolderLookup.Provider.create(Stream.concat(
        registries.listRegistries().filter(registry -> !registry.key().equals(Registries.ITEM)),
        Stream.of(loadingItems)));
    var loadingOps = RecipeJson.jsonOps(RegistryOps.create(JsonOps.INSTANCE, loadingProvider));
    Ingredient tagged = RecipeJson.ingredient(JsonParser.parseString("{\"tag\":\"c:ingots/copper\"}"), loadingOps);
    check(tagged.test(new ItemStack(copper)), "Legacy tag uses the loading registry");
    check(RecipeJson.ingredient(JsonParser.parseString("\"#c:ingots/copper\""), loadingOps)
        .test(new ItemStack(copper)), "Native tag uses the loading registry");
    var reloadingOutput = new ItemStackTemplate(copper, 2, net.minecraft.core.component.DataComponentPatch.EMPTY);
    copper.bindComponents(net.minecraft.core.component.DataComponentMap.builder()
        .addAll(Items.COPPER_INGOT.builtInRegistryHolder().components())
        .set(DataComponents.MAX_STACK_SIZE, 32).build());
    check(RecipeJson.materialize(reloadingOutput).getMaxStackSize() == 32,
        "Template resolves the loading registry's bound defaults");
    copper.bindComponents(net.minecraft.core.component.DataComponentMap.builder()
        .addAll(Items.COPPER_INGOT.builtInRegistryHolder().components())
        .set(DataComponents.MAX_STACK_SIZE, 16).build());
    check(RecipeJson.materialize(reloadingOutput).getMaxStackSize() == 16,
        "Later component binding refreshes template materialization without a one-time global flag");

    SmeltingRecipe recipe = new SmeltingRecipe(new Recipe.CommonInfo(true),
        new AbstractCookingRecipe.CookingBookInfo(CookingBookCategory.MISC, ""),
        Ingredient.of(Items.RAW_COPPER), new ItemStackTemplate(Items.COPPER_INGOT), 0.7f, 200);
    var recipeKey = ResourceKey.create(Registries.RECIPE, Identifier.parse("ic2:probe_copper"));
    RecipeHolder<?> holder = new RecipeHolder<>(recipeKey, recipe);
    var manager = RecipeRegistryAccess.create(List.of(holder), registries);
    check(manager.getRecipes().size() == 1 && manager.byKey(recipeKey).isPresent(),
        "Client snapshot indexes ResourceKey recipes");
    check(recipe.assemble(new SingleRecipeInput(new ItemStack(Items.RAW_COPPER))).is(Items.COPPER_INGOT),
        "Modern smelting assembly");
    var reloaded = RecipeRegistryAccess.create(List.of(), registries);
    check(reloaded.getRecipes().isEmpty() && manager.getRecipes().size() == 1,
        "Reload creates an independent snapshot");

    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
    try {
      RecipeHolder.STREAM_CODEC.encode(buffer, holder);
      RecipeHolder<?> received = RecipeHolder.STREAM_CODEC.decode(buffer);
      check(received.id().equals(recipeKey) && !buffer.isReadable(), "RecipeHolder wire round trip");
      check(((SmeltingRecipe) received.value()).assemble(new SingleRecipeInput(new ItemStack(Items.RAW_COPPER)))
          .is(Items.COPPER_INGOT), "Wire recipe retains assembly result");
    } finally {
      buffer.release();
    }
    System.out.println("PASS: " + checks + " real Minecraft 26.3 recipe compatibility checks");
  }
}
