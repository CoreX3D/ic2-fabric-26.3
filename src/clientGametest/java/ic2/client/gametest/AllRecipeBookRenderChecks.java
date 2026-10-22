package ic2.client.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItem;
import ic2.api.recipe.IRecipeInput;
import ic2.core.recipe.AdvRecipe;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Real crafted goggles, native Russian book clicks, and every ordinary IC2 display after transport. */
@Environment(EnvType.CLIENT)
@SuppressWarnings("UnstableApiUsage")
public final class AllRecipeBookRenderChecks {
  private static final BlockPos TABLE = new BlockPos(-36, 64, 48);
  private static final ResourceKey<Recipe<?>> NANO = recipe("nano_helmet");
  private static final Identifier ADVANCEMENT = Identifier.parse("ic2:recipes/shaped/nano_helmet");

  private AllRecipeBookRenderChecks() {}

  public static List<String> validate(ClientGameTestContext context, TestServerContext server,
      TestServerConnection connection) {
    Path directory = context.computeOnClient(client -> client.gameDirectory.toPath().toAbsolutePath().normalize());
    require(directory.endsWith(Path.of("build", "client-render-smoke")),
        "All-recipe book fixtures are permitted only in build/client-render-smoke");
    SavedPlayer original = server.computeOnServer(instance -> {
      var player = connection.getServerPlayer();
      require(player.containerMenu == player.inventoryMenu, "A preceding test menu is still open");
      return new SavedPlayer(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
          player.gameMode.getGameModeForPlayer());
    });
    List<String> evidence = new ArrayList<>();
    boolean created = false;
    try {
      server.runOnServer(instance -> {
        var level = connection.getServerLevel();
        require(level.isEmptyBlock(TABLE), "Nano helmet fixture would overwrite another scene");
        for (BlockPos floor : platform()) require(level.isEmptyBlock(floor),
            "Nano helmet platform would overwrite another scene at " + floor);
      });
      created = true;
      server.runOnServer(instance -> {
        var level = connection.getServerLevel();
        var player = connection.getServerPlayer();
        for (BlockPos floor : platform()) level.setBlock(floor, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(TABLE, Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_ALL);
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(level, TABLE.getX() + 0.5, TABLE.getY(), TABLE.getZ() + 2.5,
            Set.of(), 180.0f, 0.0f, true);
        player.getInventory().clearContent();
        player.inventoryMenu.broadcastChanges();
      });
      context.waitTicks(2);
      connection.waitForClientboundPackets();
      craftNanoHelmet(context, server, connection, false, evidence);
      craftNanoHelmet(context, server, connection, true, evidence);
      validateEveryDisplay(context, server, connection, evidence);
      return evidence;
    } finally {
      context.runOnClient(client -> { if (client.player != null) client.player.closeContainer(); });
      connection.waitForServerboundPackets();
      boolean removeScene = created;
      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        player.closeContainer();
        player.getInventory().clearContent();
        if (removeScene) {
          connection.getServerLevel().setBlock(TABLE, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
          for (BlockPos floor : platform()) {
            connection.getServerLevel().setBlock(floor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
          }
        }
        player.setGameMode(original.mode());
        player.teleportTo(connection.getServerLevel(), original.x(), original.y(), original.z(), Set.of(),
            original.yaw(), original.pitch(), true);
      });
      context.waitTick();
      connection.waitForClientboundPackets();
    }
  }

  private static void craftNanoHelmet(ClientGameTestContext context, TestServerContext server,
      TestServerConnection connection, boolean partlyCharged, List<String> evidence) {
    Item helmet = item("ic2:nano_helmet");
    Item gogglesItem = item("ic2:night_vision_goggles");
    Item carbon = item("ic2:carbon_plate");
    Item crystal = item("ic2:energy_crystal");
    server.runOnServer(instance -> {
      var player = connection.getServerPlayer();
      require(player.containerMenu == player.inventoryMenu, "Nano helmet fixture requires a closed table");
      player.getInventory().clearContent();
      player.inventoryMenu.broadcastChanges();
      var advancement = instance.getAdvancements().get(ADVANCEMENT);
      require(advancement != null, "Nano helmet natural discovery advancement is missing");
      List<String> completed = new ArrayList<>();
      player.getAdvancements().getOrStartProgress(advancement).getCompletedCriteria().forEach(completed::add);
      for (String criterion : completed) player.getAdvancements().revoke(advancement, criterion);
      player.getRecipeBook().removeRecipes(List.of(instance.getRecipeManager().byKey(NANO).orElseThrow()), player);
      require(!player.getRecipeBook().contains(NANO), "Nano helmet was already discovered before inventory gain");
    });
    connection.waitForClientboundPackets();
    context.waitTick();
    double expectedCharge = server.computeOnServer(instance -> {
      var player = connection.getServerPlayer();
      AdvRecipe gogglesRecipe = (AdvRecipe) instance.getRecipeManager().byKey(recipe("night_vision_goggles"))
          .orElseThrow().value();
      List<ItemStack> grid = new ArrayList<>();
      int nextIngredient = 0;
      for (var slot : gogglesRecipe.getGridIngredients()) {
        grid.add(slot.isPresent() ? actualIngredient(gogglesRecipe.input[nextIngredient++]) : ItemStack.EMPTY);
      }
      CraftingInput input = CraftingInput.of(gogglesRecipe.inputWidth, gogglesRecipe.inputHeight, grid);
      require(gogglesRecipe.matches(input, connection.getServerLevel()), "Real goggles ingredients did not match");
      ItemStack goggles = gogglesRecipe.assemble(input);
      require(goggles.is(gogglesItem) && goggles.getCount() == 1, "Loaded goggles recipe returned wrong output");
      require(goggles.getMaxDamage() == 27 && goggles.getDamageValue() == 26,
          "Freshly crafted empty goggles must have their real damage-26 electric gauge");
      require(ElectricItem.manager.getCharge(goggles) == 0, "Freshly crafted goggles unexpectedly contain energy");
      if (partlyCharged) {
        double half = ((IElectricItem) gogglesItem).getMaxCharge(goggles) / 2;
        ElectricItem.manager.charge(goggles, half, Integer.MAX_VALUE, true, false);
        require(ElectricItem.manager.getCharge(goggles) == half && goggles.isDamaged(),
            "Partly charged goggles fixture must retain its genuine nonzero electric gauge");
      }
      ItemStack energyCrystal = new ItemStack(crystal);
      ElectricItem.manager.charge(energyCrystal, 0, Integer.MAX_VALUE, true, false);
      require(player.getInventory().add(new ItemStack(carbon, 4)), "Could not receive carbon plates");
      require(player.getInventory().add(energyCrystal), "Could not receive empty energy crystal");
      require(player.getInventory().add(goggles.copy()), "Could not receive actually crafted goggles");
      player.inventoryMenu.broadcastChanges();
      return ElectricItem.manager.getCharge(goggles);
    });
    server.waitFor(instance -> connection.getServerPlayer().getRecipeBook().contains(NANO)
        && connection.getServerPlayer().getAdvancements()
            .getOrStartProgress(instance.getAdvancements().get(ADVANCEMENT)).isDone());
    List<RecipeDisplayId> ids = server.computeOnServer(instance -> {
      List<RecipeDisplayId> displays = new ArrayList<>();
      instance.getRecipeManager().listDisplaysForRecipe(NANO, display -> displays.add(display.id()));
      require(!displays.isEmpty(), "Naturally discovered nano helmet has no native displays");
      return displays;
    });
    connection.waitForClientboundPackets();
    context.waitFor(client -> client.player.getRecipeBook().getCollections().stream()
        .flatMap(collection -> collection.getRecipes().stream()).anyMatch(entry -> ids.contains(entry.id())));
    context.runOnClient(client -> {
      client.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
      client.player.getRecipeBook().setFiltering(RecipeBookType.CRAFTING, true);
    });
    int menuId = server.computeOnServer(instance -> {
      var level = connection.getServerLevel();
      var provider = level.getBlockState(TABLE).getMenuProvider(level, TABLE);
      require(provider != null && connection.getServerPlayer().openMenu(provider).isPresent(),
          "Nano helmet native crafting table did not open");
      return connection.getServerPlayer().containerMenu.containerId;
    });
    context.waitFor(client -> client.gui.screen() instanceof CraftingScreen screen
        && screen.getMenu().containerId == menuId);
    context.waitTicks(2);
    click(context, context.computeOnClient(client -> {
      CraftingScreen screen = screen(client);
      require(!book(screen).isVisible(), "Nano helmet book should initially be closed");
      var button = screen.children().stream().filter(ImageButton.class::isInstance)
          .map(ImageButton.class::cast).filter(widget -> widget.visible && widget.active).findFirst().orElseThrow();
      return cursor(client, button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0);
    }));
    context.waitFor(client -> book(screen(client)).isVisible());
    click(context, context.computeOnClient(client -> {
      EditBox search = field(book(screen(client)), RecipeBookComponent.class, "searchBox", EditBox.class);
      require(search.getValue().isEmpty(), "Nano helmet fixture inherited a stale search");
      return cursor(client, search.getX() + 10.0, search.getY() + search.getHeight() / 2.0);
    }));
    String name = context.computeOnClient(client -> new ItemStack(helmet).getHoverName().getString());
    context.getInput().typeChars(name);
    context.waitFor(client -> findButton(screen(client), ids) != null);
    click(context, context.computeOnClient(client -> {
      RecipeButton button = findButton(screen(client), ids);
      require(button.getDisplayStack().is(helmet), "Native nano helmet book button has wrong result");
      require(client.player.getRecipeBook().isFiltering(RecipeBookType.CRAFTING)
              && button.getCollection().isCraftable(button.getCurrentRecipe()),
          "Nano helmet is excluded by craftable-only filter with actually crafted goggles");
      return cursor(client, button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0);
    }));
    connection.waitForServerboundPackets();
    server.waitFor(instance -> connection.getServerPlayer().containerMenu instanceof CraftingMenu menu
        && menu.getResultSlot().getItem().is(helmet));
    server.runOnServer(instance -> {
      CraftingMenu menu = (CraftingMenu) connection.getServerPlayer().containerMenu;
      List<ItemStack> grid = menu.getInputGridSlots().stream().map(slot -> slot.getItem().copy()).toList();
      AdvRecipe loaded = (AdvRecipe) instance.getRecipeManager().byKey(NANO).orElseThrow().value();
      require(loaded.matches(CraftingInput.of(3, 3, grid), connection.getServerLevel()),
          "Nano helmet book autofill produced an invalid actual server pattern");
      require(grid.stream().filter(stack -> stack.is(carbon) && stack.getCount() == 1).count() == 4,
          "Nano helmet autofill failed to place four carbon plates");
      require(grid.stream().filter(stack -> stack.is(crystal) && stack.getCount() == 1).count() == 1,
          "Nano helmet autofill failed to place its actual energy crystal");
      require(grid.stream().filter(stack -> stack.is(gogglesItem) && stack.getCount() == 1
          && ElectricItem.manager.getCharge(stack) == expectedCharge && stack.isDamaged()).count() == 1,
          "Nano helmet autofill lost actual goggles or their electric charge/gauge");
      require(grid.stream().filter(stack -> !stack.isEmpty()).count() == 6,
          "Nano helmet autofill left unexpected ingredients");
      require(ElectricItem.manager.getCharge(menu.getResultSlot().getItem()) == expectedCharge,
          "Nano helmet result did not inherit goggles energy");
      require(connection.getServerPlayer().getInventory().isEmpty(), "Nano helmet autofill duplicated ingredients");
    });
    connection.waitForClientboundPackets();
    context.waitFor(client -> screen(client).getMenu().getResultSlot().getItem().is(helmet));
    context.waitTicks(3);
    evidence.add("Nano helmet screenshot: " + context.takeScreenshot(TestScreenshotOptions.of(partlyCharged
        ? "ic2-nano-helmet-partly-charged-recipe-book" : "ic2-nano-helmet-recipe-book")
        .disableCounterPrefix()).toAbsolutePath());
    click(context, context.computeOnClient(client -> {
      CraftingScreen screen = screen(client);
      int left = field(screen, AbstractContainerScreen.class, "leftPos", Integer.class);
      int top = field(screen, AbstractContainerScreen.class, "topPos", Integer.class);
      var slot = screen.getMenu().getResultSlot();
      return cursor(client, left + slot.x + 8.0, top + slot.y + 8.0);
    }));
    connection.waitForServerboundPackets();
    server.waitFor(instance -> connection.getServerPlayer().containerMenu.getCarried().is(helmet));
    server.runOnServer(instance -> {
      CraftingMenu menu = (CraftingMenu) connection.getServerPlayer().containerMenu;
      require(menu.getCarried().getCount() == 1 && ElectricItem.manager.getCharge(menu.getCarried()) == expectedCharge,
          "Native nano helmet result pickup has wrong count or charge");
      require(menu.getInputGridSlots().stream().allMatch(slot -> slot.getItem().isEmpty())
              && connection.getServerPlayer().getInventory().isEmpty(),
          "Nano helmet craft failed to consume exactly four carbon plates, one crystal and the crafted goggles");
    });
    evidence.add("Nano helmet native book craft: actual loaded goggles recipe assembled empty damage-26 goggles; "
        + (partlyCharged ? "half-charged to " + expectedCharge + " EU" : "retained empty charge")
        + "; ordinary inventory gain naturally unlocked the helmet, Russian search found " + name
        + " with craftable-only filter enabled; actual recipe button filled the real server grid, result mouse click "
        + "yielded one helmet with " + expectedCharge + " EU and consumed all six ingredients. No recipe awards used.");
    context.runOnClient(client -> screen(client).onClose());
    connection.waitForServerboundPackets();
    server.waitFor(instance -> connection.getServerPlayer().containerMenu == connection.getServerPlayer().inventoryMenu);
    context.waitFor(client -> !(client.gui.screen() instanceof CraftingScreen));
    connection.waitForClientboundPackets();
  }

  private static void validateEveryDisplay(ClientGameTestContext context, TestServerContext server,
      TestServerConnection connection, List<String> evidence) {
    // Discovery was proven above without awards. This separate transport fixture deliberately grants all
    // ordinary recipes to compare every received book display; server tests independently discover all 439.
    List<ExpectedDisplay> expected = server.computeOnServer(instance -> {
      List<RecipeHolder<?>> recipes = instance.getRecipeManager().getRecipes().stream()
          .filter(holder -> holder.id().identifier().getNamespace().equals("ic2") && !holder.value().isSpecial())
          .filter(holder -> holder.value() instanceof CraftingRecipe || holder.value() instanceof AbstractCookingRecipe)
          .sorted(Comparator.comparing(holder -> holder.id().identifier().toString())).toList();
      require(recipes.size() == 449, "All ordinary recipe client transport matrix must include exactly 449 recipes");
      require(recipes.stream().filter(holder -> holder.value() instanceof CraftingRecipe).count() == 414,
          "Client transport matrix must include exactly 414 ordinary crafting recipes");
      require(recipes.stream().filter(holder -> holder.value() instanceof AbstractCookingRecipe).count() == 35,
          "Client transport matrix must include exactly 35 ordinary smelting recipes");
      List<ExpectedDisplay> displays = new ArrayList<>();
      ContextMap displayContext = SlotDisplayContext.fromLevel(connection.getServerLevel());
      for (RecipeHolder<?> holder : recipes) {
        List<RecipeDisplayEntry> entries = new ArrayList<>();
        instance.getRecipeManager().listDisplaysForRecipe(holder.id(), entries::add);
        require(!entries.isEmpty(), "No server display for ordinary recipe " + holder.id().identifier());
        for (RecipeDisplayEntry entry : entries) displays.add(snapshot(holder.id().identifier().toString(), entry, displayContext));
      }
      connection.getServerPlayer().getRecipeBook().removeRecipes(recipes, connection.getServerPlayer());
      connection.getServerPlayer().awardRecipes(recipes);
      return displays;
    });
    connection.waitForClientboundPackets();
    context.waitFor(client -> {
      Set<RecipeDisplayId> actualIds = client.player.getRecipeBook().getCollections().stream()
          .flatMap(collection -> collection.getRecipes().stream()).map(RecipeDisplayEntry::id)
          .collect(java.util.stream.Collectors.toSet());
      return expected.stream().allMatch(display -> actualIds.contains(display.id()));
    });
    context.runOnClient(client -> {
      Map<RecipeDisplayId, RecipeDisplayEntry> received = new HashMap<>();
      client.player.getRecipeBook().getCollections().forEach(collection -> collection.getRecipes()
          .forEach(entry -> received.put(entry.id(), entry)));
      ContextMap displayContext = SlotDisplayContext.fromLevel(client.level);
      for (ExpectedDisplay target : expected) {
        ExpectedDisplay actual = snapshot(target.recipe(), received.get(target.id()), displayContext);
        String label = target.recipe() + " [display " + target.id().index() + "]";
        require(actual.displayClass().equals(target.displayClass()) && actual.category() == target.category()
                && actual.group().equals(target.group()) && actual.width() == target.width()
                && actual.height() == target.height() && actual.duration() == target.duration()
                && Float.compare(actual.experience(), target.experience()) == 0,
            "Recipe display metadata changed during native client synchronization: " + label);
        require(java.util.Objects.equals(actual.requirements(), target.requirements()),
            "Recipe book crafting requirements changed during native client synchronization: " + label);
        require(actual.slots().size() == target.slots().size(), "Recipe display slot count differs: " + label);
        for (int index = 0; index < target.slots().size(); index++) {
          require(sameStacks(actual.slots().get(index), target.slots().get(index)),
              "Recipe display ingredient/result components changed at slot " + index + ": " + label);
        }
        evidence.add("Ordinary recipe client display PASS: " + label + "; " + target.displayClass().getSimpleName()
            + "; ingredient/result components, category, shape, crafting requirements and station preserved.");
      }
    });
    evidence.add("All ordinary native recipe book transport: 449 recipes (414 crafting, 35 smelting), "
        + expected.size() + " server displays all received and validated on the Russian client. "
        + "Explicit recipe grants were limited to this separate exhaustive transport fixture, after both natural helmet crafts.");
  }

  private static ExpectedDisplay snapshot(String recipe, RecipeDisplayEntry entry, ContextMap context) {
    require(entry != null, "Missing synchronized client display for " + recipe);
    RecipeDisplay display = entry.display();
    List<SlotDisplay> slots = new ArrayList<>();
    int width = 0;
    int height = 0;
    int duration = 0;
    float experience = 0;
    if (display instanceof ShapedCraftingRecipeDisplay shaped) {
      width = shaped.width();
      height = shaped.height();
      slots.addAll(shaped.ingredients());
    } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
      slots.addAll(shapeless.ingredients());
    } else if (display instanceof FurnaceRecipeDisplay furnace) {
      slots.add(furnace.ingredient());
      slots.add(furnace.fuel());
      duration = furnace.duration();
      experience = furnace.experience();
    } else {
      throw new AssertionError("Unexpected ordinary IC2 recipe display type for " + recipe + ": " + display.getClass());
    }
    slots.add(display.result());
    slots.add(display.craftingStation());
    List<List<ItemStack>> resolved = slots.stream().map(slot -> slot.resolveForStacks(context).stream()
        .map(ItemStack::copy).toList()).toList();
    require(!resolved.get(resolved.size() - 2).isEmpty(), "Recipe result display resolves empty for " + recipe);
    require(!resolved.getLast().isEmpty(), "Recipe crafting station display resolves empty for " + recipe);
    List<List<String>> requirements = entry.craftingRequirements().map(ingredients -> ingredients.stream()
        .map(AllRecipeBookRenderChecks::ingredientItems).toList()).orElse(null);
    return new ExpectedDisplay(recipe, entry.id(), display.getClass(), entry.category(), entry.group(), width,
        height, duration, experience, requirements, resolved);
  }

