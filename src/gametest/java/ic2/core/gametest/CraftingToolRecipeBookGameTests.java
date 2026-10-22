package ic2.core.gametest;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Used IC2 crafting tools remain available to the native book without losing their components. */
public final class CraftingToolRecipeBookGameTests {
  private static final String EMPTY = "ic2:gametest/empty3x3x3";

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void wornHammerCountsAsCraftableAndCanBeSelectedFromInventory(GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    ItemStack hammer = markedTool("ic2:forge_hammer", 1);
    player.getInventory().setItem(0, hammer);
    player.getInventory().setItem(1, stack("minecraft:iron_ingot"));
    player.getInventory().setItem(2, stack("ic2:lead_ingot"));
    helper.assertTrue(Inventory.isUsableForCrafting(hammer),
        "a used forge hammer must remain a usable crafting ingredient");
    helper.assertValueEqual(
        player.getInventory().findSlotMatchingCraftingIngredient(hammer.typeHolder(), ItemStack.EMPTY),
        0, "native book inventory selection must find the used hammer");
    helper.assertValueEqual(
        player.getInventory().findSlotMatchingCraftingIngredient(hammer.typeHolder(), hammer.copy()),
        0, "native selection must retain an existing slot's component match");
    ItemStack differentHammer = hammer.copy();
    CompoundTag differentMarker = new CompoundTag();
    differentMarker.putString("ic2_recipe_book_test", "a different tool");
    differentHammer.set(DataComponents.CUSTOM_DATA, CustomData.of(differentMarker));
    helper.assertValueEqual(
        player.getInventory().findSlotMatchingCraftingIngredient(hammer.typeHolder(), differentHammer),
        -1, "native selection must not merge tools with different components");

    StackedItemContents contents = new StackedItemContents();
    player.getInventory().fillStackedContents(contents);
    helper.assertTrue(contents.canCraft(recipe(helper, "ic2:shapeless/iron_plate").value(), null),
        "the native craftable filter must include iron plate with a used hammer");
    helper.assertTrue(contents.canCraft(recipe(helper, "ic2:shapeless/lead_plate").value(), null),
        "the native craftable filter must include lead plate with a used hammer");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void sameWornHammerAutoFillsAndCraftsIronThenLeadPlate(GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    CraftingMenu menu = tableMenu(helper, player);
    ItemStack hammer = markedTool("ic2:forge_hammer", 17);
    // Custom data must survive native relocation and the reusable-tool remainder.
    helper.assertTrue(player.getInventory().add(hammer.copy()), "provide a used hammer");
    craft(helper, player, menu, "ic2:shapeless/iron_plate", "minecraft:iron_ingot",
        "ic2:iron_plate", 1, hammer);
    ItemStack afterIron = onlyTool(helper, player, menu, hammer);
    assertRetainedComponents(helper, hammer, afterIron, 18);
    menu.setCarried(ItemStack.EMPTY);
    craft(helper, player, menu, "ic2:shapeless/lead_plate", "ic2:lead_ingot",
        "ic2:lead_plate", 1, afterIron);
    assertRetainedComponents(helper, hammer, onlyTool(helper, player, menu, hammer), 19);
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void wornCutterAutoFillsAndRemainsReusableForWire(GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    CraftingMenu menu = tableMenu(helper, player);
    ItemStack cutter = markedTool("ic2:cutter", 9);
    helper.assertTrue(player.getInventory().add(cutter.copy()), "provide a used cutter");
    for (int expectedDamage = 10; expectedDamage <= 11; expectedDamage++) {
      craft(helper, player, menu, "ic2:shapeless/copper_cable", "ic2:copper_plate",
          "ic2:copper_cable", 2, onlyTool(helper, player, menu, cutter));
      assertRetainedComponents(helper, cutter, onlyTool(helper, player, menu, cutter), expectedDamage);
      menu.setCarried(ItemStack.EMPTY);
    }
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void finalHammerAndCutterUseCraftsOnceAndBreaksTheTool(GameTestHelper helper) {
    for (String toolId : List.of("ic2:forge_hammer", "ic2:cutter")) {
      ServerPlayer player = freshPlayer(helper);
      CraftingMenu menu = tableMenu(helper, player);
      ItemStack tool = stack(toolId);
      tool.setDamageValue(tool.getMaxDamage() - 1);
      helper.assertTrue(player.getInventory().add(tool.copy()), "provide last-use " + toolId);
      boolean hammer = toolId.equals("ic2:forge_hammer");
      craft(helper, player, menu,
          hammer ? "ic2:shapeless/iron_plate" : "ic2:shapeless/copper_cable",
          hammer ? "minecraft:iron_ingot" : "ic2:copper_plate",
          hammer ? "ic2:iron_plate" : "ic2:copper_cable", hammer ? 1 : 2, tool);
      helper.assertTrue(allInputs(player, menu).stream().noneMatch(s -> s.is(tool.getItem())),
          "the last use must break " + toolId + " rather than repair or duplicate it");
      helper.assertTrue(menu.getInputGridSlots().stream().noneMatch(Slot::hasItem)
              && player.getInventory().isEmpty(),
          "the final craft must consume its material and final-use tool");
    }
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void nativeFilteringStillProtectsOtherDamagedNamedAndEnchantedItems(GameTestHelper helper) {
    ItemStack pristine = new ItemStack(Items.IRON_PICKAXE);
    helper.assertTrue(Inventory.isUsableForCrafting(pristine), "retain native pristine-tool behavior");
    ItemStack damaged = pristine.copy();
    damaged.setDamageValue(1);
    helper.assertTrue(!Inventory.isUsableForCrafting(damaged),
        "a damaged vanilla tool must retain native recipe-book protection");
    ItemStack named = pristine.copy();
    named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep this pickaxe"));
    helper.assertTrue(!Inventory.isUsableForCrafting(named),
        "a named vanilla item must retain native recipe-book protection");
    ItemStack enchanted = pristine.copy();
    enchanted.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
        .getOrThrow(Enchantments.EFFICIENCY), 1);
    helper.assertTrue(!Inventory.isUsableForCrafting(enchanted),
        "an enchanted vanilla item must retain native recipe-book protection");
    helper.assertTrue(Inventory.isUsableForCrafting(markedTool("ic2:black_painter", 1)),
        "ordinary worn IC2 ingredients must remain available to the book");

    ServerPlayer player = freshPlayer(helper);
    player.getInventory().setItem(0, damaged);
    helper.assertValueEqual(player.getInventory().findSlotMatchingCraftingIngredient(
        damaged.typeHolder(), ItemStack.EMPTY), -1,
        "native autofill must still reject a damaged vanilla tool");

    for (String toolId : List.of("ic2:forge_hammer", "ic2:cutter")) {
      for (int damage : List.of(0, 1)) {
        ItemStack ordinaryTool = markedTool(toolId, damage);
        helper.assertTrue(Inventory.isUsableForCrafting(ordinaryTool),
            "an ordinary IC2 crafting tool must be usable both before and after its first use");
        ItemStack namedTool = ordinaryTool.copy();
        namedTool.set(DataComponents.CUSTOM_NAME, Component.literal("Keep this crafting tool"));
        helper.assertTrue(!Inventory.isUsableForCrafting(namedTool),
            "a named " + toolId + " must retain native protection at damage " + damage);
        ItemStack enchantedTool = ordinaryTool.copy();
        enchantedTool.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.UNBREAKING), 1);
        helper.assertTrue(!Inventory.isUsableForCrafting(enchantedTool),
            "an enchanted " + toolId + " must retain native protection at damage " + damage);
        for (ItemStack protectedTool : List.of(namedTool, enchantedTool)) {
          player.getInventory().clearContent();
          player.getInventory().setItem(0, protectedTool);
          player.getInventory().setItem(1, stack(toolId.equals("ic2:forge_hammer")
              ? "minecraft:iron_ingot" : "ic2:copper_plate"));
          helper.assertValueEqual(player.getInventory().findSlotMatchingCraftingIngredient(
              protectedTool.typeHolder(), ItemStack.EMPTY), -1,
              "native autofill must preserve named and enchanted crafting tools");
          StackedItemContents contents = new StackedItemContents();
          player.getInventory().fillStackedContents(contents);
          helper.assertTrue(!contents.canCraft(recipe(helper, toolId.equals("ic2:forge_hammer")
                  ? "ic2:shapeless/iron_plate" : "ic2:shapeless/copper_cable").value(), null),
              "the native craftable filter must not spend a named or enchanted crafting tool");
        }
      }
    }
    helper.succeed();
  }

  private static void craft(GameTestHelper helper, ServerPlayer player, CraftingMenu menu,
      String recipeId, String materialId, String outputId, int outputCount, ItemStack expectedTool) {
    helper.assertTrue(menu.getCarried().isEmpty(), "clear the previous result before another craft");
    helper.assertTrue(player.getInventory().add(stack(materialId)), "provide material " + materialId);
    RecipeHolder<CraftingRecipe> recipe = recipe(helper, recipeId);
    player.awardRecipes(List.of(recipe));
    menu.handlePlacement(false, false, recipe, helper.getLevel(), player.getInventory());
    helper.assertValueEqual(menu.getInputGridSlots().stream().filter(Slot::hasItem).count(), 2L,
        "native book must place the material and existing tool for " + recipeId);
    ItemStack placedTool = menu.getInputGridSlots().stream().map(Slot::getItem)
        .filter(s -> s.is(expectedTool.getItem())).findFirst().orElseThrow();
    helper.assertTrue(ItemStack.isSameItemSameComponents(placedTool, expectedTool),
        "native autofill must preserve tool damage and every component for " + recipeId);
    CraftingInput input = CraftingInput.of(menu.getGridWidth(), menu.getGridHeight(),
        menu.getInputGridSlots().stream().map(slot -> slot.getItem().copy()).toList());
    helper.assertTrue(recipe.value().matches(input, helper.getLevel()),
        "auto-filled grid must match the real loaded recipe " + recipeId);
    helper.assertTrue(menu.getResultSlot().getItem().is(stack(outputId).getItem())
            && menu.getResultSlot().getItem().getCount() == outputCount,
        "native result must contain " + outputCount + " " + outputId);
    menu.clicked(0, 0, ContainerInput.PICKUP, player);
    helper.assertTrue(menu.getCarried().is(stack(outputId).getItem())
            && menu.getCarried().getCount() == outputCount,
        "real result pickup must produce " + outputCount + " " + outputId);
    helper.assertTrue(allInputs(player, menu).stream().noneMatch(s -> s.is(stack(materialId).getItem())),
        "native result pickup must consume the provided material for " + recipeId);
    helper.assertTrue(menu.getResultSlot().getItem().isEmpty(),
        "the result must disappear when its single material is consumed");
  }

  private static ItemStack onlyTool(GameTestHelper helper, ServerPlayer player,
      CraftingMenu menu, ItemStack expectedTool) {
    List<ItemStack> tools = allInputs(player, menu).stream()
        .filter(s -> s.is(expectedTool.getItem())).toList();
    helper.assertTrue(tools.size() == 1 && tools.getFirst().getCount() == 1,
        "exactly one reusable tool must survive without duplication");
    return tools.getFirst().copy();
  }

  private static List<ItemStack> allInputs(ServerPlayer player, CraftingMenu menu) {
    List<ItemStack> stacks = new ArrayList<>();
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
      stacks.add(player.getInventory().getItem(slot));
    }
    menu.getInputGridSlots().forEach(slot -> stacks.add(slot.getItem()));
    return stacks;
  }

  private static void assertRetainedComponents(GameTestHelper helper, ItemStack original,
      ItemStack actual, int expectedDamage) {
    ItemStack expected = original.copy();
    expected.setDamageValue(expectedDamage);
    helper.assertTrue(ItemStack.isSameItemSameComponents(actual, expected),
        "crafting must change only durability, retaining the tool's custom data");
  }

  private static ItemStack markedTool(String id, int damage) {
    ItemStack tool = stack(id);
    tool.setDamageValue(damage);
    CompoundTag marker = new CompoundTag();
    marker.putString("ic2_recipe_book_test", "preserve existing tool data");
    marker.putInt("marker", 267);
    tool.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
    return tool;
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

  private static RecipeHolder<CraftingRecipe> recipe(GameTestHelper helper, String id) {
    var holder = helper.getLevel().getServer().getRecipeManager().byKey(
        ResourceKey.create(Registries.RECIPE, Identifier.parse(id))).orElseThrow();
    helper.assertTrue(holder.value() instanceof CraftingRecipe, "must load crafting recipe " + id);
    return new RecipeHolder<>(holder.id(), (CraftingRecipe) holder.value());
  }

  private static ItemStack stack(String id) {
    return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
  }
}
