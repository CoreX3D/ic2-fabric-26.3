package ic2.client.gametest;

import ic2.api.item.ElectricItem;
import ic2.core.item.ElectricItemManager;
import ic2.core.ref.Ic2Items;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Actual client block destruction and native sound cancellation, in the disposable test world. */
@Environment(EnvType.CLIENT)
@SuppressWarnings("UnstableApiUsage")
public final class DrillMiningRenderChecks {
  private static final BlockPos BASE = new BlockPos(-20, 64, 48);
  private static final BlockPos TARGET = BASE.above();

  private DrillMiningRenderChecks() {}

  public static List<String> validate(ClientGameTestContext context, TestServerContext server,
      TestServerConnection connection) {
    Path directory = context.computeOnClient(client -> client.gameDirectory.toPath().toAbsolutePath().normalize());
    require(directory.endsWith(Path.of("build", "client-render-smoke")),
        "Drill fixtures are permitted only in build/client-render-smoke");
    SavedPlayer saved = server.computeOnServer(instance -> {
      var player = connection.getServerPlayer();
      return new SavedPlayer(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
          player.gameMode.getGameModeForPlayer());
    });
    List<String> evidence = new ArrayList<>();
    ConcurrentLinkedQueue<Identifier> sounds = new ConcurrentLinkedQueue<>();
    SoundEventListener listener = (sound, event, distance) -> sounds.add(sound.getIdentifier());
    boolean fixtureCreated = false;
    try {
      server.runOnServer(instance -> {
        var level = connection.getServerLevel();
        require(level.isEmptyBlock(TARGET), "Drill fixture would overwrite another scene");
        for (BlockPos floor : platform()) require(level.isEmptyBlock(floor),
            "Drill platform would overwrite another scene: " + floor);
      });
      fixtureCreated = true;
      server.runOnServer(instance -> {
        var level = connection.getServerLevel();
        var player = connection.getServerPlayer();
        for (BlockPos floor : platform()) level.setBlock(floor, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
        player.closeContainer();
        player.getInventory().clearContent();
        player.getInventory().setSelectedSlot(0);
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(level, BASE.getX() + 0.5, BASE.getY(), BASE.getZ() + 2.5,
            Set.of(), 180.0f, 3.5f, true);
        player.inventoryMenu.broadcastChanges();
      });
      context.waitTicks(3);
      connection.waitForClientboundPackets();
      context.runOnClient(client -> {
        client.player.getInventory().setSelectedSlot(0);
        client.getSoundManager().addListener(listener);
        var result = client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.STONE_BREAK,
            SoundSource.BLOCKS, 1.0f, 1.0f, RandomSource.create(), TARGET));
        require(result != SoundEngine.PlayResult.NOT_STARTED, "Vanilla stone sound was incorrectly cancelled");
      });
      evidence.add("Drill sound control: native SoundManager played the vanilla stone break sound with an empty hand.");

      for (Item item : List.of(Ic2Items.DRILL, Ic2Items.DIAMOND_DRILL, Ic2Items.IRIDIUM_DRILL)) {
        for (double charge : new double[] {0.0, 10000.0}) {
          server.runOnServer(instance -> {
            var player = connection.getServerPlayer();
            player.getInventory().setItem(0, ElectricItemManager.getCharged(item, charge));
            player.inventoryMenu.broadcastChanges();
          });
          connection.waitForClientboundPackets();
          context.waitFor(client -> client.player.getMainHandItem().is(item)
              && ElectricItem.manager.getCharge(client.player.getMainHandItem()) == charge);
          context.runOnClient(client -> {
            var result = client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.STONE_BREAK,
                SoundSource.BLOCKS, 1.0f, 1.0f, RandomSource.create(), TARGET));
            require(result == SoundEngine.PlayResult.NOT_STARTED,
                "Drill override did not cancel native stone sound before playback");
            // The user's crash originates in this real native block-break level-event path.
            client.level.levelEvent(2001, TARGET, Block.getId(Blocks.COBBLESTONE.defaultBlockState()));
          });
          context.waitTicks(2);
        }
      }
      context.waitFor(client -> sounds.stream().anyMatch(id -> id.getNamespace().equals("ic2")
          && (id.getPath().equals("item.drill.soft") || id.getPath().equals("item.drill.hard"))));
      evidence.add("Drill sound regression: charged and empty basic, diamond and iridium drills survived native "
          + "ClientLevel levelEvent 2001; overridden sound returned NOT_STARTED and deferred IC drill sound reached the native listener.");

      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        player.getInventory().setItem(0, ElectricItemManager.getCharged(Ic2Items.CHAINSAW, 10000));
        player.inventoryMenu.broadcastChanges();
        connection.getServerLevel().setBlock(TARGET, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
      });
      connection.waitForClientboundPackets();
      context.waitFor(client -> client.player.getMainHandItem().is(Ic2Items.CHAINSAW)
          && client.level.getBlockState(TARGET).is(Blocks.COBBLESTONE));
      context.runOnClient(client -> {
        // Exact block coordinates also avoid the legacy override's truncation of negative sound positions.
        var result = client.getSoundManager().play(new SimpleSoundInstance(SoundEvents.STONE_HIT,
            SoundSource.BLOCKS, 1.0f, 1.0f, RandomSource.create(),
            TARGET.getX(), TARGET.getY(), TARGET.getZ()));
        require(result == SoundEngine.PlayResult.NOT_STARTED,
            "Raycast-confirmed chainsaw hit did not cancel the native stone hit sound");
      });
      context.waitFor(client -> sounds.stream().anyMatch(id -> id.getNamespace().equals("ic2")
          && id.getPath().startsWith("item.chainsaw.use")));
      evidence.add("Shared sound regression: a raycast-confirmed charged chainsaw hit cancelled the native stone "
          + "hit and delivered its deferred IC chainsaw use sound to the native listener.");

      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        player.getInventory().setItem(0, ElectricItemManager.getCharged(Ic2Items.DRILL, 10000));
        player.inventoryMenu.broadcastChanges();
        connection.getServerLevel().setBlock(TARGET, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
      });
      connection.waitForClientboundPackets();
      context.waitFor(client -> client.player.getMainHandItem().is(Ic2Items.DRILL)
          && client.level.getBlockState(TARGET).is(Blocks.COBBLESTONE));
      context.runOnClient(client -> require(client.gameMode.startDestroyBlock(TARGET, Direction.SOUTH),
          "Native client refused to start drilling cobblestone"));
      for (int tick = 0; tick < 120
          && !context.computeOnClient(client -> client.level.isEmptyBlock(TARGET)); tick++) {
        context.runOnClient(client -> client.gameMode.continueDestroyBlock(TARGET, Direction.SOUTH));
        context.waitTick();
      }
      context.runOnClient(client -> {
        require(client.level.isEmptyBlock(TARGET), "Native client drilling did not finish");
        client.gameMode.stopDestroyBlock();
      });
      connection.waitForServerboundPackets();
      server.waitFor(instance -> connection.getServerLevel().isEmptyBlock(TARGET));
      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        require(ElectricItem.manager.getCharge(player.getMainHandItem()) == 9950.0,
            "Actual drill mining did not consume exactly 50 EU");
        require(player.getInventory().contains(new ItemStack(Items.COBBLESTONE))
            || !connection.getServerLevel().getEntitiesOfClass(ItemEntity.class, new AABB(TARGET).inflate(3),
                entity -> entity.getItem().is(Items.COBBLESTONE)).isEmpty(),
            "Actual server drilling did not drop cobblestone");
      });
      evidence.add("Drill mining regression: real survival MultiPlayerGameMode start/continueDestroyBlock broke "
          + "cobblestone on the server, produced its drop and reduced drill charge from 10000 to 9950 EU without crashing.");
      return evidence;
    } finally {
      context.runOnClient(client -> {
        client.getSoundManager().removeListener(listener);
        if (client.gameMode != null) client.gameMode.stopDestroyBlock();
      });
      boolean removeFixture = fixtureCreated;
      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        player.getInventory().clearContent();
        if (removeFixture) {
          connection.getServerLevel().setBlock(TARGET, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
          for (BlockPos floor : platform()) connection.getServerLevel()
              .setBlock(floor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
          connection.getServerLevel().getEntitiesOfClass(ItemEntity.class, new AABB(TARGET).inflate(3))
              .forEach(ItemEntity::discard);
        }
        player.setGameMode(saved.mode());
        player.teleportTo(connection.getServerLevel(), saved.x(), saved.y(), saved.z(), Set.of(),
            saved.yaw(), saved.pitch(), true);
      });
      connection.waitForClientboundPackets();
      context.waitTick();
    }
  }

  private static List<BlockPos> platform() {
    List<BlockPos> positions = new ArrayList<>();
    for (int x = -1; x <= 1; x++) for (int z = 0; z <= 3; z++) positions.add(BASE.offset(x, -1, z));
    return positions;
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  private record SavedPlayer(double x, double y, double z, float yaw, float pitch, GameType mode) {}
}
