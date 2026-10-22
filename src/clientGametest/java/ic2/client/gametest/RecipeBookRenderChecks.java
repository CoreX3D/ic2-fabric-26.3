package ic2.client.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Inventory discovery, native green book UI, and real server recipe placement. */
@Environment(EnvType.CLIENT)
@SuppressWarnings("UnstableApiUsage")
public final class RecipeBookRenderChecks {
  private static final ResourceKey<Recipe<?>> RECIPE = ResourceKey.create(Registries.RECIPE,
      Identifier.parse("ic2:shapeless/copper_plate"));
  private static final Identifier ADVANCEMENT = Identifier.parse("ic2:recipes/shapeless/copper_plate");
  private static final BlockPos TABLE = new BlockPos(-4, 64, 48);

  private RecipeBookRenderChecks() {}

  public static List<String> validate(ClientGameTestContext context, TestServerContext server,
      TestServerConnection connection) {
    Path directory = context.computeOnClient(client -> client.gameDirectory.toPath().toAbsolutePath().normalize());
    require(directory.endsWith(Path.of("build", "client-render-smoke")),
        "Recipe book fixtures are permitted only in build/client-render-smoke");
    Item plate = item("ic2:copper_plate");
    Item hammer = item("ic2:forge_hammer");
    SavedPlayer original = server.computeOnServer(instance -> {
      var player = connection.getServerPlayer();
      require(player.containerMenu == player.inventoryMenu, "A preceding test menu is still open");
      return new SavedPlayer(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
          player.gameMode.getGameModeForPlayer());
    });
    List<String> evidence = new ArrayList<>();
    boolean tableCreated = false;
    try {
      server.runOnServer(instance -> {
        var level = connection.getServerLevel();
        require(level.isEmptyBlock(TABLE), "Recipe book fixture would overwrite another test scene");
        for (BlockPos floor : platform()) require(level.isEmptyBlock(floor),
            "Recipe book platform would overwrite another test scene at " + floor);
      });
      tableCreated = true;
      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        var level = connection.getServerLevel();
        // The consistent flat world has its ground at the dimension minimum, far below Y=64.
        // Include the survival player's standing position, not only the table's support.
        for (BlockPos floor : platform()) {
          level.setBlock(floor, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(TABLE, Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_ALL);
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(level, TABLE.getX() + 0.5, TABLE.getY(), TABLE.getZ() + 2.5,
            Set.of(), 180.0f, 0.0f, true);
        player.getInventory().clearContent();
        var advancement = instance.getAdvancements().get(ADVANCEMENT);
        require(advancement != null, "Missing natural copper plate recipe discovery advancement");
        List<String> completed = new ArrayList<>();
        player.getAdvancements().getOrStartProgress(advancement).getCompletedCriteria().forEach(completed::add);
        for (String criterion : completed) player.getAdvancements().revoke(advancement, criterion);
        var recipe = instance.getRecipeManager().byKey(RECIPE).orElseThrow();
        player.getRecipeBook().removeRecipes(List.of(recipe), player);
        player.inventoryMenu.broadcastChanges();
        require(!player.getRecipeBook().contains(RECIPE), "Copper plate recipe was already discovered");
        require(!player.getAdvancements().getOrStartProgress(advancement).isDone(),
            "Copper plate discovery advancement was already complete");
      });
      context.waitTicks(2);
      connection.waitForClientboundPackets();

      // Only an ordinary inventory change may discover this recipe. No recipe/advancement award commands.
      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        require(player.getInventory().add(new ItemStack(Items.COPPER_INGOT)), "Could not add copper ingot");
        require(player.getInventory().add(new ItemStack(hammer)), "Could not add forge hammer");
        player.inventoryMenu.broadcastChanges();
      });
      server.waitFor(instance -> {
        var player = connection.getServerPlayer();
        return player.getRecipeBook().contains(RECIPE)
            && player.getAdvancements().getOrStartProgress(instance.getAdvancements().get(ADVANCEMENT)).isDone();
      });
      List<RecipeDisplayId> displays = server.computeOnServer(instance -> {
        List<RecipeDisplayId> ids = new ArrayList<>();
        instance.getRecipeManager().listDisplaysForRecipe(RECIPE, display -> ids.add(display.id()));
        require(!ids.isEmpty(), "Copper plate recipe has no native recipe book displays");
        return ids;
      });
      connection.waitForClientboundPackets();
      context.waitFor(client -> client.player != null && client.player.getRecipeBook().getCollections().stream()
          .flatMap(collection -> collection.getRecipes().stream()).anyMatch(entry -> displays.contains(entry.id())));
      evidence.add("Recipe book discovery: copper ingot + forge hammer inventory gain completed " + ADVANCEMENT
          + " and synchronized native copper plate recipe displays to the client without award commands.");

