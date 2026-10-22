package ic2.client.gametest;

import ic2.api.item.ElectricItem;
import ic2.core.block.tileentity.Ic2TileEntityBlock;
import ic2.core.util.StackUtil;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Runs only from Loom's isolated build/client-render-smoke game directory. */
@Environment(EnvType.CLIENT)
@SuppressWarnings("UnstableApiUsage")
public final class Ic2ClientRenderGameTest implements FabricClientGameTest {
  private static final List<String> MACHINE_IDS = List.of(
      "ic2:generator", "ic2:macerator", "ic2:electric_furnace", "ic2:extractor", "ic2:compressor", "ic2:batbox");
  private static final List<String> RESOURCE_IDS = List.of(
      "minecraft:copper_ore", "ic2:tin_ore", "ic2:lead_ore", "ic2:uranium_ore", "ic2:rubber_log", "ic2:reinforced_glass");
  private static final List<String> ITEM_IDS = List.of(
      "ic2:generator", "ic2:macerator", "ic2:electric_furnace", "ic2:batbox",
      "ic2:tin_ore", "ic2:lead_ore", "ic2:uranium_ore", "minecraft:copper_ingot",
      "ic2:insulated_copper_cable", "ic2:tin_cable", "ic2:rubber_leaves", "ic2:empty_cell",
      "ic2:quantum_helmet", "ic2:quantum_chestplate", "ic2:quantum_leggings", "ic2:quantum_boots",
      "ic2:drill", "ic2:diamond_drill", "ic2:mining_laser", "ic2:chainsaw",
      "ic2:wrench", "ic2:nano_saber", "ic2:re_battery", "ic2:lapotron_crystal", "ic2:obscurator");

