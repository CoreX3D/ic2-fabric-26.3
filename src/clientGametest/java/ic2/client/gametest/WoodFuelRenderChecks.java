package ic2.client.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import ic2.core.IHasGui;
import ic2.core.Ic2Gui;
import ic2.core.block.comp.Energy;
import ic2.core.block.generator.tileentity.TileEntityGenerator;
import ic2.core.ref.Ic2Blocks;
import ic2.core.ref.Ic2Items;
import ic2.core.slot.SlotInvSlot;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Real client slot clicks and server combustion, exclusively in the disposable test world. */
@Environment(EnvType.CLIENT)
@SuppressWarnings("UnstableApiUsage")
public final class WoodFuelRenderChecks {
  private static final BlockPos POSITION = new BlockPos(-28, 64, 48);

  private WoodFuelRenderChecks() {}

  public static List<String> validate(ClientGameTestContext context, TestServerContext server,
      TestServerConnection connection) {
    Path directory = context.computeOnClient(client -> client.gameDirectory.toPath().toAbsolutePath().normalize());
    require(directory.endsWith(Path.of("build", "client-render-smoke")),
        "Wood fuel fixtures are permitted only in build/client-render-smoke");
    SavedPlayer saved = server.computeOnServer(instance -> {
      var player = connection.getServerPlayer();
      return new SavedPlayer(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
          player.gameMode.getGameModeForPlayer());
    });
    List<String> evidence = new ArrayList<>();
    boolean fixtureCreated = false;
    try {
      server.runOnServer(instance -> {
        var level = connection.getServerLevel();
        require(level.isEmptyBlock(POSITION), "Wood fuel fixture would overwrite another scene");
        for (BlockPos floor : platform()) require(level.isEmptyBlock(floor),
            "Wood fuel platform would overwrite another scene: " + floor);
      });
      fixtureCreated = true;
      server.runOnServer(instance -> {
        var level = connection.getServerLevel();
        var player = connection.getServerPlayer();
        for (BlockPos floor : platform()) level.setBlock(floor, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(POSITION, Ic2Blocks.GENERATOR.defaultBlockState(), Block.UPDATE_ALL);
        var generator = generator(connection);
        generator.getComponent(Energy.class).addEnergy(4000);
        player.closeContainer();
        player.getInventory().clearContent();
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(level, POSITION.getX() + 0.5, POSITION.getY(), POSITION.getZ() + 2.5,
            Set.of(), 180.0f, 0.0f, true);
        player.inventoryMenu.broadcastChanges();
      });
      connection.waitForChunksDownload();
      connection.waitForClientboundPackets();
      int menuId = server.computeOnServer(instance -> {
        require(((IHasGui) generator(connection)).openManagedBe(connection.getServerPlayer(), InteractionHand.MAIN_HAND),
            "Server refused to open wood fuel generator");
        return connection.getServerPlayer().containerMenu.containerId;
      });
      context.waitFor(client -> matchesScreen(client, menuId));
      context.waitTicks(2);
      context.runOnClient(client -> {
        Slot fuel = fuelSlot(client);
        int checked = 0;
        for (Item item : BuiltInRegistries.ITEM) {
          ItemStack stack = new ItemStack(item);
          if ((stack.is(ItemTags.LOGS_THAT_BURN) || stack.is(ItemTags.PLANKS))
              && stack.has(DataComponents.COOKING_FUEL)) {
            require(fuel.mayPlace(stack), "Client generator rejects native wood fuel " + BuiltInRegistries.ITEM.getKey(item));
            checked++;
          }
        }
        require(checked >= 40, "Loaded native wood fuel tags are unexpectedly empty: " + checked);
        for (Item item : List.of(Ic2Items.RUBBER_LOG, Ic2Items.STRIPPED_RUBBER_LOG,
            Ic2Items.RUBBER_WOOD, Ic2Items.STRIPPED_RUBBER_WOOD, Ic2Items.RUBBER_PLANKS)) {
          require(fuel.mayPlace(new ItemStack(item)), "Client generator rejects rubber wood "
              + BuiltInRegistries.ITEM.getKey(item));
        }
        for (Item item : List.of(Items.COBBLESTONE, Items.CRIMSON_PLANKS, Items.WARPED_PLANKS)) {
          require(!fuel.mayPlace(new ItemStack(item)), "Client generator accepts nonfuel " + BuiltInRegistries.ITEM.getKey(item));
        }
        evidence.add("Wood fuel client slots: " + checked + " native burnable logs/planks and all five IC rubber wood items accepted; "
            + "cobblestone and nonburnable crimson/warped planks rejected.");
      });

      for (Item item : List.of(Items.BIRCH_LOG, Items.SPRUCE_LOG, Ic2Items.RUBBER_LOG, Ic2Items.RUBBER_PLANKS)) {
        server.runOnServer(instance -> {
          var generator = generator(connection);
          generator.fuel = 0;
          generator.totalFuel = 0;
          generator.fuelSlot.clear();
          var energy = generator.getComponent(Energy.class);
          energy.addEnergy(energy.getCapacity());
          var player = connection.getServerPlayer();
          player.getInventory().clearContent();
          player.getInventory().setItem(9, new ItemStack(item));
          player.containerMenu.broadcastChanges();
        });
        connection.waitForClientboundPackets();
        context.waitFor(client -> matchesScreen(client, menuId) && inventorySlot(client).getItem().is(item)
            && fuelSlot(client).getItem().isEmpty());
        clickSlot(context, inventorySlotIndex(context));
        context.waitFor(client -> client.player.containerMenu.getCarried().is(item));
        clickSlot(context, fuelSlotIndex(context));
        connection.waitForServerboundPackets();
        server.runOnServer(instance -> {
          var generator = generator(connection);
          require(generator.fuelSlot.get().is(item) && generator.fuelSlot.get().getCount() == 1,
              "Native generator GUI clicks failed to insert " + BuiltInRegistries.ITEM.getKey(item));
          require(connection.getServerPlayer().getInventory().getItem(9).isEmpty()
                  && connection.getServerPlayer().containerMenu.getCarried().isEmpty(),
              "GUI insertion left duplicate/carried fuel " + BuiltInRegistries.ITEM.getKey(item));
        });
        connection.waitForClientboundPackets();
        context.waitFor(client -> fuelSlot(client).getItem().is(item)
            && client.player.containerMenu.getCarried().isEmpty());
        if (item == Ic2Items.RUBBER_PLANKS) {
          context.waitTicks(2);
          evidence.add("Wood fuel GUI screenshot: " + context.takeScreenshot(
              TestScreenshotOptions.of("ic2-generator-rubber-fuel").disableCounterPrefix()).toAbsolutePath());
        }
        server.runOnServer(instance -> {
          var energy = generator(connection).getComponent(Energy.class);
          require(energy.useEnergy(energy.getEnergy()), "Could not empty isolated generator buffer");
        });
        server.waitFor(instance -> generator(connection).fuelSlot.isEmpty()
            && generator(connection).fuel > 0 && generator(connection).getComponent(Energy.class).getEnergy() > 0);
        evidence.add("Wood fuel GUI: real inventory/fuel-slot mouse clicks inserted " + BuiltInRegistries.ITEM.getKey(item)
            + "; server consumed exactly one item and generated EU.");
      }
      return evidence;
    } finally {
      context.runOnClient(client -> {
        if (client.player != null) client.player.closeContainer();
      });
      connection.waitForServerboundPackets();
      boolean removeFixture = fixtureCreated;
      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        player.closeContainer();
        player.getInventory().clearContent();
        if (removeFixture) {
          connection.getServerLevel().setBlock(POSITION, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
          for (BlockPos floor : platform()) connection.getServerLevel().setBlock(floor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        player.setGameMode(saved.mode());
        player.teleportTo(connection.getServerLevel(), saved.x(), saved.y(), saved.z(), Set.of(),
            saved.yaw(), saved.pitch(), true);
      });
      connection.waitForClientboundPackets();
      context.waitTick();
    }
  }

  private static int inventorySlotIndex(ClientGameTestContext context) {
    return context.computeOnClient(client -> inventorySlot(client).index);
  }

  private static int fuelSlotIndex(ClientGameTestContext context) {
    return context.computeOnClient(client -> fuelSlot(client).index);
  }

  private static void clickSlot(ClientGameTestContext context, int index) {
    double[] cursor = context.computeOnClient(client -> {
      var screen = (Ic2Gui<?>) client.gui.screen();
      var slot = client.player.containerMenu.getSlot(index);
      var window = client.getWindow();
      return new double[] {(screen.getX() + slot.x + 8.0) * window.getScreenWidth() / window.getGuiScaledWidth(),
          (screen.getY() + slot.y + 8.0) * window.getScreenHeight() / window.getGuiScaledHeight()};
    });
    context.getInput().setCursorPos(cursor[0], cursor[1]);
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
  }

  private static Slot inventorySlot(Minecraft client) {
    return client.player.containerMenu.slots.stream().filter(slot -> slot.container == client.player.getInventory()
        && slot.getContainerSlot() == 9).findFirst().orElseThrow(() -> new AssertionError("Missing player inventory GUI slot"));
  }

  private static Slot fuelSlot(Minecraft client) {
    return client.player.containerMenu.slots.stream().filter(slot -> slot instanceof SlotInvSlot inventory
        && inventory.invSlot == ((TileEntityGenerator) ((Ic2Gui<?>) client.gui.screen()).getContainer().base).fuelSlot)
        .findFirst().orElseThrow(() -> new AssertionError("Missing generator GUI fuel slot"));
  }

  private static boolean matchesScreen(Minecraft client, int menuId) {
    return client.gui.screen() instanceof Ic2Gui<?> screen && screen.getContainer().containerId == menuId
        && screen.getContainer().base instanceof TileEntityGenerator generator && generator.getBlockPos().equals(POSITION);
  }

  private static TileEntityGenerator generator(TestServerConnection connection) {
    var machine = connection.getServerLevel().getBlockEntity(POSITION);
    require(machine instanceof TileEntityGenerator, "Missing isolated wood fuel generator");
    return (TileEntityGenerator) machine;
  }

  private static List<BlockPos> platform() {
    List<BlockPos> result = new ArrayList<>();
    for (int x = -1; x <= 1; x++) for (int z = 0; z <= 3; z++) result.add(POSITION.offset(x, -1, z));
    return result;
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  private record SavedPlayer(double x, double y, double z, float yaw, float pitch, GameType mode) {}
}