      context.runOnClient(client -> {
        client.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
        client.player.getRecipeBook().setFiltering(RecipeBookType.CRAFTING, true);
      });
      int menuId = server.computeOnServer(instance -> {
        var level = connection.getServerLevel();
        var provider = level.getBlockState(TABLE).getMenuProvider(level, TABLE);
        require(provider != null, "Native crafting table did not provide its menu");
        require(connection.getServerPlayer().openMenu(provider).isPresent(), "Native crafting table menu did not open");
        return connection.getServerPlayer().containerMenu.containerId;
      });
      context.waitFor(client -> client.gui.screen() instanceof CraftingScreen screen
          && screen.getMenu().containerId == menuId);
      context.waitTicks(2);
      double[] greenButton = context.computeOnClient(client -> {
        CraftingScreen screen = craftingScreen(client);
        require(!book(screen).isVisible(), "Native recipe book should initially be closed");
        var button = screen.children().stream().filter(ImageButton.class::isInstance)
            .map(ImageButton.class::cast).filter(widget -> widget.visible && widget.active).findFirst().orElseThrow();
        return cursor(client, button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0);
      });
      click(context, greenButton);
      context.waitFor(client -> book(craftingScreen(client)).isVisible());

      double[] search = context.computeOnClient(client -> {
        EditBox box = field(book(craftingScreen(client)), RecipeBookComponent.class, "searchBox", EditBox.class);
        return cursor(client, box.getX() + 10.0, box.getY() + box.getHeight() / 2.0);
      });
      click(context, search);
      String name = context.computeOnClient(client -> new ItemStack(plate).getHoverName().getString());
      context.getInput().typeChars(name);
      context.waitFor(client -> findButton(craftingScreen(client), displays) != null);
      context.runOnClient(client -> {
        RecipeButton button = findButton(craftingScreen(client), displays);
        require(button != null && button.getDisplayStack().is(plate), "Native recipe book button has wrong IC result");
        require(button.getCollection().isCraftable(button.getCurrentRecipe()),
            "Copper plate recipe is not shown as craftable with its real ingredients");
      });
      double[] recipeButton = context.computeOnClient(client -> {
        RecipeButton button = findButton(craftingScreen(client), displays);
        return cursor(client, button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0);
      });
      click(context, recipeButton);
      connection.waitForServerboundPackets();
      server.waitFor(instance -> connection.getServerPlayer().containerMenu instanceof CraftingMenu menu
          && menu.getResultSlot().getItem().is(plate));
      server.runOnServer(instance -> {
        CraftingMenu menu = (CraftingMenu) connection.getServerPlayer().containerMenu;
        require(menu.getInputGridSlots().stream().filter(slot -> slot.getItem().is(Items.COPPER_INGOT)).count() == 1,
            "Recipe book click did not place the copper ingot in the actual server grid");
        require(menu.getInputGridSlots().stream().filter(slot -> slot.getItem().is(hammer)).count() == 1,
            "Recipe book click did not place the forge hammer in the actual server grid");
      });
      connection.waitForClientboundPackets();
      context.waitFor(client -> craftingScreen(client).getMenu().getResultSlot().getItem().is(plate));
      context.waitTicks(3);
      evidence.add("Recipe book UI: real green button opened CraftingScreen book; native localized search found "
          + name + "; real recipe button click filled copper and hammer in the server grid with copper plate result.");
      evidence.add("Recipe book screenshot: " + context.takeScreenshot(
          TestScreenshotOptions.of("ic2-recipe-book").disableCounterPrefix()).toAbsolutePath());

