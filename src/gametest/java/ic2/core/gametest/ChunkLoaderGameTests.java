package ic2.core.gametest;

import ic2.core.ChunkLoaderLogic;
import ic2.core.block.machine.tileentity.TileEntityChunkLoader;
import ic2.core.event.WorldData;
import ic2.core.ref.Ic2Blocks;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ChunkLoaderGameTests {
  private static final String EMPTY = "ic2:gametest/empty3x3x3";

  private static final BlockPos LOADER_POS = new BlockPos(1, 1, 1);

  // chunk loader: consumes euPerChunk (default 1 EU/t) per loaded chunk and keeps the chunks
  // force-loaded while powered
  @GameTest(structure = EMPTY, maxTicks = 200)
  public void chunkLoaderForceLoadsWhilePoweredAndStopsWhenDrained(GameTestHelper helper) {
    TileEntityChunkLoader te = placeLoader(helper);
    long ownChunk = ChunkPos.pack(helper.absolutePos(LOADER_POS));
    te.energy.addEnergy(50.0);

    helper.runAtTickTime(
        10,
        () -> {
          helper.assertTrue(te.getActive(), "a powered chunk loader should be active");
          helper.assertTrue(
              te.energy.getEnergy() < 50.0,
              "the chunk loader should consume 1 EU/t per chunk, has " + te.energy.getEnergy());
          WorldData worldData = WorldData.get(helper.getLevel());
          helper.assertTrue(
              worldData.loadedChunks.containsKey(ownChunk),
              "the loader's own chunk should be registered as force-loaded");
        });

    helper.runAtTickTime(
        120,
        () -> {
          helper.assertFalse(
              te.getActive(), "the chunk loader must shut down once its 50 EU are used up");
          WorldData worldData = WorldData.get(helper.getLevel());
          helper.assertFalse(
              worldData.loadedChunks.containsKey(ownChunk),
              "the chunk must be released when the loader shuts down");
          helper.succeed();
        });
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void chunkLoaderEditingUnpoweredSelectionDoesNotForceLoad(GameTestHelper helper) {
    TileEntityChunkLoader te = placeLoader(helper);
    BlockPos loaderPos = helper.absolutePos(LOADER_POS);
    ChunkPos ownChunk = ChunkPos.containing(loaderPos);
    ChunkPos selectedChunk = new ChunkPos(ownChunk.x() + 4, ownChunk.z());

    te.addChunkToLoaded(selectedChunk);
    helper.assertTrue(
        te.getLoadedChunks().contains(selectedChunk.pack()),
        "an unpowered loader should retain the selected chunk");
    assertNoRuntimeOwnership(helper, loaderPos);

    te.removeChunkFromLoaded(selectedChunk);
    helper.assertFalse(
        te.getLoadedChunks().contains(selectedChunk.pack()),
        "an unpowered loader should remove the deselected chunk");
    assertNoRuntimeOwnership(helper, loaderPos);

    helper.runAtTickTime(
        20,
        () -> {
          helper.assertFalse(te.getActive(), "editing chunks must not activate an empty loader");
          assertNoRuntimeOwnership(helper, loaderPos);
          helper.succeed();
        });
  }

  private static void assertNoRuntimeOwnership(GameTestHelper helper, BlockPos loaderPos) {
    WorldData worldData = WorldData.get(helper.getLevel());
    helper.assertFalse(
        worldData.chunkLoaders.containsKey(loaderPos),
        "editing an unpowered loader must not register a chunk-loader owner");
    helper.assertTrue(
        worldData.loadedChunks.values().stream().noneMatch(owners -> owners.contains(loaderPos)),
        "editing an unpowered loader must not acquire any chunk ticket");
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void chunkLoaderInactiveOnLoadReleasesStaleRegistration(GameTestHelper helper) {
    TileEntityChunkLoader te = placeLoader(helper);
    BlockPos loaderPos = helper.absolutePos(LOADER_POS);
    helper.assertFalse(te.getActive(), "the restored loader should be inactive");
    // World loading restores saved owner tickets before the tile's deferred onLoaded callback.
    ChunkLoaderLogic.addChunkLoader(helper.getLevel(), loaderPos, te.getLoadedChunks());
    helper.assertTrue(
        WorldData.get(helper.getLevel()).chunkLoaders.containsKey(loaderPos),
        "the fixture should restore an old chunk-loader owner before the tile loads");

    helper.runAtTickTime(
        20,
        () -> {
          helper.assertFalse(te.getActive(), "restoring an inactive loader must not activate it");
          assertNoRuntimeOwnership(helper, loaderPos);
          helper.succeed();
        });
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void chunkLoaderOnlyAcceptsChunksInRange(GameTestHelper helper) {
    TileEntityChunkLoader te = placeLoader(helper);
    ChunkPos mainChunk = ChunkPos.containing(helper.absolutePos(LOADER_POS));
    ChunkPos nearChunk = new ChunkPos(mainChunk.x() + 4, mainChunk.z());
    ChunkPos farChunk = new ChunkPos(mainChunk.x() + 5, mainChunk.z());

    helper.assertTrue(
        te.isChunkInRange(nearChunk), "a chunk 4 chunks away is within the 4 chunk radius");
    helper.assertFalse(
        te.isChunkInRange(farChunk), "a chunk 5 chunks away is outside the 4 chunk radius");

    te.addChunkToLoaded(nearChunk);
    te.addChunkToLoaded(farChunk);
    helper.assertValueEqual(te.getLoadedChunks().size(), 2, "loaded chunk entries (own + near)");
    helper.assertTrue(
        te.getLoadedChunks().contains(nearChunk.pack()), "the in-range chunk should be accepted");
    helper.assertFalse(
        te.getLoadedChunks().contains(farChunk.pack()),
        "the out-of-range chunk must be rejected");

    helper.succeed();
  }

  private static TileEntityChunkLoader placeLoader(GameTestHelper helper) {
    helper.setBlock(LOADER_POS, Ic2Blocks.CHUNK_LOADER);
    TileEntityChunkLoader te = getTe(helper, LOADER_POS, TileEntityChunkLoader.class);
    // placing via setBlock skips onPlaced, so register the loader's own chunk like item placement
    // would
    te.getLoadedChunks().add(ChunkPos.pack(helper.absolutePos(LOADER_POS)));
    return te;
  }

  private static <T extends BlockEntity> T getTe(
      GameTestHelper helper, BlockPos pos, Class<T> type) {
    BlockEntity be = helper.getBlockEntity(pos, net.minecraft.world.level.block.entity.BlockEntity.class);
    if (!type.isInstance(be)) {
      throw new IllegalStateException(
          "expected " + type.getSimpleName() + " at " + pos + ", found " + be);
    }

    return type.cast(be);
  }
}
