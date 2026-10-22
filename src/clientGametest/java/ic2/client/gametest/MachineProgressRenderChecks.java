package ic2.client.gametest;

import ic2.core.IHasGui;
import ic2.core.Ic2Gui;
import ic2.core.block.comp.Energy;
import ic2.core.block.machine.tileentity.TileEntityMetalFormer;
import ic2.core.block.machine.tileentity.TileEntityStandardMachine;
import ic2.core.event.IWorldTickCallback;
import ic2.core.event.TickHandler;
import ic2.core.ref.Ic2Blocks;
import ic2.core.ref.Ic2Items;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector2f;

/** Real powered machine cycles and the native GUI quads extracted on every rendered frame. */
@Environment(EnvType.CLIENT)
@SuppressWarnings("UnstableApiUsage")
public final class MachineProgressRenderChecks {
  private static final List<MachineCase> MACHINES = List.of(
      new MachineCase("metal_former", new BlockPos(-52, 64, 48), Ic2Blocks.METAL_FORMER,
          Items.COPPER_INGOT, Ic2Items.COPPER_PLATE, 52, 39, 200, 19, 46, 9),
      new MachineCase("macerator", new BlockPos(-58, 64, 48), Ic2Blocks.MACERATOR,
          Items.COPPER_INGOT, Ic2Items.COPPER_DUST, 80, 38, 165, 52, 21, 11));
  private static final double EPSILON = 0.00015;

  private MachineProgressRenderChecks() {}

