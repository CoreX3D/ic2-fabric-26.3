package ic2.core.gametest;

import com.google.gson.GsonBuilder;
import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItem;
import ic2.api.recipe.IRecipeInput;
import ic2.core.recipe.AdvRecipe;
import ic2.core.recipe.AdvShapelessRecipe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Exhaustive audit of loaded, ordinary IC2 recipes through the actual native recipe book. */
public final class AllRecipeBookGameTests {
  private static final String EMPTY = "ic2:gametest/empty3x3x3";
  private static final int CHUNKS = 8;
  private static final int WORN_CHUNKS = 4;

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void allCraftingRecipesPartOne(GameTestHelper helper) { craftChunk(helper, 0); }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void allCraftingRecipesPartTwo(GameTestHelper helper) { craftChunk(helper, 1); }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void allCraftingRecipesPartThree(GameTestHelper helper) { craftChunk(helper, 2); }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void allCraftingRecipesPartFour(GameTestHelper helper) { craftChunk(helper, 3); }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void allCraftingRecipesPartFive(GameTestHelper helper) { craftChunk(helper, 4); }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void allCraftingRecipesPartSix(GameTestHelper helper) { craftChunk(helper, 5); }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void allCraftingRecipesPartSeven(GameTestHelper helper) { craftChunk(helper, 6); }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void allCraftingRecipesPartEight(GameTestHelper helper) { craftChunk(helper, 7); }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void everyMatchingWornIcIngredientPartOne(GameTestHelper helper) {
    craftChunk(helper, 0, WORN_CHUNKS, true);
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void everyMatchingWornIcIngredientPartTwo(GameTestHelper helper) {
    craftChunk(helper, 1, WORN_CHUNKS, true);
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void everyMatchingWornIcIngredientPartThree(GameTestHelper helper) {
    craftChunk(helper, 2, WORN_CHUNKS, true);
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void everyMatchingWornIcIngredientPartFour(GameTestHelper helper) {
    craftChunk(helper, 3, WORN_CHUNKS, true);
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void everyOrdinaryRecipeNaturallyUnlocksAndHasNativeDisplay(GameTestHelper helper) {
    List<RecipeHolder<?>> recipes = ordinaryRecipes(helper);
    ServerPlayer player = freshPlayer(helper);
    List<Map<String, Object>> results = new ArrayList<>();
    List<String> failures = new ArrayList<>();
    for (RecipeHolder<?> holder : recipes) {
      String id = holder.id().identifier().toString();
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("recipe", id);
      List<String> errors = new ArrayList<>();
      row.put("failures", errors);
      try {
        player.getInventory().clearContent();
        player.inventoryMenu.broadcastChanges();
        // Reset only this target; other recipe awards cannot satisfy its assertion.
        List<AdvancementHolder> advancements = helper.getLevel().getServer().getAdvancements()
            .getAllAdvancements().stream()
            .filter(a -> a.value().rewards().recipes().contains(holder.id())).toList();
        row.put("discovery_advancements", advancements.stream().map(a -> a.id().toString()).toList());
        require(!advancements.isEmpty(), "missing advancement awarding target recipe", errors);
        for (AdvancementHolder advancement : advancements) {
          List<String> completed = new ArrayList<>();
          player.getAdvancements().getOrStartProgress(advancement).getCompletedCriteria()
              .forEach(completed::add);
          for (String criterion : completed) player.getAdvancements().revoke(advancement, criterion);
        }
        player.getRecipeBook().remove(holder.id());
        require(!player.getRecipeBook().contains(holder.id()), "target was not reset", errors);
        List<ItemStack> samples = samples(holder);
        row.put("received_inputs", samples.stream().map(AllRecipeBookGameTests::describe).toList());
        for (ItemStack sample : samples) {
          require(player.getInventory().add(sample.copy()), "could not receive " + sample, errors);
          // This fires the real ServerPlayer inventory listener and INVENTORY_CHANGED trigger.
          // Deliberately do not award recipes or criteria directly in this discovery test.
          player.inventoryMenu.broadcastChanges();
          if (player.getRecipeBook().contains(holder.id())) break;
        }
        require(player.getRecipeBook().contains(holder.id()), "inventory did not unlock recipe", errors);
        require(advancements.stream().anyMatch(a ->
                player.getAdvancements().getOrStartProgress(a).isDone()),
            "inventory did not complete any target discovery advancement", errors);
        List<RecipeDisplayEntry> displays = displays(helper, holder);
        row.put("display_count", displays.size());
        require(!displays.isEmpty(), "no synchronized native recipe display", errors);
        require(!holder.value().display().isEmpty(), "recipe has no native display", errors);
        require(!holder.value().placementInfo().isImpossibleToPlace(),
            "ordinary recipe marked impossible to place", errors);
        if (holder.value() instanceof AbstractCookingRecipe cooking) {
          ItemStack source = samples.getFirst();
          SingleRecipeInput input = new SingleRecipeInput(source);
          require(cooking.matches(input, helper.getLevel()), "smelting sample does not match", errors);
          require(!cooking.assemble(input).isEmpty(), "smelting output is empty", errors);
          row.put("smelting_output", describe(cooking.assemble(input)));
        }
      } catch (Exception | AssertionError failure) {
        errors.add(failure.getClass().getSimpleName() + ": " + failure.getMessage());
      }
      if (!errors.isEmpty()) failures.add(id + " => " + String.join("; ", errors));
      results.add(row);
    }
    Map<String, Object> report = report("natural_inventory_discovery_and_displays", results, failures);
    report.put("ordinary_recipe_count", recipes.size());
    report.put("crafting_count", recipes.stream().filter(r -> r.value() instanceof CraftingRecipe).count());
    report.put("smelting_count", recipes.stream().filter(r -> r.value() instanceof AbstractCookingRecipe).count());
    writeReport("runtime-discovery.json", report);
    helper.assertValueEqual(recipes.size(), 449, "all ordinary IC2 recipes must be audited");
    helper.assertTrue(failures.isEmpty(), "Ordinary recipe discovery failures: " + failures);
    helper.succeed();
  }

  private static void craftChunk(GameTestHelper helper, int chunk) {
    craftChunk(helper, chunk, CHUNKS, false);
  }

  private static void craftChunk(GameTestHelper helper, int chunk, int partitions, boolean worn) {
    List<RecipeHolder<?>> allRecipes = ordinaryRecipes(helper).stream()
        .filter(r -> r.value() instanceof CraftingRecipe).toList();
    List<Map<String, Object>> excludedWornCases = new ArrayList<>();
    List<RecipeHolder<?>> recipes = worn
        ? matchingWornRecipes(helper, allRecipes, excludedWornCases) : allRecipes;
    ServerPlayer player = freshPlayer(helper);
    BlockPos table = new BlockPos(1, 1, 1);
    helper.setBlock(table, Blocks.CRAFTING_TABLE);
    CraftingMenu menu = new CraftingMenu(1, player.getInventory(),
        ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(table)));
    player.containerMenu = menu;
    List<Map<String, Object>> results = new ArrayList<>();
    List<String> failures = new ArrayList<>();
    for (int index = chunk; index < recipes.size(); index += partitions) {
      RecipeHolder<?> holder = recipes.get(index);
      Map<String, Object> row = new LinkedHashMap<>();
      String id = holder.id().identifier().toString();
      row.put("recipe", id);
      List<String> errors = new ArrayList<>();
      row.put("failures", errors);
      try {
        clear(player, menu);
        CraftingRecipe recipe = (CraftingRecipe) holder.value();
        List<ItemStack> samples = samples(holder);
        if (worn) damageIcIngredients(samples);
        row.put("inventory_inputs", samples.stream().map(AllRecipeBookGameTests::describe).toList());
        CraftingInput referenceInput = referenceInput(recipe, samples);
        require(recipe.matches(referenceInput, helper.getLevel()),
            "representative input does not match loaded recipe", errors);
        ItemStack expected = recipe.assemble(referenceInput);
        require(!expected.isEmpty(), "representative assembly produced no output", errors);
        row.put("expected_output", describe(expected));
        for (ItemStack sample : samples) {
          require(player.getInventory().add(sample.copy()), "inventory rejected " + sample, errors);
        }
        row.put("native_unusable_inputs", samples.stream()
            .filter(s -> !Inventory.isUsableForCrafting(s))
            .map(AllRecipeBookGameTests::describe).toList());
        StackedItemContents contents = new StackedItemContents();
        player.getInventory().fillStackedContents(contents);
        boolean craftable = contents.canCraft(recipe, null);
        row.put("native_can_craft", craftable);
        require(craftable, "native inventory craftability excludes valid ingredients", errors);
        List<RecipeDisplayEntry> displays = displays(helper, holder);
        row.put("display_count", displays.size());
        require(!displays.isEmpty(), "no synchronized native display", errors);
        require(displays.stream().anyMatch(d -> d.canCraft(contents)),
            "native display craftable filter excludes recipe", errors);
        // Granting here isolates placement from discovery, which is checked separately above.
        player.awardRecipes(List.of(holder));
        menu.handlePlacement(false, false, holder, helper.getLevel(), player.getInventory());
        CraftingInput placed = CraftingInput.of(menu.getGridWidth(), menu.getGridHeight(),
            menu.getInputGridSlots().stream().map(s -> s.getItem().copy()).toList());
        row.put("placed_grid", menu.getInputGridSlots().stream()
            .map(s -> describe(s.getItem())).toList());
        boolean matches = recipe.matches(placed, helper.getLevel());
        require(matches, "native book auto-filled grid does not match recipe", errors);
        ItemStack result = menu.getResultSlot().getItem().copy();
        row.put("native_result", describe(result));
        require(sameStack(result, expected), "native result differs from loaded recipe assembly", errors);
        if (matches && !result.isEmpty()) {
          List<ItemStack> remaining = recipe.getRemainingItems(placed).stream()
              .filter(s -> !s.isEmpty()).map(ItemStack::copy).toList();
          menu.clicked(0, 0, ContainerInput.PICKUP, player);
          require(sameStack(menu.getCarried(), expected), "native result pickup has wrong output", errors);
          List<ItemStack> actualRemainders = allInputs(player, menu);
          require(sameMultiset(actualRemainders, remaining),
              "native craft did not consume exactly one ingredient set and retain exact remainders", errors);
          row.put("retained_remainders", actualRemainders.stream()
              .map(AllRecipeBookGameTests::describe).toList());
          if (expected.getItem() instanceof IElectricItem) {
            double expectedCharge = ElectricItem.manager.getCharge(expected);
            double actualCharge = ElectricItem.manager.getCharge(menu.getCarried());
            row.put("output_charge", actualCharge);
            require(Double.compare(actualCharge, expectedCharge) == 0,
                "native result pickup changed electric charge", errors);
          }
        }
      } catch (Exception | AssertionError failure) {
        errors.add(failure.getClass().getSimpleName() + ": " + failure.getMessage());
      }
      if (!errors.isEmpty()) failures.add(id + " => " + String.join("; ", errors));
      results.add(row);
    }
    clear(player, menu);
    Map<String, Object> report = report(worn
        ? "native_crafting_with_every_matching_worn_ic_ingredient"
        : "native_crafting_placement_and_pickup", results, failures);
    report.put("partition", chunk + 1);
    report.put("partition_count", partitions);
    report.put("total_loaded_ordinary_crafting_recipes", allRecipes.size());
    if (worn) {
      report.put("matching_worn_recipe_count", recipes.size());
      report.put("excluded_worn_cases", excludedWornCases);
    }
    writeReport("runtime-" + (worn ? "worn-" : "crafting-") + (chunk + 1) + ".json", report);
    helper.assertValueEqual(allRecipes.size(), 414, "all ordinary IC2 crafting recipes must be audited");
    if (worn) helper.assertTrue(!recipes.isEmpty(), "worn IC ingredient matrix must contain cases");
    helper.assertTrue(failures.isEmpty(), "Native crafting book failures: " + failures);
    helper.succeed();
  }

  private static List<RecipeHolder<?>> matchingWornRecipes(GameTestHelper helper,
      List<RecipeHolder<?>> recipes, List<Map<String, Object>> excluded) {
    List<RecipeHolder<?>> matched = new ArrayList<>();
    for (RecipeHolder<?> holder : recipes) {
      List<ItemStack> candidates = samples(holder);
      if (!damageIcIngredients(candidates)) continue;
      CraftingRecipe recipe = (CraftingRecipe) holder.value();
      if (recipe.matches(referenceInput(recipe, candidates), helper.getLevel())) {
        matched.add(holder);
      } else {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("recipe", holder.id().identifier().toString());
        row.put("reason", "loaded recipe rejects this worn component state even when placed manually");
        row.put("inputs", candidates.stream().map(AllRecipeBookGameTests::describe).toList());
        excluded.add(row);
      }
    }
    return matched;
  }

  private static boolean damageIcIngredients(List<ItemStack> samples) {
    boolean changed = false;
    for (ItemStack stack : samples) {
      // Electric gauges already represent a genuinely crafted empty item in prepare(). This
      // second matrix covers every other native damageable IC item, including material-backed
      // armor such as rubber boots whose damage component is supplied by the native material.
      if (!(stack.getItem() instanceof IElectricItem) && stack.getMaxDamage() > 0
          && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("ic2")) {
        stack.setDamageValue(Math.max(1, stack.getMaxDamage() / 2));
        changed = true;
      }
    }
    return changed;
  }

  private static List<RecipeHolder<?>> ordinaryRecipes(GameTestHelper helper) {
    return helper.getLevel().getServer().getRecipeManager().getRecipes().stream()
        .filter(r -> r.id().identifier().getNamespace().equals("ic2") && !r.value().isSpecial())
        .filter(r -> r.value() instanceof CraftingRecipe || r.value() instanceof AbstractCookingRecipe)
        .sorted(Comparator.comparing(r -> r.id().identifier().toString())).toList();
  }

  private static List<ItemStack> samples(RecipeHolder<?> holder) {
    if (holder.value() instanceof AdvRecipe shaped) {
      return Arrays.stream(shaped.input).map(AllRecipeBookGameTests::sample).toList();
    }
    if (holder.value() instanceof AdvShapelessRecipe shapeless) {
      return Arrays.stream(shapeless.input).map(AllRecipeBookGameTests::sample).toList();
    }
    return holder.value().placementInfo().ingredients().stream()
        .map(AllRecipeBookGameTests::sample).toList();
  }

  private static ItemStack sample(IRecipeInput input) {
    // Prefer a real item-only ingredient candidate, retaining constrained component stacks when
    // needed. Fluid getInputs() may enumerate mutable cells; native book uses dedicated buckets.
    List<ItemStack> candidates = new ArrayList<>();
    input.getIngredient().items().map(h -> new ItemStack(h.value())).forEach(candidates::add);
    candidates.addAll(input.getInputs().stream().map(ItemStack::copy).toList());
    for (ItemStack candidate : candidates) {
      ItemStack actual = prepare(candidate);
      if (input.matches(actual)) return actual;
    }
    throw new IllegalStateException("No real matching input candidate for " + input);
  }

  private static ItemStack sample(Ingredient input) {
    return input.items().map(h -> prepare(new ItemStack(h.value()))).filter(input::test)
        .findFirst().orElseThrow(() -> new IllegalStateException("Empty loaded native ingredient"));
  }

  private static ItemStack prepare(ItemStack stack) {
    ItemStack actual = stack.copyWithCount(1);
    if (actual.getItem() instanceof IElectricItem) {
      // Crafting any electric item initializes its empty-charge gauge. A raw new ItemStack has
      // damage zero and would miss the exact book bug seen with crafted night-vision goggles.
      ElectricItem.manager.charge(actual, 0, Integer.MAX_VALUE, true, false);
    }
    return actual;
  }

  private static CraftingInput referenceInput(CraftingRecipe recipe, List<ItemStack> samples) {
    List<ItemStack> grid = new ArrayList<>();
    for (int i = 0; i < 9; i++) grid.add(ItemStack.EMPTY);
    if (recipe instanceof AdvRecipe shaped) {
      int sample = 0;
      var slots = shaped.getGridIngredients();
      for (int y = 0; y < shaped.inputHeight; y++) {
        for (int x = 0; x < shaped.inputWidth; x++) {
          if (slots.get(x + y * shaped.inputWidth).isPresent()) {
            grid.set(x + y * 3, samples.get(sample++).copy());
          }
        }
      }
    } else {
      for (int i = 0; i < samples.size(); i++) grid.set(i, samples.get(i).copy());
    }
    return CraftingInput.of(3, 3, grid);
  }

  private static List<RecipeDisplayEntry> displays(GameTestHelper helper, RecipeHolder<?> holder) {
    List<RecipeDisplayEntry> displays = new ArrayList<>();
    helper.getLevel().getServer().getRecipeManager().listDisplaysForRecipe(holder.id(), displays::add);
    return displays;
  }

  private static ServerPlayer freshPlayer(GameTestHelper helper) {
    ServerPlayer player = helper.makeMockServerPlayerInLevel();
    player.setGameMode(GameType.SURVIVAL);
    player.getInventory().clearContent();
    return player;
  }

  private static void clear(ServerPlayer player, CraftingMenu menu) {
    menu.setCarried(ItemStack.EMPTY);
    menu.getInputGridSlots().forEach(s -> s.set(ItemStack.EMPTY));
    menu.getResultSlot().set(ItemStack.EMPTY);
    player.getInventory().clearContent();
  }

  private static List<ItemStack> allInputs(ServerPlayer player, CraftingMenu menu) {
    List<ItemStack> stacks = new ArrayList<>();
    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
      ItemStack stack = player.getInventory().getItem(i);
      if (!stack.isEmpty()) stacks.add(stack.copy());
    }
    menu.getInputGridSlots().stream().map(Slot::getItem).filter(s -> !s.isEmpty())
        .map(ItemStack::copy).forEach(stacks::add);
    return stacks;
  }

  private static boolean sameStack(ItemStack actual, ItemStack expected) {
    return actual.getCount() == expected.getCount()
        && ItemStack.isSameItemSameComponents(actual, expected);
  }

  private static boolean sameMultiset(List<ItemStack> actual, List<ItemStack> expected) {
    List<ItemStack> unmatched = expected.stream().map(ItemStack::copy)
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    for (ItemStack stack : actual) {
      int count = stack.getCount();
      for (ItemStack candidate : unmatched) {
        if (ItemStack.isSameItemSameComponents(stack, candidate)) {
          int matched = Math.min(count, candidate.getCount());
          count -= matched;
          candidate.shrink(matched);
        }
      }
      if (count != 0) return false;
    }
    return unmatched.stream().allMatch(ItemStack::isEmpty);
  }

  private static Map<String, Object> describe(ItemStack stack) {
    Map<String, Object> description = new LinkedHashMap<>();
    description.put("item", stack.isEmpty() ? "minecraft:air"
        : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    description.put("count", stack.getCount());
    if (!stack.isEmpty() && stack.getMaxDamage() > 0) description.put("damage", stack.getDamageValue());
    if (stack.getItem() instanceof IElectricItem) {
      description.put("charge", ElectricItem.manager.getCharge(stack));
    }
    return description;
  }

  private static void require(boolean condition, String message, List<String> errors) {
    if (!condition) errors.add(message);
  }

  private static Map<String, Object> report(String audit, List<Map<String, Object>> rows,
      List<String> failures) {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("audit", audit);
    report.put("minecraft", "26.3");
    report.put("checked_count", rows.size());
    report.put("failure_count", failures.size());
    report.put("failures", failures);
    report.put("recipes", rows);
    return report;
  }

  private static void writeReport(String filename, Map<String, Object> report) {
    Path project = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (project != null && !Files.isRegularFile(project.resolve("build.gradle"))) {
      project = project.getParent();
    }
    if (project == null) throw new IllegalStateException("Cannot locate isolated IC2 test project");
    Path output = project.resolve("build/all-book-audit").resolve(filename);
    try {
      Files.createDirectories(output.getParent());
      Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot save complete recipe-book audit " + output, failure);
    }
  }
}
