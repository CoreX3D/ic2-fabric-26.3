package ic2.fabric.client.model;

import ic2.core.block.DynamicBeModel;
import ic2.core.block.comp.Obscuration;
import ic2.core.block.tileentity.Ic2TileEntity;
import ic2.core.util.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import java.util.function.Predicate;
import ic2.core.model.ModelUtil;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.Minecraft;
import ic2.core.model.MeshQuad;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import ic2.core.model.BlockMesh;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric port of {@code ic2.forge.model.DynamicBeModelForge}.
 *
 * <p>Quad generation is unchanged. NeoForge's {@code getModelData(world, pos, state, tileData)}
 * produced a {@code ModelData} snapshot that the renderer handed back to {@code getQuads}; Fabric's
 * Indigo asks {@link #emitBlockQuads} for the geometry with the world and position directly, so the
 * active flag and the obscuration overlays are resolved there instead.
 */
@Environment(EnvType.CLIENT)
final class DynamicBeModelFabric extends DynamicBeModel<List<List<MeshQuad>>>
    implements FabricBlockStateModel {
  private final Identifier modelId;
  private BlockState wrappedState;

  DynamicBeModelFabric(Identifier id) {
    super(id);
    this.modelId = id;
  }

  DynamicBeModelFabric(BlockState state, BlockStateModel model) {
    this(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    this.baseModel = BlockMesh.wrap(model);
    this.wrappedState = state;
  }

  @Override
  protected List<List<MeshQuad>> getMesh(BlockState state, boolean active) {
    return wrappedState == null ? super.getMesh(state, active) : generateMesh(baseModel, 0, false);
  }

  @Override
  public List<MeshQuad> getQuads(BlockState state, Direction side, RandomSource random) {
    return wrappedState == null ? getMesh(state == null ? block.defaultBlockState() : state, false).get(getIdx(side))
        : baseModel.getQuads(wrappedState, side, random);
  }

  private static int getIdx(Direction dir) {
    return dir == null ? 6 : dir.ordinal();
  }

  private static List<MeshQuad> getObscuredQuads(
      Obscuration.ObscurationData data, Direction targetFace) {
    BlockMesh refModel = ModelUtil.getBlockModel(data.state());
    RandomSource rand = RandomSource.create(42L);
    List<MeshQuad> refQuads = refModel.getQuads(data.state(), data.side(), rand);

    if (refQuads.isEmpty()) {
      return null;
    }

    List<MeshQuad> result = new ArrayList<>(refQuads.size());

    for (int i = 0; i < refQuads.size(); i++) {
      MeshQuad quad = refQuads.get(i);

      if (data.side() != targetFace) {
        quad = transformQuadFace(quad, data.side(), targetFace);
      }

      quad = tintQuad(quad, data.colorMultipliers() != null && i < data.colorMultipliers().length
          ? data.colorMultipliers()[i] : -1);

      result.add(quad);
    }

    return result;
  }

  private static MeshQuad transformQuadFace(MeshQuad quad, Direction fromFace, Direction toFace) {
    int[] oldData = quad.getVertices();
    int[] newData = Arrays.copyOf(oldData, oldData.length);
    int stride = oldData.length >>> 2;

    for (int v = 0; v < 4; v++) {
      int off = v * stride;
      float x = Float.intBitsToFloat(oldData[off]);
      float y = Float.intBitsToFloat(oldData[off + 1]);
      float z = Float.intBitsToFloat(oldData[off + 2]);

      float[] uv = posToUV(fromFace, x, y, z);
      float[] newPos = uvToPos(toFace, uv[0], uv[1]);

      newData[off] = Float.floatToRawIntBits(newPos[0]);
      newData[off + 1] = Float.floatToRawIntBits(newPos[1]);
      newData[off + 2] = Float.floatToRawIntBits(newPos[2]);
    }

    return quad.withVertices(newData, quad.getTintIndex(), toFace);
  }

  private static float[] posToUV(Direction face, float x, float y, float z) {
    return switch (face) {
      case NORTH -> new float[] {x, y};
      case SOUTH -> new float[] {1 - x, y};
      case WEST -> new float[] {1 - z, y};
      case EAST -> new float[] {z, y};
      case DOWN -> new float[] {x, 1 - z};
      case UP -> new float[] {x, z};
    };
  }

  private static float[] uvToPos(Direction face, float u, float v) {
    return switch (face) {
      case NORTH -> new float[] {u, v, 0};
      case SOUTH -> new float[] {1 - u, v, 1};
      case WEST -> new float[] {0, v, 1 - u};
      case EAST -> new float[] {1, v, u};
      case DOWN -> new float[] {u, 0, 1 - v};
      case UP -> new float[] {u, 1, v};
    };
  }

  private static MeshQuad tintQuad(MeshQuad quad, int color) {
    int[] oldData = quad.getVertices();
    int[] newData = Arrays.copyOf(oldData, oldData.length);
    int stride = oldData.length >>> 2;

    float r = ((color >>> 16) & 0xFF) / 255f;
    float g = ((color >>> 8) & 0xFF) / 255f;
    float b = (color & 0xFF) / 255f;

    for (int v = 0; v < 4; v++) {
      int off = v * stride + 3;
      int packedColor = oldData[off];
      int a = (packedColor >>> 24) & 0xFF;
      int red = (int) ((packedColor & 0xFF) * r);
      int green = (int) (((packedColor >>> 8) & 0xFF) * g);
      int blue = (int) (((packedColor >>> 16) & 0xFF) * b);
      newData[off] = (a << 24) | (blue << 16) | (green << 8) | red;
    }

    return quad.withVertices(newData, -1, quad.getDirection());
  }

  // UnbakedModel.bake (3-arg), inherited through DynamicBeModel/UnbakedModel.
  public BlockMesh bake(
      ModelBaker bakery,
      Function<Material, TextureAtlasSprite> spriteGetter,
      ModelState modelTransform) {
    return super.bake(bakery, spriteGetter, modelTransform, this.modelId);
  }

  @Override
  public void emitQuads(QuadEmitter emitter, BlockAndTintGetter world, BlockPos pos,
      BlockState state, RandomSource random, Predicate<Direction> cullTest) {
    BlockEntity blockEntity = world.getBlockEntity(pos);
    boolean active =
        this.block.canActive() && blockEntity instanceof Ic2TileEntity tile && tile.getActive();
    List<List<MeshQuad>> mesh = this.getMesh(state, active);

    if (blockEntity instanceof Ic2TileEntity tile) {
      Obscuration component = tile.getComponent(Obscuration.class);
      if (component != null) {
        Obscuration.ObscurationData[] data = component.getRenderState();
        if (data != null) {
          mesh = new ArrayList<>(mesh);

          for (int face = 0; face < 6; face++) {
            if (data[face] != null) {
              List<MeshQuad> obscured = getObscuredQuads(data[face], Util.ALL_DIRS[face]);
              if (obscured != null) {
                mesh.set(face, obscured);
              }
            }
          }
        }
      }
    }

    for (int i = 0; i < mesh.size(); i++) {
      Direction cull = i == 6 ? null : Direction.values()[i];
      if (cull == null || !cullTest.test(cull)) QuadEmit.emitAll(emitter, mesh.get(i), cull);
    }
  }

  protected List<List<MeshQuad>> generateMesh(BlockMesh baseModel, int rot, boolean rotX) {
    RandomSource rand = RandomSource.create(42L);
    List<List<MeshQuad>> mesh = new ArrayList<>(7);

    for (int i = 0; i < 7; i++) {
      mesh.add(Collections.emptyList());
    }

    for (int i = 0; i < 7; i++) {
      Direction face = i < 6 ? Util.ALL_DIRS[i] : null;
      List<MeshQuad> quads = baseModel.getQuads(null, face, rand);
      int writeIdx = i;

      if (rot != 0) {
        if (face != null) {
          writeIdx = rotateFace(face, rot, rotX).ordinal();
        }

        if (!quads.isEmpty()) {
          List<MeshQuad> newQuads = new ArrayList<>(quads.size());

          for (MeshQuad quad : quads) {
            newQuads.add(rotateQuad(quad, rot, rotX));
          }

          quads = newQuads;
        }
      }

      mesh.set(writeIdx, quads);
    }

    return mesh;
  }

  @Override
  public TextureAtlasSprite getParticleIcon() {
    return this.baseModel.getParticleIcon();
  }

  @Override public int materialFlags(BlockAndTintGetter world, BlockPos pos,
      BlockState state, RandomSource random) {
    int flags = materialFlags();
    if (world.getBlockEntity(pos) instanceof Ic2TileEntity tile) {
      Obscuration obscuration = tile.getComponent(Obscuration.class);
      if (obscuration != null && obscuration.getRenderState() != null) {
        for (var data : obscuration.getRenderState()) {
          if (data != null) flags |= ModelUtil.getBlockModel(data.state()).materialFlags();
        }
      }
    }
    return flags;
  }

  @Override
  public ItemTransforms getTransforms() {
    return ItemTransforms.NO_TRANSFORMS;
  }

}