  public static List<String> validate(ClientGameTestContext context, TestServerContext server,
      TestServerConnection connection) {
    Path directory = context.computeOnClient(client -> client.gameDirectory.toPath().toAbsolutePath().normalize());
    require(directory.endsWith(Path.of("build", "client-render-smoke")),
        "Machine progress fixtures are permitted only in build/client-render-smoke");
    SavedPlayer saved = server.computeOnServer(instance -> {
      var player = connection.getServerPlayer();
      require(player.containerMenu == player.inventoryMenu, "A preceding native machine menu is still open");
      return new SavedPlayer(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    });
    List<String> evidence = new ArrayList<>();
    List<String> csv = new ArrayList<>(List.of("machine,phase,frame_ns,server_tick,server_progress,server_output,"
        + "client_tick,client_progress,client_output,draw_width,source_width,draw_height,source_height,first_texel_error"));
    boolean fixturesCreated = false;
    try {
      server.runOnServer(instance -> {
        for (MachineCase spec : MACHINES) {
          require(connection.getServerLevel().isEmptyBlock(spec.position()), "Machine progress fixture would overwrite a block");
          for (BlockPos position : platform(spec.position())) require(connection.getServerLevel().isEmptyBlock(position),
              "Machine progress platform would overwrite another scene: " + position);
        }
      });
      fixturesCreated = true;
      server.runOnServer(instance -> {
        var level = connection.getServerLevel();
        for (MachineCase spec : MACHINES) {
          for (BlockPos position : platform(spec.position())) level.setBlock(position, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
          level.setBlock(spec.position(), spec.block().defaultBlockState(), Block.UPDATE_ALL);
        }
      });
      context.waitTicks(3);
      for (MachineCase spec : MACHINES) evidence.addAll(validateMachine(context, server, connection, spec, csv));
      Path series = directory.resolve("machine-progress-samples.csv");
      try { Files.write(series, csv); }
      catch (IOException exception) { throw new AssertionError("Could not save native machine progress time series", exception); }
      evidence.add("Machine progress native time series: " + series.toAbsolutePath()
          + "; rows retain each changed server tick, progress value or extracted geometry; repeated identical frames omitted.");
      return evidence;
    } finally {
      context.runOnClient(client -> {
        if (client.gui.screen() != null) client.gui.screen().onClose();
      });
      connection.waitForServerboundPackets();
      boolean removeFixtures = fixturesCreated;
      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        player.closeContainer();
        if (removeFixtures) for (MachineCase spec : MACHINES) {
          connection.getServerLevel().setBlock(spec.position(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
          for (BlockPos position : platform(spec.position())) connection.getServerLevel()
              .setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        player.teleportTo(connection.getServerLevel(), saved.x(), saved.y(), saved.z(), Set.of(),
            saved.yaw(), saved.pitch(), true);
      });
      connection.waitForClientboundPackets();
      context.waitTick();
    }
  }

  private static List<String> validateMachine(ClientGameTestContext context, TestServerContext server,
      TestServerConnection connection, MachineCase spec, List<String> csv) {
    List<String> evidence = new ArrayList<>();
    ConcurrentLinkedQueue<ServerSample> serverSamples = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<FrameSample> frames = new ConcurrentLinkedQueue<>();
    AtomicReference<ServerSample> latestServer = new AtomicReference<>(new ServerSample(0, 0.0, 0));
    AtomicReference<String> phase = new AtomicReference<>("ready");
    AtomicReference<FrameSample> lastFrame = new AtomicReference<>();
    AtomicInteger renderedFrames = new AtomicInteger();
    AtomicInteger fractionalFrames = new AtomicInteger();
    AtomicBoolean recording = new AtomicBoolean(true);
    IWorldTickCallback sampler = level -> {
      if (!recording.get()) return;
      var machine = machine(connection, spec);
      ServerSample sample = new ServerSample(level.getGameTime(), machine.getProgress(), countOutput(machine));
      latestServer.set(sample);
      serverSamples.add(sample);
    };
    server.runOnServer(instance -> {
      var player = connection.getServerPlayer();
      player.teleportTo(connection.getServerLevel(), spec.position().getX() + 0.5, spec.position().getY(),
          spec.position().getZ() + 2.5, Set.of(), 180.0F, 0.0F, true);
      var machine = machine(connection, spec);
      if (machine instanceof TileEntityMetalFormer former) former.setMode(1);
      machine.inputSlot.clear();
      machine.outputSlot.clear();
      machine.dischargeSlot.clear();
      machine.getComponent(Energy.class).addEnergy(machine.getComponent(Energy.class).getCapacity());
      TickHandler.requestContinuousWorldTick(connection.getServerLevel(), sampler);
    });
    try {
      connection.waitForChunksDownload();
      connection.waitForClientboundPackets();
      int menuId = server.computeOnServer(instance -> {
        require(((IHasGui) machine(connection, spec)).openManagedBe(connection.getServerPlayer(), InteractionHand.MAIN_HAND),
            "Server refused to open live " + spec.name() + " menu");
        return connection.getServerPlayer().containerMenu.containerId;
      });
      context.waitFor(client -> matchesScreen(client, spec, menuId));
      context.runOnClient(client -> {
        var screen = (Ic2Gui<?>) client.gui.screen();
        ScreenEvents.afterExtract(screen).register((currentScreen, graphics, mouseX, mouseY, delta) -> {
          if (!recording.get()) return;
          var machine = (TileEntityStandardMachine<?, ?, ?>) screen.getContainer().base;
          AtomicReference<BlitRenderState> fill = new AtomicReference<>();
          graphics.guiRenderState.forEachElement(element -> {
            if (element instanceof BlitRenderState blit
                && near(blit.u0() * 256.0, spec.u()) && near(blit.v0() * 256.0, spec.v())
                && near((blit.v1() - blit.v0()) * 256.0, spec.height())) {
              Vector2f origin = blit.pose().transformPosition(blit.x0(), blit.y0(), new Vector2f());
              if (near(origin.x, screen.getX() + spec.x()) && near(origin.y, screen.getY() + spec.y())) fill.set(blit);
            }
          }, GuiRenderState.TraverseRange.ALL);
          double width = 0.0, sourceWidth = 0.0, height = 0.0, sourceHeight = 0.0, texelError = 0.0;
          BlitRenderState fillState = fill.get();
          if (fillState != null) {
            Vector2f start = fillState.pose().transformPosition(fillState.x0(), fillState.y0(), new Vector2f());
            Vector2f right = fillState.pose().transformPosition(fillState.x1(), fillState.y0(), new Vector2f());
            Vector2f bottom = fillState.pose().transformPosition(fillState.x0(), fillState.y1(), new Vector2f());
            width = right.distance(start);
            height = bottom.distance(start);
            sourceWidth = (fillState.u1() - fillState.u0()) * 256.0;
            sourceHeight = (fillState.v1() - fillState.v0()) * 256.0;
            if (width > 0.5) {
              // The fixed first pixel center must keep sampling the same texel as the edge advances.
              double firstPixel = screen.getX() + spec.x() + 0.5;
              double actualTexel = fillState.u0() * 256.0 + (firstPixel - start.x) * sourceWidth / width;
              texelError = actualTexel - (spec.u() + 0.5);
            }
            if (Math.abs(width - Math.rint(width)) > 0.0005) fractionalFrames.incrementAndGet();
          }
          renderedFrames.incrementAndGet();
          FrameSample frame = new FrameSample(phase.get(), System.nanoTime(), latestServer.get(),
              client.level.getGameTime(), machine.getProgress(), countOutput(machine), width, sourceWidth,
              height, sourceHeight, texelError);
          FrameSample previous = lastFrame.getAndSet(frame);
          if (previous == null || !frame.sameState(previous)) frames.add(frame);
        });
      });
      phase.set("running");
      server.runOnServer(instance -> {
        var machine = machine(connection, spec);
        machine.inputSlot.put(new ItemStack(spec.input()));
        machine.setChanged();
      });
      server.waitFor(instance -> machine(connection, spec).getProgress() >= 0.30F, 1200);
      context.waitFor(client -> clientMachine(client).getProgress() >= 0.25F);
      context.waitTicks(2);
      evidence.add("Machine progress live " + spec.name() + " screenshot: " + context.takeScreenshot(
          TestScreenshotOptions.of("ic2-progress-" + spec.name()).disableCounterPrefix()).toAbsolutePath());

      phase.set("power-stop");
      double stopped = server.computeOnServer(instance -> {
        var machine = machine(connection, spec);
        var energy = machine.getComponent(Energy.class);
        require(energy.useEnergy(energy.getEnergy()), "Could not empty isolated machine energy for pause control");
        require(machine.getProgress() > 0.0 && machine.getProgress() < 0.9, "Pause missed the live operation");
        return (double) machine.getProgress();
      });
      context.waitTicks(4);
      connection.waitForClientboundPackets();
      context.waitFor(client -> near(clientMachine(client).getProgress(), stopped));
      phase.set("power-stop-stable");
      context.waitTicks(12);
      server.runOnServer(instance -> {
        var machine = machine(connection, spec);
        require(near(machine.getProgress(), stopped) && !machine.getActive() && countOutput(machine) == 0,
            "Unpowered " + spec.name() + " advanced or completed its server recipe");
      });
      context.runOnClient(client -> require(near(clientMachine(client).getProgress(), stopped)
          && !clientMachine(client).getActive() && countOutput(clientMachine(client)) == 0,
          "Unpowered " + spec.name() + " invented progress in the native client GUI"));
      phase.set("resumed");
      server.runOnServer(instance -> {
        var energy = machine(connection, spec).getComponent(Energy.class);
        energy.addEnergy(energy.getCapacity());
      });
      server.waitFor(instance -> countOutput(machine(connection, spec)) == 1, 1200);
      server.runOnServer(instance -> {
        var machine = machine(connection, spec);
        require(machine.outputSlot.get().is(spec.output()) && machine.inputSlot.isEmpty()
            && near(machine.getProgress(), 0.0) && !machine.getActive(),
            "Native " + spec.name() + " cycle did not consume input and produce its exact result");
      });
      connection.waitForClientboundPackets();
      context.waitFor(client -> clientMachine(client).outputSlot.get().is(spec.output())
          && countOutput(clientMachine(client)) == 1 && near(clientMachine(client).getProgress(), 0.0));
      phase.set("completed");
      context.waitTicks(4);
      recording.set(false);

      List<ServerSample> serverSeries = List.copyOf(serverSamples);
      List<FrameSample> frameSeries = List.copyOf(frames);
      for (FrameSample frame : frameSeries) csv.add(frame.csv(spec.name()));
      // Preserve a failing native capture too, so a rendering regression remains inspectable.
      Path partialSeries = context.computeOnClient(client -> client.gameDirectory.toPath()
          .resolve("machine-progress-samples.csv"));
      try { Files.write(partialSeries, csv); }
      catch (IOException exception) { throw new AssertionError("Could not save native machine progress capture", exception); }
      require(renderedFrames.get() > 30 && fractionalFrames.get() > 10,
          "Too few actual native fractional gauge frames for " + spec.name() + ": " + fractionalFrames.get());
      List<Long> completionTicks = new ArrayList<>();
      for (int index = 1; index < serverSeries.size(); index++) {
        var before = serverSeries.get(index - 1);
        var after = serverSeries.get(index);
        if (after.progress() + EPSILON < before.progress()) {
          require(after.output() == before.output() + 1 && near(after.progress(), 0.0)
                  && before.progress() > 0.9,
              "Server " + spec.name() + " progress went backwards before completing a recipe");
          completionTicks.add(after.tick());
        }
      }
      require(completionTicks.size() == 1, "Server " + spec.name()
          + " did not show exactly one completed cycle reset: " + completionTicks.size());
      double maxSpanError = 0.0, maxTexelError = 0.0;
      int resets = 0;
      for (int index = 0; index < frameSeries.size(); index++) {
        FrameSample frame = frameSeries.get(index);
        if (index > 0 && frame.progress() + EPSILON < frameSeries.get(index - 1).progress()) {
          // TE progress applies at the client tick boundary; the inventory packet can follow it.
          // The server sampler also runs before that tick's machine update. Correlate this zero
          // reset with the adjacent real server completion, rather than same-frame output order.
          long serverCompletion = completionTicks.getFirst();
          require(near(frame.progress(), 0.0) && frameSeries.get(index - 1).progress() > 0.9
                  && Math.abs(frame.server().tick() - serverCompletion) <= 2,
              "Client " + spec.name() + " progress went backwards before completing a recipe");
          resets++;
        }
        if (frame.width() > 0.0) {
          maxSpanError = Math.max(maxSpanError, Math.abs(frame.width() - frame.sourceWidth()));
          maxTexelError = Math.max(maxTexelError, Math.abs(frame.texelError()));
          require(near(frame.width(), frame.sourceWidth()) && near(frame.height(), frame.sourceHeight()),
              "Native " + spec.name() + " fractional gauge distorted its texture span: " + frame);
          require(near(frame.width(), frame.progress() * spec.width()) && near(frame.texelError(), 0.0),
              "Native " + spec.name() + " texture interior shifted while the progress edge advanced: " + frame);
        }
      }
      require(resets == 1, "Client " + spec.name() + " did not show exactly one completed cycle reset: " + resets);
      evidence.add("Machine progress native " + spec.name() + ": " + renderedFrames.get() + " extracted frames, "
          + fractionalFrames.get() + " fractional widths, " + serverSeries.size() + " server tick samples; "
          + "one real copper operation completed with exact output and one legitimate reset. A 12-tick energy stop "
          + "held progress on server/client before resuming. Max source/destination span error=" + maxSpanError
          + ", max fixed interior texel drift=" + maxTexelError + ".");
      return evidence;
    } finally {
      recording.set(false);
      server.runOnServer(instance -> TickHandler.removeContinuousWorldTick(connection.getServerLevel(), sampler));
      context.runOnClient(client -> {
        if (client.gui.screen() != null) client.gui.screen().onClose();
      });
      connection.waitForServerboundPackets();
      context.waitForScreen(null);
    }
  }

  private static TileEntityStandardMachine<?, ?, ?> machine(TestServerConnection connection, MachineCase spec) {
    var blockEntity = connection.getServerLevel().getBlockEntity(spec.position());
    require(blockEntity instanceof TileEntityStandardMachine<?, ?, ?>, "Missing native machine fixture " + spec.name());
    return (TileEntityStandardMachine<?, ?, ?>) blockEntity;
  }

  private static TileEntityStandardMachine<?, ?, ?> clientMachine(Minecraft client) {
    require(client.gui.screen() instanceof Ic2Gui<?>, "Native machine screen closed during progress validation");
    return (TileEntityStandardMachine<?, ?, ?>) ((Ic2Gui<?>) client.gui.screen()).getContainer().base;
  }

  private static boolean matchesScreen(Minecraft client, MachineCase spec, int menuId) {
    return client.gui.screen() instanceof Ic2Gui<?> screen && screen.getContainer().containerId == menuId
        && screen.getContainer().base instanceof TileEntityStandardMachine<?, ?, ?> machine
        && machine.getBlockPos().equals(spec.position());
  }

  private static int countOutput(TileEntityStandardMachine<?, ?, ?> machine) {
    return machine.outputSlot.isEmpty() ? 0 : machine.outputSlot.get().getCount();
  }

  private static List<BlockPos> platform(BlockPos origin) {
    List<BlockPos> result = new ArrayList<>();
    for (int x = -2; x <= 2; x++) for (int z = -1; z <= 3; z++) result.add(origin.offset(x, -1, z));
    return result;
  }

  private static boolean near(double actual, double expected) { return Math.abs(actual - expected) < EPSILON; }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  private record SavedPlayer(double x, double y, double z, float yaw, float pitch) {}

  private record MachineCase(String name, BlockPos position, Block block, Item input, Item output,
      int x, int y, int u, int v, int width, int height) {}

  private record ServerSample(long tick, double progress, int output) {}

  private record FrameSample(String phase, long nanos, ServerSample server, long clientTick,
      double progress, int output, double width, double sourceWidth, double height, double sourceHeight,
      double texelError) {
    boolean sameState(FrameSample other) {
      return phase.equals(other.phase) && server.equals(other.server) && clientTick == other.clientTick
          && progress == other.progress && output == other.output && width == other.width
          && sourceWidth == other.sourceWidth && height == other.height && texelError == other.texelError;
    }

    String csv(String machine) {
      return String.format(Locale.ROOT, "%s,%s,%d,%d,%.7f,%d,%d,%.7f,%d,%.7f,%.7f,%.7f,%.7f,%.9f",
          machine, phase, nanos, server.tick(), server.progress(), server.output(), clientTick, progress, output,
          width, sourceWidth, height, sourceHeight, texelError);
    }
  }
}
