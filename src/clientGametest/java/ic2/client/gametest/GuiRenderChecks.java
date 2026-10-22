package ic2.client.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import ic2.core.IHasGui;
import ic2.core.Ic2Gui;
import ic2.core.block.comp.Energy;
import ic2.core.block.generator.tileentity.TileEntityGenerator;
import ic2.core.block.machine.gui.GuiMetalFormer;
import ic2.core.block.machine.tileentity.TileEntityMacerator;
import ic2.core.block.machine.tileentity.TileEntityMetalFormer;
import ic2.core.block.tileentity.Ic2TileEntity;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Real menus and payloads, exclusively in the isolated client render test world. */
@Environment(EnvType.CLIENT)
@SuppressWarnings("UnstableApiUsage")
public final class GuiRenderChecks {
  private static final List<String> MACHINES = List.of("generator", "macerator", "metal_former");
  private static final double INITIAL_ENERGY = 500.0;

  private GuiRenderChecks() {}

  public static List<String> validate(ClientGameTestContext context, TestServerContext server,
      TestServerConnection connection) {
    Path directory = context.computeOnClient(client -> client.gameDirectory.toPath().toAbsolutePath().normalize());
    require(directory.endsWith(Path.of("build", "client-render-smoke")),
        "GUI fixtures are permitted only in build/client-render-smoke");
    context.runOnClient(client -> require(client.gui.screen() == null,
        "Close the preceding test screen before GUI validation"));
    SavedPlayer original = server.computeOnServer(instance -> {
      var player = connection.getServerPlayer();
      require(player.containerMenu == player.inventoryMenu, "A preceding server menu is still open");
      return new SavedPlayer(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    });
    List<String> evidence = new ArrayList<>();
    boolean fixturesCreated = false;
    try {
      server.runOnServer(instance -> {
        var level = connection.getServerLevel();
        for (int i = 0; i < MACHINES.size(); i++) {
          require(level.isEmptyBlock(position(i)) && level.isEmptyBlock(position(i).below()),
              "GUI fixture would overwrite another test scene at " + position(i));
        }
      });
      fixturesCreated = true;
      server.runOnServer(instance -> {
        var level = connection.getServerLevel();
        for (int i = 0; i < MACHINES.size(); i++) {
          BlockPos pos = position(i);
          var block = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("ic2", MACHINES.get(i)));
          require(block != null && block != Blocks.AIR, "Missing GUI fixture block " + MACHINES.get(i));
          level.setBlock(pos.below(), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
          level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
          var machine = (Ic2TileEntity) level.getBlockEntity(pos);
          require(machine != null, "Missing fixture block entity at " + pos);
          machine.getComponent(Energy.class).addEnergy(INITIAL_ENERGY);
          if (machine instanceof TileEntityGenerator generator) generator.totalFuel = 197;
          // A nondefault initial value proves the first field packet was applied.
          if (machine instanceof TileEntityMetalFormer former) former.setMode(1);
        }
      });
      context.waitTick();
      connection.waitForClientboundPackets();

      for (int i = 0; i < MACHINES.size(); i++) {
        BlockPos pos = position(i);
        String name = MACHINES.get(i);
        server.runOnServer(instance -> {
          var player = connection.getServerPlayer();
          player.teleportTo(connection.getServerLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5,
              Set.of(), 180.0f, 0.0f, true);
        });
        connection.waitForChunksDownload();
        connection.waitForClientboundPackets();
        int menuId = server.computeOnServer(instance -> {
          var machine = (IHasGui) connection.getServerLevel().getBlockEntity(pos);
          require(machine.openManagedBe(connection.getServerPlayer(), InteractionHand.MAIN_HAND),
              "Server refused to open " + name);
          return connection.getServerPlayer().containerMenu.containerId;
        });
        context.waitFor(client -> matchesScreen(client, pos, menuId));
        context.waitTicks(2);
        connection.waitForClientboundPackets();
        context.waitFor(client -> {
          if (!matchesScreen(client, pos, menuId)) return false;
          var machine = (Ic2TileEntity) ((Ic2Gui<?>) client.gui.screen()).getContainer().base;
          if (Math.abs(machine.getComponent(Energy.class).getEnergy() - INITIAL_ENERGY) > 0.001) return false;
          return switch (machine) {
            case TileEntityGenerator generator -> generator.totalFuel == 197;
            case TileEntityMacerator ignored -> true;
            case TileEntityMetalFormer former -> former.getMode() == 1;
            default -> false;
          };
        });
        context.waitTicks(3);
        evidence.add("GUI " + name + ": real server menu and energy/field packets received; screenshot: "
            + screenshot(context, "ic2-gui-" + name));
        if (name.equals("metal_former")) {
          clickMetalFormerMode(context, server, connection, pos);
          evidence.add("GUI metal_former: real mouse click changed server mode 1 -> 2 and synchronized the client; screenshot: "
              + screenshot(context, "ic2-gui-metal_former-cutting"));
        }
        // Close through the screen action so a queued key repeat cannot reopen the pause menu.
        context.runOnClient(client -> client.gui.screen().onClose());
        context.waitForScreen(null);
        connection.waitForServerboundPackets();
        server.runOnServer(instance -> require(connection.getServerPlayer().containerMenu
            == connection.getServerPlayer().inventoryMenu, "Server menu did not close for " + name));
      }
      return evidence;
    } finally {
      context.runOnClient(client -> {
        if (client.player != null) client.player.closeContainer();
      });
      connection.waitForServerboundPackets();
      boolean removeFixtures = fixturesCreated;
      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        player.closeContainer();
        if (removeFixtures) for (int i = 0; i < MACHINES.size(); i++) {
          connection.getServerLevel().setBlock(position(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
          connection.getServerLevel().setBlock(position(i).below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        player.teleportTo(connection.getServerLevel(), original.x(), original.y(), original.z(), Set.of(),
            original.yaw(), original.pitch(), true);
      });
      context.waitTick();
      connection.waitForClientboundPackets();
    }
  }

  private static void clickMetalFormerMode(ClientGameTestContext context, TestServerContext server,
      TestServerConnection connection, BlockPos pos) {
    double[] cursor = context.computeOnClient(client -> {
      require(client.gui.screen() instanceof GuiMetalFormer, "Metal former has the wrong screen");
      var screen = (GuiMetalFormer) client.gui.screen();
      var window = client.getWindow();
      // Center of the actual 20x20 mode button (65,53), converted to physical cursor coordinates.
      return new double[] {(screen.getX() + 75.0) * window.getScreenWidth() / window.getGuiScaledWidth(),
          (screen.getY() + 63.0) * window.getScreenHeight() / window.getGuiScaledHeight()};
    });
    context.getInput().setCursorPos(cursor[0], cursor[1]);
    context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    connection.waitForServerboundPackets();
    server.waitFor(instance -> ((TileEntityMetalFormer) connection.getServerLevel().getBlockEntity(pos)).getMode() == 2);
    context.waitTicks(2);
    connection.waitForClientboundPackets();
    context.waitFor(client -> client.gui.screen() instanceof GuiMetalFormer screen
        && screen.getContainer().base.getMode() == 2);
    context.waitTicks(3);
  }

  private static boolean matchesScreen(Minecraft client, BlockPos pos, int menuId) {
    return client.gui.screen() instanceof Ic2Gui<?> screen
        && screen.getContainer().containerId == menuId
        && screen.getContainer().base instanceof Ic2TileEntity machine
        && machine.getBlockPos().equals(pos);
  }

  private static Path screenshot(ClientGameTestContext context, String name) {
    return context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix()).toAbsolutePath();
  }

  private static BlockPos position(int index) { return new BlockPos(-2 + index * 2, 64, 40); }
  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
  private record SavedPlayer(double x, double y, double z, float yaw, float pitch) {}
}
