package ic2.core.gametest;

import ic2.core.ref.Ic2Items;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

/** Recipe discovery through the real inventory listener, loaded advancements and server book. */
public final class RecipeBookGameTests {
  private static final String EMPTY = "ic2:gametest/empty3x3x3";

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void vanillaCopperDiscoversCopperPlateBeforeFirstCraft(GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    assertLocked(helper, player, "shapeless/copper_plate");

    receive(helper, player, new ItemStack(Items.COPPER_INGOT));

    assertDiscovered(helper, player, "shapeless/copper_plate");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void icIngredientDiscoversShapedEquipmentWithoutUnlockingUnrelatedRecipes(
      GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    assertLocked(helper, player, "shaped/advanced_batpack");
    assertLocked(helper, player, "shapeless/copper_plate");

    receive(helper, player, new ItemStack(Ic2Items.CIRCUIT));

    assertDiscovered(helper, player, "shaped/advanced_batpack");
    assertLocked(helper, player, "shapeless/copper_plate");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void rawLeadBlockDiscoversUnpackingWithoutLearningRawLeadSmelting(
      GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    assertLocked(helper, player, "shapeless/raw_ore/lead");
    assertLocked(helper, player, "smelting/lead_ingot_from_raw_lead");

    receive(helper, player, new ItemStack(Ic2Items.RAW_LEAD_BLOCK));

    assertDiscovered(helper, player, "shapeless/raw_ore/lead");
    assertLocked(helper, player, "smelting/lead_ingot_from_raw_lead");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void rawLeadDiscoversFurnaceRecipeBeforeFirstSmelt(GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    assertLocked(helper, player, "smelting/lead_ingot_from_raw_lead");

    receive(helper, player, new ItemStack(Ic2Items.RAW_LEAD));

    assertDiscovered(helper, player, "smelting/lead_ingot_from_raw_lead");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void acquiringInputsDoesNotDiscoverDynamicOrMachineRecipes(GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    String hidden = "shaped/jetpack_attachment";
    String machine = "macerator/raw_copper_to_crushed_copper";
    assertLocked(helper, player, hidden);
    assertLocked(helper, player, machine);

    receive(helper, player, new ItemStack(Ic2Items.TIN_INGOT));
    receive(helper, player, new ItemStack(Items.RAW_COPPER));

    assertLocked(helper, player, hidden);
    assertLocked(helper, player, machine);
    for (String path : List.of(hidden, machine)) {
      var recipe = helper.getLevel().getServer().getRecipeManager().byKey(recipeKey(path))
          .orElseThrow();
      helper.assertTrue(recipe.value().isSpecial(),
          "dynamic or machine recipe must retain its special recipe semantics: " + path);
      helper.assertTrue(recipe.value().display().isEmpty(),
          "dynamic or machine recipe must not acquire a crafting-book display: " + path);
    }
    helper.succeed();
  }

  private static ServerPlayer freshPlayer(GameTestHelper helper) {
    // The native helper joins a real ServerPlayer with a fresh random UUID and initializes its
    // inventory listener. Clear state explicitly so none of these checks can pass via saved data.
    ServerPlayer player = helper.makeMockServerPlayerInLevel();
    player.getInventory().clearContent();
    for (var recipe : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
      player.getRecipeBook().remove(recipe.id());
    }
    for (AdvancementHolder advancement :
        helper.getLevel().getServer().getAdvancements().getAllAdvancements()) {
      if (!advancement.id().getNamespace().equals("ic2")) continue;
      List<String> completed = new ArrayList<>();
      player.getAdvancements().getOrStartProgress(advancement).getCompletedCriteria()
          .forEach(completed::add);
      for (String criterion : completed) player.getAdvancements().revoke(advancement, criterion);
    }
    player.inventoryMenu.broadcastChanges();
    return player;
  }

  private static void receive(GameTestHelper helper, ServerPlayer player, ItemStack stack) {
    helper.assertTrue(player.getInventory().add(stack), "the fresh player must receive the input");
    // ServerPlayer's native ContainerListener fires INVENTORY_CHANGED for the changed inventory
    // slot. Do not manually grant criteria or recipes: that would bypass the behavior under test.
    player.inventoryMenu.broadcastChanges();
  }

  private static void assertLocked(GameTestHelper helper, ServerPlayer player, String path) {
    helper.assertFalse(player.getRecipeBook().contains(recipeKey(path)),
        "the recipe must not already be learned: ic2:" + path);
  }

  private static void assertDiscovered(GameTestHelper helper, ServerPlayer player, String path) {
    Identifier advancementId = Identifier.fromNamespaceAndPath("ic2", "recipes/" + path);
    AdvancementHolder advancement =
        helper.getLevel().getServer().getAdvancements().get(advancementId);
    helper.assertTrue(advancement != null,
        "the server must load the recipe discovery advancement: " + advancementId);
    helper.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone(),
        "receiving the ingredient must complete the discovery advancement: " + advancementId);
    helper.assertTrue(player.getRecipeBook().contains(recipeKey(path)),
        "the advancement must award the actual server recipe: ic2:" + path);
    List<RecipeDisplayEntry> displays = new ArrayList<>();
    helper.getLevel().getServer().getRecipeManager()
        .listDisplaysForRecipe(recipeKey(path), displays::add);
    helper.assertFalse(displays.isEmpty(),
        "the discovered recipe must have a native display to send to the client: ic2:" + path);
  }

  private static ResourceKey<Recipe<?>> recipeKey(String path) {
    return ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("ic2", path));
  }
}
