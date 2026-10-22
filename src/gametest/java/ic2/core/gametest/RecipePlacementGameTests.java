package ic2.core.gametest;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Native server recipe-book placement followed by a real result-slot pickup. */
public final class RecipePlacementGameTests {
  private static final String EMPTY = "ic2:gametest/empty3x3x3";

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void generatorBookPlacementBuildsVerticalCentreColumnAndCrafts(GameTestHelper helper) {
    tableCraft(helper, "ic2:shaped/generator_from_furnace", "ic2:generator", 1,
        null, "ic2:re_battery", null,
        null, "ic2:machine", null,
        null, "minecraft:furnace", null);
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void oneByTwoFrequencyTransmitterKeepsItsVerticalShape(GameTestHelper helper) {
    tableCraft(helper, "ic2:shaped/frequency_transmitter", "ic2:frequency_transmitter", 1,
        null, "ic2:insulated_copper_cable", null,
        null, "ic2:circuit", null,
        null, null, null);
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void twoByTwoCarbonFibreLeavesTheThirdColumnEmpty(GameTestHelper helper) {
    tableCraft(helper, "ic2:shaped/carbon_fibre", "ic2:carbon_fibre", 1,
        "ic2:coal_dust", "ic2:coal_dust", null,
        "ic2:coal_dust", "ic2:coal_dust", null,
        null, null, null);
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void twoByThreeAxeKeepsItsTrailingHoleAndRowStride(GameTestHelper helper) {
    tableCraft(helper, "ic2:shaped/bronze_axe", "ic2:bronze_axe", 1,
        "ic2:bronze_ingot", "ic2:bronze_ingot", null,
        "minecraft:stick", "ic2:bronze_ingot", null,
        "minecraft:stick", null, null);
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void threeByThreeTreetapPreservesInternalEmptySlots(GameTestHelper helper) {
    tableCraft(helper, "ic2:shaped/treetap", "ic2:treetap", 1,
        null, "minecraft:oak_planks", null,
        "minecraft:oak_planks", "minecraft:oak_planks", "minecraft:oak_planks",
        "minecraft:oak_planks", null, null);
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void narrowSingleRowsUseVanillaVerticalCentring(GameTestHelper helper) {
    tableCraft(helper, "ic2:shaped/rubber_pressure_plate", "ic2:rubber_pressure_plate", 1,
        null, null, null,
        "ic2:rubber_planks", "ic2:rubber_planks", null,
        null, null, null);
    tableCraft(helper, "ic2:shaped/iron_ingot_from_machine", "minecraft:iron_ingot", 8,
        null, null, null,
        null, "ic2:machine", null,
        null, null, null);
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void inventoryTwoByTwoBookAlsoPreservesNarrowIcShape(GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    craft(helper, player, player.inventoryMenu, "ic2:shaped/frequency_transmitter",
        "ic2:frequency_transmitter", 1,
        "ic2:insulated_copper_cable", null,
        "ic2:circuit", null);
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void vanillaShapedAndShapelessBookPlacementStillCraft(GameTestHelper helper) {
    tableCraft(helper, "minecraft:oak_stairs", "minecraft:oak_stairs", 4,
        "minecraft:oak_planks", null, null,
        "minecraft:oak_planks", "minecraft:oak_planks", null,
        "minecraft:oak_planks", "minecraft:oak_planks", "minecraft:oak_planks");
    // Distinct shapeless ingredients may be assigned to the first row in any order.
    ServerPlayer player = freshPlayer(helper);
    CraftingMenu menu = tableMenu(helper, player);
    RecipeHolder<CraftingRecipe> recipe = recipe(helper, "minecraft:fire_charge");
    for (String item : List.of("minecraft:coal", "minecraft:blaze_powder", "minecraft:gunpowder")) {
      helper.assertTrue(player.getInventory().add(stack(item)), "could not provide " + item);
    }
    player.awardRecipes(List.of(recipe));
    menu.handlePlacement(false, false, recipe, helper.getLevel(), player.getInventory());
    helper.assertValueEqual(menu.getInputGridSlots().stream().filter(Slot::hasItem).count(), 3L,
        "vanilla shapeless input count");
    for (int slot = 3; slot < 9; slot++) {
      helper.assertTrue(menu.getInputGridSlots().get(slot).getItem().isEmpty(),
          "vanilla shapeless placement must retain its native first-row arrangement");
    }
    assertMatchesAndTake(helper, player, menu, recipe, "minecraft:fire_charge", 3);
    helper.succeed();
  }

  private static void tableCraft(GameTestHelper helper, String recipeId, String output, int count,
      String... grid) {
    ServerPlayer player = freshPlayer(helper);
    craft(helper, player, tableMenu(helper, player), recipeId, output, count, grid);
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

  private static void craft(GameTestHelper helper, ServerPlayer player, AbstractCraftingMenu menu,
      String recipeId, String output, int count, String... grid) {
    helper.assertValueEqual(grid.length, menu.getInputGridSlots().size(), "expected grid size");
    RecipeHolder<CraftingRecipe> recipe = recipe(helper, recipeId);
    for (String ingredient : grid) {
      if (ingredient != null) {
        helper.assertTrue(player.getInventory().add(stack(ingredient)),
            "could not provide ingredient for " + recipeId + ": " + ingredient);
      }
    }
    player.awardRecipes(List.of(recipe));
    // This is the same server entry point invoked by the recipe-book placement packet. The test
    // supplies inventory only: it never constructs or writes the expected crafting grid itself.
    menu.handlePlacement(false, false, recipe, helper.getLevel(), player.getInventory());
    for (int index = 0; index < grid.length; index++) {
      ItemStack actual = menu.getInputGridSlots().get(index).getItem();
      if (grid[index] == null) {
        helper.assertTrue(actual.isEmpty(), recipeId + " must leave grid cell " + index + " empty");
      } else {
        helper.assertTrue(actual.is(stack(grid[index]).getItem()) && actual.getCount() == 1,
            recipeId + " must place one " + grid[index] + " in grid cell " + index
                + ", got " + actual);
      }
    }
    assertMatchesAndTake(helper, player, menu, recipe, output, count);
  }

  private static void assertMatchesAndTake(GameTestHelper helper, ServerPlayer player,
      AbstractCraftingMenu menu, RecipeHolder<CraftingRecipe> recipe, String output, int count) {
    CraftingInput input = CraftingInput.of(menu.getGridWidth(), menu.getGridHeight(),
        menu.getInputGridSlots().stream().map(slot -> slot.getItem().copy()).toList());
    helper.assertTrue(recipe.value().matches(input, helper.getLevel()),
        "the auto-filled grid must match the actual loaded recipe " + recipe.id());
    helper.assertTrue(menu.getResultSlot().getItem().is(stack(output).getItem())
            && menu.getResultSlot().getItem().getCount() == count,
        "native crafting result must contain " + count + " " + output + " for " + recipe.id());
    // Use the native click path so ResultSlot.onTake consumes inputs and performs the real craft.
    menu.clicked(0, 0, ContainerInput.PICKUP, player);
    helper.assertTrue(menu.getCarried().is(stack(output).getItem()) && menu.getCarried().getCount() == count,
        "native result pickup must produce " + count + " " + output + " for " + recipe.id());
    helper.assertTrue(menu.getInputGridSlots().stream().noneMatch(Slot::hasItem),
        "crafting must consume every provided ingredient for " + recipe.id());
    helper.assertTrue(player.getInventory().isEmpty(),
        "provided ingredients must be consumed rather than left in inventory for " + recipe.id());
    helper.assertTrue(menu.getResultSlot().getItem().isEmpty(),
        "the result must disappear after its only ingredient set is consumed for " + recipe.id());
  }

  private static RecipeHolder<CraftingRecipe> recipe(GameTestHelper helper, String id) {
    var holder = helper.getLevel().getServer().getRecipeManager().byKey(
        ResourceKey.create(Registries.RECIPE, Identifier.parse(id))).orElseThrow();
    helper.assertTrue(holder.value() instanceof CraftingRecipe, "must load a crafting recipe: " + id);
    return new RecipeHolder<>(holder.id(), (CraftingRecipe) holder.value());
  }

  private static ItemStack stack(String id) {
    return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
  }
}