  @Override public void runTest(ClientGameTestContext context) {
    Path gameDirectory = context.computeOnClient(client -> client.gameDirectory.toPath().toAbsolutePath().normalize());
    require(gameDirectory.endsWith(Path.of("build", "client-render-smoke")),
        "Refusing to create a test world outside build/client-render-smoke: " + gameDirectory);
    int[] previousWindow = context.computeOnClient(client -> new int[] {
        client.getWindow().getScreenWidth(), client.getWindow().getScreenHeight(), client.options.guiScale().get()});
    context.runOnClient(client -> {
      client.options.renderDistance().set(5);
      client.options.simulationDistance().set(5);
      client.options.guiScale().set(2);
      client.options.fov().set(65);
    });
    // withSize only resizes the screenshot render target; GUI layout stays at its old size.
    // Resize the test window before creating any screen so its viewport and GUI agree.
    context.getInput().resizeWindow(1600, 900);
    context.runOnClient(Minecraft::resizeGui);
    context.waitTicks(3);
    context.runOnClient(client -> {
      var window = client.getWindow();
      require(window.getScreenWidth() == 1600 && window.getScreenHeight() == 900
              && window.getWidth() == 1600 && window.getHeight() == 900
              && window.getGuiScaledWidth() == 800 && window.getGuiScaledHeight() == 450,
          "Client test viewport and GUI did not resize together to 1600x900 at GUI scale 2");
    });
    List<String> evidence = new ArrayList<>();
    for (String modId : System.getProperty("ic2.gametest.requiredMods", "").split(",")) {
      if (modId.isBlank()) continue;
      var loaded = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(modId);
      require(loaded.isPresent(), "Compatibility test did not load requested profile mod " + modId);
      evidence.add("Profile mod loaded: " + modId + " "
          + loaded.orElseThrow().getMetadata().getVersion().getFriendlyString());
    }
    evidence.add("Screenshot viewport: native test window/framebuffer 1600x900, GUI 800x450 (scale 2).");
    RussianLocaleChecks.SavedLanguage previousLanguage = null;
    try {
      previousLanguage = RussianLocaleChecks.configure(context, evidence);
      try (var world = context.worldBuilder().setUseConsistentSettings(true).create()) {
        TestServerConnection connection = world.getConnection();
        world.getServer().runOnServer(server -> buildScene(connection));
        context.waitTick();
        connection.waitForClientboundPackets();
        connection.waitForClientboundEntityUpdates(net.minecraft.world.entity.EntityTypes.ARMOR_STAND);
        connection.waitForChunksRender();
        context.waitTicks(10);
        evidence.addAll(RussianChatChecks.validate(context, world.getServer(), connection));
        context.runOnClient(client -> {
          require(client.player != null && client.level != null, "Client did not join the test world");
          evidence.addAll(RussianLocaleChecks.validateRegisteredItemNames(client));
          evidence.addAll(validateBlocks(client));
          evidence.addAll(GasFluidRenderChecks.validate(client));
        });
        Path worldImage = takeWorldScreenshot(context, "ic2-world-render");
        evidence.add("World screenshot: " + worldImage.toAbsolutePath());

        // Hold partial fluid levels still while the real chunk renderer draws the
        // comparison scene. This is the isolated test server, never a user save.
        world.getServer().runOnServer(server -> {
          server.tickRateManager().setFrozen(true);
          buildFluidScene(connection);
        });
        try {
          connection.waitForClientboundPackets();
          connection.waitForChunksRender();
          context.waitTicks(3);
          Path fluidImage = takeWorldScreenshot(context, "ic2-fluid-render");
          evidence.add("Fluid screenshot: " + fluidImage.toAbsolutePath());
          evidence.add("Fluid scene top row, left to right: steam, superheated steam, air, hydrogen, oxygen.");
          evidence.add("Fluid scene bottom row, left to right: water, lava, coolant, biogas.");
        } finally {
          world.getServer().runOnServer(server -> server.tickRateManager().setFrozen(false));
        }

        List<ItemStack> samples = context.computeOnClient(client -> {
          List<ItemStack> stacks = makeSamples();
          evidence.addAll(validateItems(client, stacks));
          evidence.addAll(RussianLocaleChecks.validateItemNames(stacks));
          return stacks;
        });
        context.setScreen(() -> new ItemGalleryScreen(samples));
        context.waitTicks(3);
        Path itemsImage = context.takeScreenshot(TestScreenshotOptions.of("ic2-item-render")
            .disableCounterPrefix());
        evidence.add("Item screenshot: " + itemsImage.toAbsolutePath());
        context.setScreen(() -> null);
        evidence.addAll(GuiRenderChecks.validate(context, world.getServer(), connection));
        evidence.addAll(RecipeBookRenderChecks.validate(context, world.getServer(), connection));
        evidence.addAll(ShapedRecipeBookRenderChecks.validate(context, world.getServer(), connection));
        evidence.addAll(AllRecipeBookRenderChecks.validate(context, world.getServer(), connection));
        evidence.addAll(DrillMiningRenderChecks.validate(context, world.getServer(), connection));
        evidence.addAll(WoodFuelRenderChecks.validate(context, world.getServer(), connection));
        evidence.addAll(ElectricArmorRenderChecks.validate(context, world.getServer(), connection));
        evidence.addAll(RadiationEffectRenderChecks.validate(context, world.getServer(), connection));
        evidence.addAll(GuiTexturePrecisionChecks.validate(context));
        evidence.addAll(MachineProgressRenderChecks.validate(context, world.getServer(), connection));
        evidence.add("Temporary flat world: " + world.getWorldSave().getSaveDirectory().toAbsolutePath());
        evidence.add("PASS: nonempty geometry and no missing sprites in all sampled block and item models.");
        try {
          Files.write(gameDirectory.resolve("render-validation.txt"), evidence);
        } catch (java.io.IOException exception) {
          throw new AssertionError("Could not save client render validation", exception);
        }
      }
    } finally {
      try {
        if (previousLanguage != null) RussianLocaleChecks.restore(context, previousLanguage);
      } finally {
        context.runOnClient(client -> client.options.guiScale().set(previousWindow[2]));
        context.getInput().resizeWindow(previousWindow[0], previousWindow[1]);
        context.runOnClient(Minecraft::resizeGui);
        context.waitTicks(2);
      }
    }
  }

  private static Path takeWorldScreenshot(ClientGameTestContext context, String name) {
    boolean wasHidden = context.computeOnClient(client -> client.gui.hud.isHidden());
    context.runOnClient(client -> {
      if (!client.gui.hud.isHidden()) client.gui.hud.toggle();
    });
    try {
      context.waitTicks(2);
      return context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
    } finally {
      context.runOnClient(client -> {
        if (client.gui.hud.isHidden() != wasHidden) client.gui.hud.toggle();
      });
    }
  }