  private static List<String> ingredientItems(Ingredient ingredient) {
    return ingredient.items().map(holder -> BuiltInRegistries.ITEM.getKey(holder.value()).toString()).sorted().toList();
  }

  private static boolean sameStacks(List<ItemStack> actual, List<ItemStack> expected) {
    if (actual.size() != expected.size()) return false;
    // Tag order is not part of the wire contract; every candidate stack and its components are.
    List<ItemStack> unmatched = new ArrayList<>(expected);
    for (ItemStack stack : actual) {
      int match = -1;
      for (int index = 0; index < unmatched.size(); index++) {
        if (stack.getCount() == unmatched.get(index).getCount()
            && ItemStack.isSameItemSameComponents(stack, unmatched.get(index))) { match = index; break; }
      }
      if (match < 0) return false;
      unmatched.remove(match);
    }
    return unmatched.isEmpty();
  }

  private static ItemStack actualIngredient(IRecipeInput ingredient) {
    List<ItemStack> candidates = new ArrayList<>();
    ingredient.getIngredient().items().map(holder -> new ItemStack(holder.value())).forEach(candidates::add);
    ingredient.getInputs().stream().map(ItemStack::copy).forEach(candidates::add);
    for (ItemStack candidate : candidates) {
      candidate.setCount(1);
      if (candidate.getItem() instanceof IElectricItem) {
        ElectricItem.manager.charge(candidate, 0, Integer.MAX_VALUE, true, false);
      }
      if (ingredient.matches(candidate)) return candidate;
    }
    throw new AssertionError("No matching real goggles recipe input for " + ingredient);
  }

