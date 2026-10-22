package ic2.core.event;

import ic2.core.IC2;
import ic2.core.block.ChunkLoadAwareBlockHandler;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;

public class TickHandler {
  private static final boolean debugUpdate = System.getProperty("ic2.debugUpdate") != null;
  private static final Map<IWorldTickCallback, Throwable> debugTraces =
      debugUpdate ? new WeakHashMap<>() : null;
  private static Throwable lastDebugTrace;

  public static void onWorldTickStart(Level world) {
    if (!world.isClientSide()) {
      ChunkLoadAwareBlockHandler.onWorldTick(world);

      WorldData worldData = WorldData.get(world, false);
      if (worldData != null) {
        net.minecraft.util.profiling.Profiler.get().push("updates");
        processUpdates(world, worldData);
        net.minecraft.util.profiling.Profiler.get().popPush("Wind");
        worldData.windSim.updateWind();
        net.minecraft.util.profiling.Profiler.get().popPush("energynet");
        worldData.energyNet.onTickStart();
        net.minecraft.util.profiling.Profiler.get().pop();
      }
    }
  }

  public static void onWorldTickEnd(Level world) {
    if (!world.isClientSide()) {
      WorldData worldData = WorldData.get(world, false);
      if (worldData != null) {
        net.minecraft.util.profiling.Profiler.get().push("energynet");
        worldData.energyNet.onTickEnd();
        net.minecraft.util.profiling.Profiler.get().popPush("Networking");
        IC2.network.get(true).onTickEnd(worldData);
        net.minecraft.util.profiling.Profiler.get().pop();
      }
    }
  }

  public static void onServerTick() {}

  public static void onClientTick() {
    IC2.keyboard.sendKeyUpdate();
    IC2.soundManager.tick();
    Level world = IC2.sideProxy.getPlayerWorld();
    if (world != null) {
      processUpdates(world, WorldData.get(world));
    }
  }

  public static void requestSingleWorldTick(Level world, IWorldTickCallback callback) {
    WorldData.get(world).singleUpdates.add(callback);
    if (debugUpdate) {
      debugTraces.put(callback, new Throwable());
    }
  }

  public static void requestContinuousWorldTick(Level world, IWorldTickCallback update) {
    WorldData worldData = WorldData.get(world);
    if (!worldData.continuousUpdatesInUse) {
      worldData.continuousUpdates.add(update);
    } else {
      worldData.continuousUpdatesToRemove.remove(update);
      worldData.continuousUpdatesToAdd.add(update);
    }

    if (debugUpdate) {
      debugTraces.put(update, new Throwable());
    }
  }

  public static void removeContinuousWorldTick(Level world, IWorldTickCallback update) {
    WorldData worldData = WorldData.get(world);
    if (!worldData.continuousUpdatesInUse) {
      worldData.continuousUpdates.remove(update);
    } else {
      worldData.continuousUpdatesToAdd.remove(update);
      worldData.continuousUpdatesToRemove.add(update);
    }
  }

  private static void processUpdates(Level world, WorldData worldData) {
    net.minecraft.util.profiling.Profiler.get().push("single-update");

    IWorldTickCallback callback;
    while ((callback = worldData.singleUpdates.poll()) != null) {
      if (debugUpdate) {
        lastDebugTrace = debugTraces.remove(callback);
      }

      callback.onTick(world);
    }

    net.minecraft.util.profiling.Profiler.get().popPush("cont-update");
    worldData.continuousUpdatesInUse = true;

    for (IWorldTickCallback update : worldData.continuousUpdates) {
      if (debugUpdate) {
        lastDebugTrace = debugTraces.remove(update);
      }

      update.onTick(world);
    }

    worldData.continuousUpdatesInUse = false;
    if (debugUpdate) {
      lastDebugTrace = null;
    }

    worldData.continuousUpdates.addAll(worldData.continuousUpdatesToAdd);
    worldData.continuousUpdatesToAdd.clear();
    worldData.continuousUpdatesToRemove.forEach(worldData.continuousUpdates::remove);
    worldData.continuousUpdatesToRemove.clear();
    net.minecraft.util.profiling.Profiler.get().pop();
  }

  public static Throwable getLastDebugTrace() {
    return lastDebugTrace;
  }
}
