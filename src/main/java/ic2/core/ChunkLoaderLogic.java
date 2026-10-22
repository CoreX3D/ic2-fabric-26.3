package ic2.core;

import ic2.core.event.WorldData;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

public final class ChunkLoaderLogic {
  private static final String legacySavedStateId =
      IC2.getIdentifier("loaded_chunks").toString().replace(':', '_');
  private static final SavedDataType<ChunkLoaderLogic.SavedState> savedStateType =
      new SavedDataType<>(
          IC2.getIdentifier("loaded_chunks"),
          ChunkLoaderLogic.SavedState::new,
          CompoundTag.CODEC.xmap(ChunkLoaderLogic.SavedState::new, SavedState::toNbt),
          net.minecraft.util.datafix.DataFixTypes.LEVEL);
  private static final TicketType ticketType =
      Registry.register(BuiltInRegistries.TICKET_TYPE, IC2.getIdentifier("chunk_loader"),
          new TicketType(TicketType.NO_TIMEOUT,
              TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

  /** Registers the ticket type while the registries are still writable during mod initialization. */
  public static void init() {}

  public static void addChunkLoader(ServerLevel world, BlockPos pos, LongSet chunks) {
    long loaderChunk = ChunkPos.pack(pos);
    if (!chunks.contains(loaderChunk)) {
      throw new IllegalArgumentException("missing own position");
    }

    ChunkLoaderLogic.SavedState state =
        (ChunkLoaderLogic.SavedState)
            world.getDataStorage().computeIfAbsent(savedStateType);
    ((Set)
            state.chunksToChunkLoaders.computeIfAbsent(
                loaderChunk, ignore -> new ObjectOpenHashSet<>(1)))
        .add(pos);
    state.setDirty();
    WorldData worldData = WorldData.get(world);

    for (long chunk : chunks) {
      Set<BlockPos> loaders =
          (Set<BlockPos>)
              worldData.loadedChunks.computeIfAbsent(chunk, ignore -> new ObjectOpenHashSet<>(1));
      if (loaders.isEmpty()) {
        addChunkTicket(world, ChunkPos.unpack(chunk));
      }

      loaders.add(pos);
    }

    worldData.chunkLoaders.put(pos, new LongOpenHashSet(chunks));
  }

  public static void removeChunkLoader(ServerLevel world, BlockPos pos) {
    ChunkLoaderLogic.SavedState state =
        (ChunkLoaderLogic.SavedState) world.getDataStorage().get(savedStateType);
    if (state != null) {
      long chunkPos = ChunkPos.pack(pos);
      Set<BlockPos> positions = (Set<BlockPos>) state.chunksToChunkLoaders.get(chunkPos);
      if (positions != null && positions.remove(pos)) {
        if (positions.isEmpty()) {
          state.chunksToChunkLoaders.remove(chunkPos);
        }
        state.setDirty();
      }
    }

    WorldData worldData = WorldData.get(world, false);
    if (worldData != null) {
      disableChunkLoader(world, pos, worldData);
    }
  }

  private static void disableChunkLoader(ServerLevel world, BlockPos pos, WorldData worldData) {
    LongSet positions = (LongSet) worldData.chunkLoaders.remove(pos);
    if (positions != null) {

      for (long chunkPos : positions) {
        Set<BlockPos> loaders = (Set<BlockPos>) worldData.loadedChunks.get(chunkPos);
        if (loaders != null && loaders.remove(pos) && loaders.isEmpty()) {
          worldData.loadedChunks.remove(chunkPos);
          removeChunkTicket(world, ChunkPos.unpack(chunkPos));
        }
      }
    }
  }

  public static void updateChunkLoader(ServerLevel world, BlockPos pos, LongSet chunks) {
    long loaderChunk = ChunkPos.pack(pos);
    if (!chunks.contains(loaderChunk)) {
      throw new IllegalArgumentException("missing own position");
    }

    WorldData worldData = WorldData.get(world);
    LongSet prev = (LongSet) worldData.chunkLoaders.get(pos);
    if (prev == null) {
      addChunkLoader(world, pos, chunks);
    } else {
      LongIterator it = prev.longIterator();

      while (it.hasNext()) {
        long chunk = it.nextLong();
        if (!chunks.contains(chunk)) {
          it.remove();
          Set<BlockPos> loaders = (Set<BlockPos>) worldData.loadedChunks.get(chunk);
          loaders.remove(pos);
          if (loaders.isEmpty()) {
            removeChunkTicket(world, ChunkPos.unpack(chunk));
            worldData.loadedChunks.remove(chunk);
          }
        }
      }

      it = chunks.iterator();

      while (it.hasNext()) {
        long chunk = (Long) it.next();
        if (prev.add(chunk)) {
          Set<BlockPos> loaders =
              (Set<BlockPos>)
                  worldData.loadedChunks.computeIfAbsent(
                      chunk, ignore -> new ObjectOpenHashSet<>());
          if (loaders.isEmpty()) {
            addChunkTicket(world, ChunkPos.unpack(chunk));
          }

          loaders.add(pos);
        }
      }
    }
  }

  public static void onWorldLoad(ServerLevel world) {
    ChunkLoaderLogic.SavedState state =
        (ChunkLoaderLogic.SavedState) world.getDataStorage().get(savedStateType);
    if (state == null) {
      state = importLegacySavedState(world);
    }
    if (state != null && !state.chunksToChunkLoaders.isEmpty()) {
      WorldData worldData = WorldData.get(world);

      for (Entry<Set<BlockPos>> entry : state.chunksToChunkLoaders.long2ObjectEntrySet()) {
        long chunkPos = entry.getLongKey();
        Set<BlockPos> loaders = (Set<BlockPos>) entry.getValue();
        worldData.loadedChunks.put(chunkPos, new ObjectOpenHashSet<>(loaders));

        for (BlockPos pos : loaders) {
          ((LongSet) worldData.chunkLoaders.computeIfAbsent(pos, ignore -> new LongOpenHashSet(1)))
              .add(chunkPos);
        }

        addChunkTicket(world, ChunkPos.unpack(chunkPos));
      }
    }
  }

  public static void onChunkUnload(LevelChunk chunk) {
    assert !chunk.getLevel().isClientSide();
    ServerLevel world = (ServerLevel) chunk.getLevel();
    ChunkLoaderLogic.SavedState state =
        (ChunkLoaderLogic.SavedState) world.getDataStorage().get(savedStateType);
    if (state != null && !state.chunksToChunkLoaders.isEmpty()) {
      Set<BlockPos> loaders =
          (Set<BlockPos>) state.chunksToChunkLoaders.get(chunk.getPos().pack());
      if (loaders != null && !loaders.isEmpty()) {
        WorldData worldData = WorldData.get(world, false);
        if (worldData != null) {
          for (BlockPos pos : loaders) {
            disableChunkLoader(world, pos, worldData);
          }
        }
      }
    }
  }

  private static void addChunkTicket(ServerLevel world, ChunkPos pos) {
    world.getChunkSource().addTicketWithRadius(ticketType, pos, 2);
  }

  private static void removeChunkTicket(ServerLevel world, ChunkPos pos) {
    world.getChunkSource().removeTicketWithRadius(ticketType, pos, 2);
  }

  private static SavedState importLegacySavedState(ServerLevel world) {
    Path dimensionFolder = DimensionType.getStorageFolder(
        world.dimension(), world.getServer().getWorldPath(LevelResource.ROOT));
    Path legacyFile = dimensionFolder.resolve("data").resolve(legacySavedStateId + ".dat");
    if (!Files.isRegularFile(legacyFile)) {
      return null;
    }
    try {
      CompoundTag root = NbtIo.readCompressed(legacyFile, NbtAccounter.unlimitedHeap());
      SavedState state = new SavedState(root.getCompoundOrEmpty("data"));
      world.getDataStorage().set(savedStateType, state);
      state.setDirty();
      return state;
    } catch (IOException error) {
      throw new IllegalStateException("Unable to import IC2 chunk loaders from " + legacyFile, error);
    }
  }

  private static final class SavedState extends SavedData {
    final Long2ObjectMap<Set<BlockPos>> chunksToChunkLoaders = new Long2ObjectOpenHashMap<>();

    SavedState() {}

    SavedState(CompoundTag nbt) {
      ListTag loaders = nbt.getListOrEmpty("loaders");

      for (int i = 0; i < loaders.size(); i++) {
        CompoundTag contentTag = loaders.getCompoundOrEmpty(i);
        BlockPos pos =
            new BlockPos(contentTag.getIntOr("x", 0), contentTag.getIntOr("y", 0), contentTag.getIntOr("z", 0));
        ((Set)
                this.chunksToChunkLoaders.computeIfAbsent(
                    ChunkPos.pack(pos), ignore -> new ObjectOpenHashSet<>(1)))
            .add(pos);
      }
    }

    CompoundTag toNbt() {
      CompoundTag nbt = new CompoundTag();
      ListTag loaders = new ListTag();
      nbt.put("loaders", loaders);

      for (Set<BlockPos> positions : this.chunksToChunkLoaders.values()) {
        for (BlockPos pos : positions) {
          CompoundTag contentTag = new CompoundTag();
          loaders.add(contentTag);
          contentTag.putInt("x", pos.getX());
          contentTag.putInt("y", pos.getY());
          contentTag.putInt("z", pos.getZ());
        }
      }

      return nbt;
    }
  }
}