  private static CraftingScreen screen(Minecraft client) {
    require(client.gui.screen() instanceof CraftingScreen, "Expected the real native crafting table screen");
    return (CraftingScreen) client.gui.screen();
  }

  private static RecipeBookComponent<?> book(CraftingScreen screen) {
    return field(screen, AbstractRecipeBookScreen.class, "recipeBookComponent", RecipeBookComponent.class);
  }

  private static RecipeButton findButton(CraftingScreen screen, List<RecipeDisplayId> ids) {
    RecipeBookPage page = field(book(screen), RecipeBookComponent.class, "recipeBookPage", RecipeBookPage.class);
    List<?> buttons = field(page, RecipeBookPage.class, "buttons", List.class);
    for (Object value : buttons) {
      RecipeButton button = (RecipeButton) value;
      if (button.visible && button.active && ids.contains(button.getCurrentRecipe())) return button;
    }
    return null;
  }

  private static List<BlockPos> platform() {
    List<BlockPos> blocks = new ArrayList<>();
    for (int x = -1; x <= 1; x++) for (int z = 0; z <= 3; z++) blocks.add(TABLE.offset(x, -1, z));
    return blocks;
  }

  private static double[] cursor(Minecraft client, double x, double y) {
    var window = client.getWindow();
    return new double[] {x * window.getScreenWidth() / window.getGuiScaledWidth(),
        y * window.getScreenHeight() / window.getGuiScaledHeight()};
  }

  private static void click(ClientGameTestContext context, double[] cursor) {
    context.getInput().setCursorPos(cursor[0], cursor[1]);
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    context.waitTick();
  }

  private static <T> T field(Object instance, Class<?> owner, String name, Class<T> type) {
    try {
      Field field = owner.getDeclaredField(name);
      field.setAccessible(true);
      return type.cast(field.get(instance));
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError("Could not inspect native recipe book field " + owner.getName() + "." + name, failure);
    }
  }

  private static ResourceKey<Recipe<?>> recipe(String name) {
    return ResourceKey.create(Registries.RECIPE, Identifier.parse("ic2:shaped/" + name));
  }

  private static Item item(String id) {
    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    require(item != null && item != Items.AIR, "Missing all-recipe client fixture item " + id);
    return item;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private record SavedPlayer(double x, double y, double z, float yaw, float pitch, GameType mode) {}

  private record ExpectedDisplay(String recipe, RecipeDisplayId id, Class<?> displayClass,
      RecipeBookCategory category, OptionalInt group, int width, int height, int duration, float experience,
      List<List<String>> requirements, List<List<ItemStack>> slots) {}
}