  private static void buildScene(TestServerConnection connection) {
    var level = connection.getServerLevel();
    var player = connection.getServerPlayer();
    for (int x = -8; x <= 8; x++) for (int z = -5; z <= 15; z++) {
      level.setBlock(new BlockPos(x, 63, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
    }
    for (int i = 0; i < MACHINE_IDS.size(); i++) {
      level.setBlock(new BlockPos(-5 + i * 2, 64, 0), sampleBlock(MACHINE_IDS.get(i)), Block.UPDATE_ALL);
      level.setBlock(new BlockPos(-5 + i * 2, 64, -3), sampleBlock(RESOURCE_IDS.get(i)), Block.UPDATE_ALL);
    }
    level.setBlock(new BlockPos(-5, 64, 3), sampleBlock("ic2:generator"), Block.UPDATE_ALL);
    level.setBlock(new BlockPos(5, 64, 3), sampleBlock("ic2:batbox"), Block.UPDATE_ALL);
    for (int x = -4; x <= 4; x++) {
      level.setBlock(new BlockPos(x, 64, 3), sampleBlock("ic2:insulated_copper_cable"), Block.UPDATE_ALL);
    }
    ArmorStand quantum = new ArmorStand(level, -6.5, 64, 0.5);
    quantum.setShowArms(true);
    quantum.setItemSlot(EquipmentSlot.HEAD, item("ic2:quantum_helmet"));
    quantum.setItemSlot(EquipmentSlot.CHEST, item("ic2:quantum_chestplate"));
    quantum.setItemSlot(EquipmentSlot.LEGS, item("ic2:quantum_leggings"));
    quantum.setItemSlot(EquipmentSlot.FEET, item("ic2:quantum_boots"));
    quantum.setItemSlot(EquipmentSlot.MAINHAND, item("ic2:diamond_drill"));
    level.addFreshEntity(quantum);
    player.setGameMode(GameType.CREATIVE);
    player.getAbilities().flying = true;
    player.onUpdateAbilities();
    player.teleportTo(level, 0.5, 67, 13.5, Set.of(), 180f, 18f, true);
  }

  private static void buildFluidScene(TestServerConnection connection) {
    var level = connection.getServerLevel();
    List<List<String>> rows = List.of(
        List.of("ic2:steam", "ic2:superheated_steam", "ic2:air", "ic2:hydrogen", "ic2:oxygen"),
        List.of("minecraft:water", "minecraft:lava", "ic2:coolant", "ic2:biogas"));
    for (int row = 0; row < rows.size(); row++) for (int i = 0; i < rows.get(row).size(); i++) {
      BlockPos pos = new BlockPos(-6 + i * 3, 67 - row * 3, 32);
      for (int dy = -1; dy <= 1; dy++) {
        level.setBlock(pos.offset(0, dy, -1), sampleBlock("minecraft:white_concrete"), Block.UPDATE_ALL);
      }
      level.setBlock(pos.below(), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
      level.setBlock(pos.above(), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
      var fluid = BuiltInRegistries.FLUID.getValue(Identifier.parse(rows.get(row).get(i)));
      require(fluid instanceof net.minecraft.world.level.material.FlowingFluid, "Missing scene fluid");
      var state = ((net.minecraft.world.level.material.FlowingFluid) fluid).getFlowing(4, false);
      level.setBlock(pos, state.createLegacyBlock(), Block.UPDATE_ALL);
    }
    connection.getServerPlayer().teleportTo(level, 0.5, 67, 44.5, Set.of(), 180f, 5f, true);
  }

  private static BlockState sampleBlock(String id) {
    Identifier key = Identifier.parse(id);
    Block block = BuiltInRegistries.BLOCK.getValue(key);
    require(block != null && block != Blocks.AIR, "Missing registered block " + id);
    BlockState state = block.defaultBlockState();
    if (block instanceof Ic2TileEntityBlock machine && machine.facingProperty != null
        && machine.facingProperty.getPossibleValues().contains(Direction.SOUTH)) {
      state = state.setValue(machine.facingProperty, Direction.SOUTH);
    }
    return state;
  }

  private static List<String> validateBlocks(Minecraft client) {
    List<String> result = new ArrayList<>();
    List<String> ids = new ArrayList<>(MACHINE_IDS);
    ids.addAll(RESOURCE_IDS);
    ids.add("ic2:insulated_copper_cable");
    for (String id : ids) {
      BlockState state = sampleBlock(id);
      var model = client.getModelManager().getBlockStateModelSet().get(state);
      require(model != client.getModelManager().getBlockStateModelSet().missingModel(), "Missing block model " + id);
      List<BlockStateModelPart> parts = new ArrayList<>();
      model.collectParts(RandomSource.create(42), parts);
      int quads = 0;
      for (var part : parts) for (int face = 0; face < 7; face++) {
        for (var quad : part.getQuads(face == 6 ? null : Direction.values()[face])) {
          requireSprite(quad.materialInfo().sprite(), id);
          quads++;
        }
      }
      require(quads > 0, "Invisible block model " + id);
      result.add("Block " + id + ": " + quads + " valid quads");
    }
    return result;
  }

  private static List<ItemStack> makeSamples() {
    List<ItemStack> stacks = new ArrayList<>();
    for (String id : ITEM_IDS) stacks.add(item(id));
    ItemStack charged = item("ic2:re_battery");
    ElectricItem.manager.charge(charged, Double.MAX_VALUE, Integer.MAX_VALUE, true, false);
    stacks.add(charged);
    ItemStack obscurator = item("ic2:obscurator");
    CompoundTag sample = new CompoundTag();
    sample.putString("refBlock", "minecraft:bricks");
    sample.putString("refVariant", "");
    sample.putByte("refSide", (byte) Direction.SOUTH.ordinal());
    sample.putIntArray("refColorMuls", new int[] {-1});
    StackUtil.setTag(obscurator, sample);
    stacks.add(obscurator);
    return stacks;
  }

  private static List<String> validateItems(Minecraft client, List<ItemStack> stacks) {
    List<String> result = new ArrayList<>();
    // Test-only reflection inspects the renderer's actual submitted layers, including every sprite.
    try {
      Field layers = ItemStackRenderState.class.getDeclaredField("layers");
      Field layerCount = ItemStackRenderState.class.getDeclaredField("activeLayerCount");
      Field layerQuads = ItemStackRenderState.LayerRenderState.class.getDeclaredField("quads");
      layers.setAccessible(true);
      layerCount.setAccessible(true);
      layerQuads.setAccessible(true);
      for (ItemStack stack : stacks) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        require(!(client.getModelManager().getItemModel(BuiltInRegistries.ITEM.getKey(stack.getItem())) instanceof MissingItemModel),
            "Missing item definition " + id);
        var render = new ItemStackRenderState();
        client.getItemModelResolver().updateForTopItem(render, stack, ItemDisplayContext.GUI, client.level, client.player, 42);
        require(!render.isEmpty(), "Invisible item model " + id);
        requireSprite(render.pickParticleMaterial(RandomSource.create(42)).sprite(), id);
        var active = (ItemStackRenderState.LayerRenderState[]) layers.get(render);
        int quads = 0;
        for (int i = 0; i < layerCount.getInt(render); i++) {
          ItemQuads geometry = (ItemQuads) layerQuads.get(active[i]);
          for (var quad : geometry.all()) {
            requireSprite(quad.materialInfo().sprite(), id);
            quads++;
          }
        }
        require(quads > 0, "No item geometry " + id);
        result.add("Item " + id + ": " + quads + " valid quads");
      }
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError("26.3 render layer inspection failed", exception);
    }
    return result;
  }

  private static ItemStack item(String id) {
    var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    require(item != null && item != Items.AIR, "Missing registered item " + id);
    return new ItemStack(item);
  }
  private static void requireSprite(TextureAtlasSprite sprite, String id) {
    require(sprite != null && !sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()),
        "Missing texture in " + id);
  }
  private static void require(boolean valid, String message) {
    if (!valid) throw new AssertionError(message);
  }

  private static final class ItemGalleryScreen extends Screen {
    private final List<ItemStack> stacks;
    ItemGalleryScreen(List<ItemStack> stacks) {
      super(Component.literal(RussianLocaleChecks.isRussianRequested()
          ? "Проверка отображения IC2 Fabric 26.3" : "IC2 Fabric 26.3 render validation"));
      this.stacks = stacks;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      graphics.fill(0, 0, width, height, 0xff17212e);
      graphics.text(font, RussianLocaleChecks.isRussianRequested()
          ? "IC2 Fabric 26.3 — русские названия предметов" : "IC2 Fabric 26.3 - loaded item models",
          18, 16, 0xffffffff);
      int columns = 3;
      int columnWidth = (width - 36) / columns;
      require(45 + ((stacks.size() - 1) / columns) * 35 + 16 <= height - 18,
          "Item gallery extends beyond the current screen; viewport and layout must be resized together");
      for (int i = 0; i < stacks.size(); i++) {
        int x = 18 + (i % columns) * columnWidth;
        int y = 45 + (i / columns) * 35;
        ItemStack stack = stacks.get(i);
        graphics.item(stack, x, y);
        String name = RussianLocaleChecks.isRussianRequested()
            ? stack.getHoverName().getString() : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (i == stacks.size() - 2) name += RussianLocaleChecks.isRussianRequested() ? " (заряжен)" : " (charged)";
        if (i == stacks.size() - 1) name += RussianLocaleChecks.isRussianRequested() ? " (образец кирпича)" : " (brick sample)";
        int availableWidth = columnWidth - 29;
        if (font.width(name) <= availableWidth) {
          graphics.text(font, name, x + 23, y + 5, 0xffe4eaf1);
        } else {
          var lines = font.split(Component.literal(name), availableWidth);
          require(lines.size() <= 3, "Item name exceeds its gallery cell: " + name);
          for (int line = 0; line < lines.size(); line++) {
            graphics.text(font, lines.get(line), x + 23, y - 3 + line * 10, 0xffe4eaf1);
          }
        }
      }
    }
  }
}
