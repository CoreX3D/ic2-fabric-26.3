package ic2.core.gametest;

import ic2.core.recipe.AdvRecipe;
import ic2.core.recipe.RecipeBookStackRules;
import ic2.core.recipe.input.RecipeInputItemStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Native book counting, source selection and result pickup for every fresh-fuel-rod recipe. */
public final class BookFuelRodGameTests {
  private static final String EMPTY = "ic2:gametest/empty3x3x3";
  private static final List<RodRecipe> RECIPES = List.of(
      new RodRecipe("dual_uranium_fuel_rod", "uranium_fuel_rod", "dual_uranium_fuel_rod", 2, 1, 0),
      new RodRecipe("quad_uranium_fuel_rod", "uranium_fuel_rod", "quad_uranium_fuel_rod", 4, 3, 2),
      new RodRecipe("quad_uranium_fuel_rod_from_dual", "dual_uranium_fuel_rod", "quad_uranium_fuel_rod", 2, 1, 2),
      new RodRecipe("dual_mox_fuel_rod", "mox_fuel_rod", "dual_mox_fuel_rod", 2, 1, 0),
      new RodRecipe("quad_mox_fuel_rod", "mox_fuel_rod", "quad_mox_fuel_rod", 4, 3, 2),
      new RodRecipe("quad_mox_fuel_rod_from_dual", "dual_mox_fuel_rod", "quad_mox_fuel_rod", 2, 1, 2));

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void allSixRecipesCraftFreshRodsWithAbsentAndExplicitZeroUse(GameTestHelper helper) {
    for (RodRecipe specification : RECIPES) {
      for (boolean explicitZero : List.of(false, true)) {
        ServerPlayer player = freshPlayer(helper);
        CraftingMenu menu = tableMenu(helper, player);
        RecipeHolder<CraftingRecipe> recipe = recipe(helper, specification);
        provideMaterials(player, specification);
        for (int index = 0; index < specification.rods(); index++) {
          player.getInventory().add(explicitZero ? withUse(specification.rod(), 0)
              : stack("ic2:" + specification.rod()));
        }
        assertCraftable(helper, player, recipe, true);
        placeAndTake(helper, player, menu, recipe, specification);
        helper.assertTrue(player.getInventory().isEmpty(),
            "one complete fresh ingredient set must be consumed: " + specification.recipe());
      }
    }
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void usedOnlyRodsCannotPassInventoryOrGridCraftableFilter(GameTestHelper helper) {
    for (RodRecipe specification : RECIPES) {
      ServerPlayer player = freshPlayer(helper);
      CraftingMenu menu = tableMenu(helper, player);
      RecipeHolder<CraftingRecipe> recipe = recipe(helper, specification);
      provideMaterials(player, specification);
      List<ItemStack> originals = new ArrayList<>();
      for (int index = 0; index < specification.rods(); index++) {
        ItemStack used = markedUsedRod(specification.rod(), index);
        originals.add(used.copy());
        player.getInventory().add(used);
      }
      assertCraftable(helper, player, recipe, false);
      ItemStack used = originals.getFirst();
      helper.assertTrue(!Inventory.isUsableForCrafting(used),
          "native inventory selection must reject an already-used fuel rod");
      helper.assertValueEqual(player.getInventory().findSlotMatchingCraftingIngredient(
          used.typeHolder(), ItemStack.EMPTY), -1, "only used rods must not be autofill sources");

      // Grid slots use accountStack directly, bypassing Inventory.isUsableForCrafting.
      StackedItemContents gridContents = new StackedItemContents();
      for (ItemStack material : materialStacks(specification)) gridContents.accountStack(material);
      for (ItemStack rod : originals) gridContents.accountStack(rod);
      helper.assertTrue(!gridContents.canCraft(recipe.value(), null),
          "used rods already in the crafting grid must not satisfy native book counts");
      helper.assertTrue(displays(helper, recipe).stream().noneMatch(d -> d.canCraft(gridContents)),
          "the synchronized display must reject used rods counted from grid slots");
      player.awardRecipes(List.of(recipe));
      menu.handlePlacement(false, false, recipe, helper.getLevel(), player.getInventory());
      helper.assertTrue(menu.getResultSlot().getItem().isEmpty()
              && menu.getInputGridSlots().stream().noneMatch(Slot::hasItem),
          "an unavailable recipe must not place an invalid used-rod grid");
      assertUsedRodsRetained(helper, player, originals);
    }
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void allSixRecipesSkipEarlierUsedRodsAndPreserveThemAfterCraft(GameTestHelper helper) {
    for (RodRecipe specification : RECIPES) {
      ServerPlayer player = freshPlayer(helper);
      CraftingMenu menu = tableMenu(helper, player);
      RecipeHolder<CraftingRecipe> recipe = recipe(helper, specification);
      List<ItemStack> originals = new ArrayList<>();
      // Lower inventory indices deliberately hold invalid rods of the exact same item type.
      for (int index = 0; index < specification.rods(); index++) {
        ItemStack used = markedUsedRod(specification.rod(), index);
        originals.add(used.copy());
        player.getInventory().setItem(index, used);
      }
      provideMaterials(player, specification);
      for (int index = 0; index < specification.rods(); index++) {
        player.getInventory().add(withUse(specification.rod(), 0));
      }
      assertCraftable(helper, player, recipe, true);
      int selected = player.getInventory().findSlotMatchingCraftingIngredient(
          originals.getFirst().typeHolder(), ItemStack.EMPTY);
      helper.assertTrue(selected >= specification.rods(),
          "native source selection must skip all earlier invalid rods");
      placeAndTake(helper, player, menu, recipe, specification);
      assertUsedRodsRetained(helper, player, originals);
      helper.assertValueEqual(player.getInventory().getNonEquipmentItems().stream()
          .filter(s -> !s.isEmpty()).count(), (long) originals.size(),
          "only the original used rods may remain after one fresh-rod craft");
    }
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void bookRestrictionExactlyMatchesLoadedRodComponentConstraints(GameTestHelper helper) {
    for (String rod : List.of("uranium_fuel_rod", "dual_uranium_fuel_rod",
        "mox_fuel_rod", "dual_mox_fuel_rod")) {
      RodRecipe specification = RECIPES.stream().filter(r -> r.rod().equals(rod)).findFirst().orElseThrow();
      AdvRecipe actual = (AdvRecipe) recipe(helper, specification).value();
      RecipeInputItemStack required = Arrays.stream(actual.input)
          .filter(i -> i instanceof RecipeInputItemStack)
          .map(i -> (RecipeInputItemStack) i).findFirst().orElseThrow();
      ItemStack missingData = stack("ic2:" + rod);
      ItemStack zeroUse = withUse(rod, 0);
      ItemStack used = withUse(rod, 1);
      ItemStack byteZero = withUseTag(rod, ByteTag.valueOf((byte) 0));
      ItemStack shortZero = withUseTag(rod, ShortTag.valueOf((short) 0));
      ItemStack longZero = withUseTag(rod, LongTag.valueOf(0));
      ItemStack floatZero = withUseTag(rod, FloatTag.valueOf(0));
      ItemStack doubleZero = withUseTag(rod, DoubleTag.valueOf(0));
      ItemStack byteOne = withUseTag(rod, ByteTag.valueOf((byte) 1));
      ItemStack negativeUse = withUse(rod, -1);
      ItemStack fractionalUse = withUseTag(rod, DoubleTag.valueOf(0.25));
      ItemStack tinyNonzeroUse = withUseTag(rod, DoubleTag.valueOf(Double.MIN_VALUE));
      ItemStack emptyData = stack("ic2:" + rod);
      emptyData.set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));
      ItemStack wrongType = stack("ic2:" + rod);
      CompoundTag wrong = new CompoundTag();
      wrong.putString("use", "0");
      wrongType.set(DataComponents.CUSTOM_DATA, CustomData.of(wrong));
      ItemStack unrelatedData = stack("ic2:" + rod);
      CompoundTag unrelated = new CompoundTag();
      unrelated.putString("marker", "not a use counter");
      unrelatedData.set(DataComponents.CUSTOM_DATA, CustomData.of(unrelated));
      ItemStack additionalData = withUse(rod, 0);
      CompoundTag additional = additionalData.get(DataComponents.CUSTOM_DATA).copyTag();
      additional.putString("marker", "allowed extra data");
      additionalData.set(DataComponents.CUSTOM_DATA, CustomData.of(additional));
      List<ItemStack> variants = List.of(missingData, zeroUse, used, emptyData, wrongType,
          unrelatedData, additionalData, byteZero, shortZero, longZero, floatZero, doubleZero,
          byteOne, negativeUse, fractionalUse, tinyNonzeroUse);
      List<Boolean> expected = List.of(true, true, false, false, false, false, true,
          true, true, true, true, true, false, false, false, false);
      for (int index = 0; index < variants.size(); index++) {
        ItemStack variant = variants.get(index);
        helper.assertValueEqual(required.matches(variant), expected.get(index),
            "retain the actual loaded recipe's component matching policy, variant " + index);
        helper.assertValueEqual(RecipeBookStackRules.canAccountStack(variant), required.matches(variant),
            "book stack restriction must agree exactly with the existing matcher, variant " + index);
        helper.assertValueEqual(Inventory.isUsableForCrafting(variant), expected.get(index),
            "inventory selection must agree with the component policy, variant " + index);
        StackedItemContents contents = new StackedItemContents();
        for (ItemStack material : materialStacks(specification)) contents.accountStack(material);
        for (int count = 0; count < specification.rods(); count++) contents.accountStack(variant.copy());
        helper.assertValueEqual(contents.canCraft(actual, null), expected.get(index),
            "direct native stack accounting must agree with the component policy, variant " + index);
      }
    }
    helper.assertTrue(RecipeBookStackRules.canAccountStack(withUse("quad_uranium_fuel_rod", 1))
            && RecipeBookStackRules.canAccountStack(withUse("rsh_condensator", 1))
            && RecipeBookStackRules.canAccountStack(stack("minecraft:iron_ingot"))
            && RecipeBookStackRules.canAccountStack(ItemStack.EMPTY),
        "the book restriction must be limited to the four rod items used by fresh-rod recipes");
    helper.succeed();
  }

  private static void assertCraftable(GameTestHelper helper, ServerPlayer player,
      RecipeHolder<CraftingRecipe> recipe, boolean expected) {
    StackedItemContents contents = new StackedItemContents();
    player.getInventory().fillStackedContents(contents);
    helper.assertValueEqual(contents.canCraft(recipe.value(), null), expected,
        "native inventory craftability: " + recipe.id());
    List<RecipeDisplayEntry> displays = displays(helper, recipe);
    helper.assertTrue(!displays.isEmpty(), "fuel rod recipes must retain synchronized book displays");
    helper.assertValueEqual(displays.stream().anyMatch(d -> d.canCraft(contents)), expected,
        "native display craftability: " + recipe.id());
  }

  private static void placeAndTake(GameTestHelper helper, ServerPlayer player, CraftingMenu menu,
      RecipeHolder<CraftingRecipe> recipe, RodRecipe specification) {
    player.awardRecipes(List.of(recipe));
    menu.handlePlacement(false, false, recipe, helper.getLevel(), player.getInventory());
    CraftingInput input = CraftingInput.of(3, 3,
        menu.getInputGridSlots().stream().map(s -> s.getItem().copy()).toList());
    helper.assertTrue(recipe.value().matches(input, helper.getLevel()),
        "autofilled grid must satisfy the original component-aware matcher: " + recipe.id());
    helper.assertTrue(menu.getResultSlot().getItem().is(stack("ic2:" + specification.output()).getItem()),
        "native result must produce the requested rod: " + recipe.id());
    menu.clicked(0, 0, ContainerInput.PICKUP, player);
    helper.assertTrue(menu.getCarried().is(stack("ic2:" + specification.output()).getItem())
            && menu.getCarried().getCount() == 1,
        "native result pickup must craft exactly one requested rod: " + recipe.id());
    helper.assertTrue(menu.getInputGridSlots().stream().noneMatch(Slot::hasItem)
            && menu.getResultSlot().getItem().isEmpty(),
        "one ingredient set must be completely consumed by native result pickup");
  }

  private static void assertUsedRodsRetained(GameTestHelper helper, ServerPlayer player,
      List<ItemStack> originals) {
    for (ItemStack original : originals) {
      helper.assertValueEqual(player.getInventory().getNonEquipmentItems().stream()
          .filter(s -> ItemStack.isSameItemSameComponents(s, original))
          .mapToInt(ItemStack::getCount).sum(), original.getCount(),
          "book placement/crafting must retain every original used rod and its custom data");
    }
  }

  private static List<RecipeDisplayEntry> displays(GameTestHelper helper,
      RecipeHolder<CraftingRecipe> recipe) {
    List<RecipeDisplayEntry> result = new ArrayList<>();
    helper.getLevel().getServer().getRecipeManager().listDisplaysForRecipe(recipe.id(), result::add);
    return result;
  }

  private static List<ItemStack> materialStacks(RodRecipe specification) {
    List<ItemStack> result = new ArrayList<>();
    for (int count = 0; count < specification.iron(); count++) result.add(stack("ic2:iron_plate"));
    for (int count = 0; count < specification.copper(); count++) result.add(stack("ic2:copper_plate"));
    return result;
  }

  private static void provideMaterials(ServerPlayer player, RodRecipe specification) {
    for (ItemStack material : materialStacks(specification)) player.getInventory().add(material);
  }

  private static ItemStack markedUsedRod(String rod, int marker) {
    ItemStack stack = withUse(rod, 1);
    CompoundTag data = stack.get(DataComponents.CUSTOM_DATA).copyTag();
    data.putInt("recipe_book_retention_marker", marker);
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    return stack;
  }

  private static ItemStack withUse(String item, int use) {
    return withUseTag(item, IntTag.valueOf(use));
  }

  private static ItemStack withUseTag(String item, Tag use) {
    ItemStack stack = stack("ic2:" + item);
    CompoundTag data = new CompoundTag();
    data.put("use", use);
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    return stack;
  }

  private static ServerPlayer freshPlayer(GameTestHelper helper) {
    ServerPlayer player = helper.makeMockServerPlayerInLevel();
    player.setGameMode(GameType.SURVIVAL);
    player.getInventory().clearContent();
    return player;
  }

  private static CraftingMenu tableMenu(GameTestHelper helper, ServerPlayer player) {
    BlockPos table = new BlockPos(1, 1, 1);
    helper.setBlock(table, Blocks.CRAFTING_TABLE);
    CraftingMenu menu = new CraftingMenu(1, player.getInventory(),
        ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(table)));
    player.containerMenu = menu;
    return menu;
  }

  private static RecipeHolder<CraftingRecipe> recipe(GameTestHelper helper, RodRecipe specification) {
    var holder = helper.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(
        Registries.RECIPE, Identifier.parse("ic2:shaped/" + specification.recipe()))).orElseThrow();
    helper.assertTrue(holder.value() instanceof AdvRecipe, "must load an ordinary shaped fuel rod recipe");
    return new RecipeHolder<>(holder.id(), (CraftingRecipe) holder.value());
  }

  private static ItemStack stack(String id) {
    return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
  }

  private record RodRecipe(String recipe, String rod, String output, int rods, int iron, int copper) {}
}
