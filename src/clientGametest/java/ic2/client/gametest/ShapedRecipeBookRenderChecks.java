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

/** Real green-book placement must preserve the generator's centered vertical pattern. */
@Environment(EnvType.CLIENT)
@SuppressWarnings("UnstableApiUsage")
public final class ShapedRecipeBookRenderChecks {
  private static final ResourceKey<Recipe<?>> RECIPE = recipe("generator_from_furnace");
  private static final ResourceKey<Recipe<?>> ALTERNATIVE = recipe("generator_from_iron_furnace");
  private static final List<Identifier> ADVANCEMENTS = List.of(
      Identifier.parse("ic2:recipes/shaped/generator_from_furnace"),
      Identifier.parse("ic2:recipes/shaped/generator_from_iron_furnace"));
  private static final BlockPos TABLE = new BlockPos(-12, 64, 48);

  private ShapedRecipeBookRenderChecks() {}

  public static List<String> validate(ClientGameTestContext context, TestServerContext server,
      TestServerConnection connection) {
    Path directory = context.computeOnClient(client -> client.gameDirectory.toPath().toAbsolutePath().normalize());
    require(directory.endsWith(Path.of("build", "client-render-smoke")),
        "Shaped recipe book fixtures are permitted only in build/client-render-smoke");
    Item battery = item("ic2:re_battery");
    Item machine = item("ic2:machine");
    Item generator = item("ic2:generator");
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
        require(level.isEmptyBlock(TABLE), "Generator recipe fixture would overwrite another test scene");
        for (BlockPos floor : platform()) require(level.isEmptyBlock(floor),
            "Generator recipe platform would overwrite another test scene at " + floor);
      });
      tableCreated = true;
      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        var level = connection.getServerLevel();
        // This test world's ground lies far below Y=64; support the survival player as well as the table.
        for (BlockPos floor : platform()) {
          level.setBlock(floor, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(TABLE, Blocks.CRAFTING_TABLE.defaultBlockState(), Block.UPDATE_ALL);
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(level, TABLE.getX() + 0.5, TABLE.getY(), TABLE.getZ() + 2.5,
            Set.of(), 180.0f, 0.0f, true);
        player.getInventory().clearContent();
        for (Identifier id : ADVANCEMENTS) {
          var advancement = instance.getAdvancements().get(id);
          require(advancement != null, "Missing natural generator recipe discovery advancement " + id);
          List<String> completed = new ArrayList<>();
          player.getAdvancements().getOrStartProgress(advancement).getCompletedCriteria().forEach(completed::add);
          for (String criterion : completed) player.getAdvancements().revoke(advancement, criterion);
        }
        player.getRecipeBook().removeRecipes(List.of(
            instance.getRecipeManager().byKey(RECIPE).orElseThrow(),
            instance.getRecipeManager().byKey(ALTERNATIVE).orElseThrow()), player);
        player.inventoryMenu.broadcastChanges();
        require(!player.getRecipeBook().contains(RECIPE) && !player.getRecipeBook().contains(ALTERNATIVE),
            "Generator recipes were already discovered");
      });
      context.waitTicks(2);
      connection.waitForClientboundPackets();

      // The battery naturally discovers both generator variants; only the furnace variant is craftable.
      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        require(player.getInventory().add(new ItemStack(battery)), "Could not add RE battery");
        require(player.getInventory().add(new ItemStack(machine)), "Could not add machine casing");
        require(player.getInventory().add(new ItemStack(Items.FURNACE)), "Could not add vanilla furnace");
        player.inventoryMenu.broadcastChanges();
      });
      server.waitFor(instance -> {
        var player = connection.getServerPlayer();
        return player.getRecipeBook().contains(RECIPE) && player.getRecipeBook().contains(ALTERNATIVE)
            && ADVANCEMENTS.stream().allMatch(id -> player.getAdvancements()
                .getOrStartProgress(instance.getAdvancements().get(id)).isDone());
      });
      List<RecipeDisplayId> displays = server.computeOnServer(instance -> {
        List<RecipeDisplayId> ids = new ArrayList<>();
        instance.getRecipeManager().listDisplaysForRecipe(RECIPE, display -> ids.add(display.id()));
        require(!ids.isEmpty(), "Furnace generator has no native recipe book display");
        return ids;
      });
      List<RecipeDisplayId> alternativeDisplays = server.computeOnServer(instance -> {
        List<RecipeDisplayId> ids = new ArrayList<>();
        instance.getRecipeManager().listDisplaysForRecipe(ALTERNATIVE, display -> ids.add(display.id()));
        require(!ids.isEmpty(), "Iron furnace generator has no native recipe book display");
        return ids;
      });
      connection.waitForClientboundPackets();
      context.waitFor(client -> client.player != null && client.player.getRecipeBook().getCollections().stream()
          .flatMap(collection -> collection.getRecipes().stream()).map(entry -> entry.id()).toList()
          .containsAll(displays) && client.player.getRecipeBook().getCollections().stream()
          .flatMap(collection -> collection.getRecipes().stream()).map(entry -> entry.id()).toList()
          .containsAll(alternativeDisplays));
      evidence.add("Shaped recipe book discovery: ordinary battery + machine casing + vanilla furnace inventory gain "
          + "unlocked and synchronized both generator recipe alternatives without award commands.");

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
      String name = context.computeOnClient(client -> new ItemStack(generator).getHoverName().getString());
      context.getInput().typeChars(name);
      context.waitFor(client -> findButton(craftingScreen(client), displays) != null);
      context.runOnClient(client -> {
        RecipeButton button = findButton(craftingScreen(client), displays);
        require(button != null && button.getDisplayStack().is(generator), "Native recipe book has wrong generator result");
        require(button.getCollection().isCraftable(button.getCurrentRecipe()),
            "Furnace generator recipe is not shown as craftable with its actual ingredients");
        require(client.player.getRecipeBook().getCollections().stream().allMatch(collection ->
            collection.getRecipes().stream().filter(entry -> alternativeDisplays.contains(entry.id()))
                .noneMatch(entry -> collection.isCraftable(entry.id()))),
            "Iron furnace generator alternative is incorrectly craftable with only a vanilla furnace");
      });
      double[] recipeButton = context.computeOnClient(client -> {
        RecipeButton button = findButton(craftingScreen(client), displays);
        return cursor(client, button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0);
      });
      click(context, recipeButton);
      connection.waitForServerboundPackets();
      server.waitFor(instance -> connection.getServerPlayer().containerMenu instanceof CraftingMenu menu
          && menu.getResultSlot().getItem().is(generator));
      server.runOnServer(instance -> {
        CraftingMenu menu = (CraftingMenu) connection.getServerPlayer().containerMenu;
        require(menu.getInputGridSlots().size() == 9, "Generator native crafting grid has wrong size");
        for (int index = 0; index < 9; index++) {
          Item expected = switch (index) { case 1 -> battery; case 4 -> machine; case 7 -> Items.FURNACE; default -> null; };
          ItemStack actual = menu.getInputGridSlots().get(index).getItem();
          require(expected == null ? actual.isEmpty() : actual.is(expected) && actual.getCount() == 1,
              "Generator recipe book placed an incorrect ingredient in server grid index " + index + ": " + actual);
        }
        require(menu.getResultSlot().getItem().getCount() == 1, "Generator native output count is incorrect");
      });
      connection.waitForClientboundPackets();
      context.waitFor(client -> craftingScreen(client).getMenu().getResultSlot().getItem().is(generator));
      context.waitTicks(3);
      evidence.add("Shaped recipe book UI: native green book and localized search found " + name
          + "; real recipe button click placed battery, machine casing and furnace at server grid indexes 1, 4, 7 "
          + "(top/middle/bottom center), left all six other slots empty and produced one generator.");
      evidence.add("Shaped recipe book screenshot: " + context.takeScreenshot(
          TestScreenshotOptions.of("ic2-generator-recipe-book").disableCounterPrefix()).toAbsolutePath());

      double[] result = context.computeOnClient(client -> {
        CraftingScreen screen = craftingScreen(client);
        int left = field(screen, AbstractContainerScreen.class, "leftPos", Integer.class);
        int top = field(screen, AbstractContainerScreen.class, "topPos", Integer.class);
        var slot = screen.getMenu().getResultSlot();
        return cursor(client, left + slot.x + 8.0, top + slot.y + 8.0);
      });
      click(context, result);
      connection.waitForServerboundPackets();
      server.waitFor(instance -> connection.getServerPlayer().containerMenu.getCarried().is(generator));
      server.runOnServer(instance -> {
        CraftingMenu menu = (CraftingMenu) connection.getServerPlayer().containerMenu;
        require(menu.getCarried().getCount() == 1, "Native generator craft has unexpected count");
        require(menu.getInputGridSlots().stream().allMatch(slot -> slot.getItem().isEmpty()),
            "Generator craft did not consume all three actual ingredients");
        require(connection.getServerPlayer().getInventory().isEmpty(),
            "Generator ingredients unexpectedly remained in the player inventory");
      });
      evidence.add("Shaped recipe book craft: real native result click yielded exactly one server-side generator "
          + "and consumed the battery, machine casing and furnace with no remaining ingredients.");
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

  private static ResourceKey<Recipe<?>> recipe(String name) {
    return ResourceKey.create(Registries.RECIPE, Identifier.parse("ic2:shaped/" + name));
  }

  private static Item item(String id) {
    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    require(item != null && item != Items.AIR, "Missing generator recipe fixture item " + id);
    return item;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private record SavedPlayer(double x, double y, double z, float yaw, float pitch, GameType mode) {}
}