      double[] result = context.computeOnClient(client -> {
        CraftingScreen screen = craftingScreen(client);
        int left = field(screen, AbstractContainerScreen.class, "leftPos", Integer.class);
        int top = field(screen, AbstractContainerScreen.class, "topPos", Integer.class);
        var slot = screen.getMenu().getResultSlot();
        return cursor(client, left + slot.x + 8.0, top + slot.y + 8.0);
      });
      click(context, result);
      connection.waitForServerboundPackets();
      server.waitFor(instance -> connection.getServerPlayer().containerMenu.getCarried().is(plate));
      server.runOnServer(instance -> {
        CraftingMenu menu = (CraftingMenu) connection.getServerPlayer().containerMenu;
        require(menu.getCarried().getCount() == 1, "Native recipe result has unexpected count");
        require(menu.getInputGridSlots().stream().noneMatch(slot -> slot.getItem().is(Items.COPPER_INGOT)),
            "Crafting did not consume copper ingot");
        require(menu.getInputGridSlots().stream().anyMatch(slot -> slot.getItem().is(hammer)
            && slot.getItem().getDamageValue() == 1), "Crafting did not preserve and damage the forge hammer");
      });
      evidence.add("Recipe book craft: real result slot click produced one server-side copper plate, consumed ingot, "
          + "and retained hammer with one durability damage.");
      craftWithWornHammer(context, server, connection, hammer, Items.IRON_INGOT, "iron", 1, evidence);
      craftWithWornHammer(context, server, connection, hammer, item("ic2:lead_ingot"), "lead", 2, evidence);
      return evidence;
    } finally {
      context.runOnClient(client -> {
        if (client.player != null) client.player.closeContainer();
      });
      connection.waitForServerboundPackets();
      boolean removeTable = tableCreated;
      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        player.closeContainer();
        player.getInventory().clearContent();
        if (removeTable) {
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

  private static void craftWithWornHammer(ClientGameTestContext context, TestServerContext server,
      TestServerConnection connection, Item hammer, Item ingot, String metal, int damage, List<String> evidence) {
    Item plate = item("ic2:" + metal + "_plate");
    ResourceKey<Recipe<?>> recipe = ResourceKey.create(Registries.RECIPE,
        Identifier.parse("ic2:shapeless/" + metal + "_plate"));
    Identifier advancementId = Identifier.parse("ic2:recipes/shapeless/" + metal + "_plate");

    // Closing the native table must return the actual worn remainder, rather than supply another test hammer.
    context.runOnClient(client -> craftingScreen(client).onClose());
    connection.waitForServerboundPackets();
    server.waitFor(instance -> connection.getServerPlayer().containerMenu == connection.getServerPlayer().inventoryMenu);
    context.waitFor(client -> !(client.gui.screen() instanceof CraftingScreen));
    server.runOnServer(instance -> {
      var player = connection.getServerPlayer();
      int hammers = 0;
      for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack.is(hammer)) {
          hammers += stack.getCount();
          require(stack.getDamageValue() == damage, "Returned forge hammer has wrong wear before " + metal + " plate");
        }
      }
      require(hammers == 1, "Closing the crafting table must return exactly the original worn hammer");
      require(player.containerMenu.getCarried().isEmpty(), "Previous plate remained on the cursor after closing the table");
      var advancement = instance.getAdvancements().get(advancementId);
      require(advancement != null, "Missing natural " + metal + " plate discovery advancement");
      List<String> completed = new ArrayList<>();
      player.getAdvancements().getOrStartProgress(advancement).getCompletedCriteria().forEach(completed::add);
      for (String criterion : completed) player.getAdvancements().revoke(advancement, criterion);
      player.getRecipeBook().removeRecipes(List.of(instance.getRecipeManager().byKey(recipe).orElseThrow()), player);
      require(!player.getRecipeBook().contains(recipe), metal + " plate recipe was not reset before natural discovery");
    });
    connection.waitForClientboundPackets();
    context.waitTick();
    server.runOnServer(instance -> {
      var player = connection.getServerPlayer();
      require(player.getInventory().add(new ItemStack(ingot)), "Could not add " + metal + " ingot");
      player.inventoryMenu.broadcastChanges();
    });
    server.waitFor(instance -> {
      var player = connection.getServerPlayer();
      return player.getRecipeBook().contains(recipe)
          && player.getAdvancements().getOrStartProgress(instance.getAdvancements().get(advancementId)).isDone();
    });
    List<RecipeDisplayId> displays = server.computeOnServer(instance -> {
      List<RecipeDisplayId> ids = new ArrayList<>();
      instance.getRecipeManager().listDisplaysForRecipe(recipe, display -> ids.add(display.id()));
      require(!ids.isEmpty(), metal + " plate has no native recipe book display");
      return ids;
    });
    connection.waitForClientboundPackets();
    context.waitFor(client -> client.player.getRecipeBook().getCollections().stream()
        .flatMap(collection -> collection.getRecipes().stream()).anyMatch(entry -> displays.contains(entry.id())));
    context.runOnClient(client -> {
      client.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
      client.player.getRecipeBook().setFiltering(RecipeBookType.CRAFTING, true);
    });
    int menuId = server.computeOnServer(instance -> {
      var level = connection.getServerLevel();
      var provider = level.getBlockState(TABLE).getMenuProvider(level, TABLE);
      require(provider != null, "Native crafting table did not provide its menu");
      require(connection.getServerPlayer().openMenu(provider).isPresent(), "Native crafting table menu did not reopen");
      return connection.getServerPlayer().containerMenu.containerId;
    });
    context.waitFor(client -> client.gui.screen() instanceof CraftingScreen screen
        && screen.getMenu().containerId == menuId);
    context.waitTicks(2);
    double[] greenButton = context.computeOnClient(client -> {
      CraftingScreen screen = craftingScreen(client);
      require(!book(screen).isVisible(), "Reopened recipe book should initially be closed");
      var button = screen.children().stream().filter(ImageButton.class::isInstance)
          .map(ImageButton.class::cast).filter(widget -> widget.visible && widget.active).findFirst().orElseThrow();
      return cursor(client, button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0);
    });
    click(context, greenButton);
    context.waitFor(client -> book(craftingScreen(client)).isVisible());
    double[] search = context.computeOnClient(client -> {
      EditBox box = field(book(craftingScreen(client)), RecipeBookComponent.class, "searchBox", EditBox.class);
      require(box.getValue().isEmpty(), "Reopened crafting table retained a stale recipe search");
      return cursor(client, box.getX() + 10.0, box.getY() + box.getHeight() / 2.0);
    });
    click(context, search);
    String name = context.computeOnClient(client -> new ItemStack(plate).getHoverName().getString());
    context.getInput().typeChars(name);
    context.waitFor(client -> findButton(craftingScreen(client), displays) != null);
    double[] recipeButton = context.computeOnClient(client -> {
      RecipeButton button = findButton(craftingScreen(client), displays);
      require(button != null && button.getDisplayStack().is(plate), "Recipe book button has wrong " + metal + " plate");
      require(client.player.getRecipeBook().isFiltering(RecipeBookType.CRAFTING),
          "Worn hammer regression must keep the craftable-only recipe filter enabled");
      require(button.getCollection().isCraftable(button.getCurrentRecipe()),
          metal + " plate is not craftable in the book with the previously used hammer");
      return cursor(client, button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0);
    });
    click(context, recipeButton);
    connection.waitForServerboundPackets();
    server.waitFor(instance -> connection.getServerPlayer().containerMenu instanceof CraftingMenu menu
        && menu.getResultSlot().getItem().is(plate));
    server.runOnServer(instance -> {
      CraftingMenu menu = (CraftingMenu) connection.getServerPlayer().containerMenu;
      require(menu.getInputGridSlots().stream().filter(slot -> slot.getItem().is(ingot)
          && slot.getItem().getCount() == 1).count() == 1, "Autofill failed to place actual " + metal + " ingot");
      require(menu.getInputGridSlots().stream().filter(slot -> slot.getItem().is(hammer)
          && slot.getItem().getCount() == 1 && slot.getItem().getDamageValue() == damage).count() == 1,
          "Autofill failed to move the original worn hammer into the " + metal + " plate recipe");
      require(menu.getInputGridSlots().stream().filter(slot -> !slot.getItem().isEmpty()).count() == 2,
          metal + " plate autofill left unexpected ingredients in the server grid");
      for (int slot = 0; slot < connection.getServerPlayer().getInventory().getContainerSize(); slot++) {
        require(!connection.getServerPlayer().getInventory().getItem(slot).is(hammer),
            "Autofill duplicated the worn hammer in the player inventory");
      }
    });
    connection.waitForClientboundPackets();
    context.waitFor(client -> craftingScreen(client).getMenu().getResultSlot().getItem().is(plate));
    context.waitTicks(3);
    evidence.add("Worn hammer recipe book screenshot: " + context.takeScreenshot(
        TestScreenshotOptions.of("ic2-" + metal + "-plate-recipe-book").disableCounterPrefix()).toAbsolutePath());
    double[] result = context.computeOnClient(client -> {
      CraftingScreen screen = craftingScreen(client);
      int left = field(screen, AbstractContainerScreen.class, "leftPos", Integer.class);
      int top = field(screen, AbstractContainerScreen.class, "topPos", Integer.class);
      var slot = screen.getMenu().getResultSlot();
      return cursor(client, left + slot.x + 8.0, top + slot.y + 8.0);
    });
    click(context, result);
    connection.waitForServerboundPackets();
    server.waitFor(instance -> connection.getServerPlayer().containerMenu.getCarried().is(plate));
    server.runOnServer(instance -> {
      CraftingMenu menu = (CraftingMenu) connection.getServerPlayer().containerMenu;
      require(menu.getCarried().getCount() == 1, "Native " + metal + " plate output has unexpected count");
      require(menu.getInputGridSlots().stream().noneMatch(slot -> slot.getItem().is(ingot)),
          "Crafting did not consume the " + metal + " ingot");
      require(menu.getInputGridSlots().stream().filter(slot -> !slot.getItem().isEmpty()).count() == 1
          && menu.getInputGridSlots().stream().anyMatch(slot -> slot.getItem().is(hammer)
              && slot.getItem().getCount() == 1 && slot.getItem().getDamageValue() == damage + 1),
          "Crafting did not preserve the same hammer and advance its durability for " + metal + " plate");
    });
    evidence.add("Worn hammer recipe book craft: natural " + metal + " ingot discovery and localized search found "
        + name + " with craftable-only filter enabled; real book autofill and output click produced one plate, "
        + "consumed its ingot and advanced the original copper-crafting hammer damage " + damage + " -> " + (damage + 1) + ".");
  }

  private static CraftingScreen craftingScreen(Minecraft client) {
    require(client.gui.screen() instanceof CraftingScreen, "Expected the real native crafting table screen");
    return (CraftingScreen) client.gui.screen();
  }

  private static List<BlockPos> platform() {
    List<BlockPos> blocks = new ArrayList<>();
    for (int x = -1; x <= 1; x++) for (int z = 0; z <= 3; z++) blocks.add(TABLE.offset(x, -1, z));
    return blocks;
  }

  private static RecipeBookComponent<?> book(CraftingScreen screen) {
    return field(screen, AbstractRecipeBookScreen.class, "recipeBookComponent", RecipeBookComponent.class);
  }

  private static RecipeButton findButton(CraftingScreen screen, List<RecipeDisplayId> displays) {
    RecipeBookPage page = field(book(screen), RecipeBookComponent.class, "recipeBookPage", RecipeBookPage.class);
    List<?> buttons = field(page, RecipeBookPage.class, "buttons", List.class);
    for (Object value : buttons) {
      RecipeButton button = (RecipeButton) value;
      if (button.visible && button.active && displays.contains(button.getCurrentRecipe())) return button;
    }
    return null;
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
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError("Could not inspect native recipe book field " + owner.getName() + "." + name, exception);
    }
  }

  private static Item item(String id) {
    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    require(item != null && item != Items.AIR, "Missing recipe book fixture item " + id);
    return item;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private record SavedPlayer(double x, double y, double z, float yaw, float pitch, GameType mode) {}
}
