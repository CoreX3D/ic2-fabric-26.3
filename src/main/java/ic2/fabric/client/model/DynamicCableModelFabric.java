package ic2.fabric.client.model;

import ic2.core.block.wiring.AbstractCableBlock;
import ic2.core.block.wiring.CableFoam;
import ic2.core.block.wiring.CableType;
import ic2.core.block.wiring.DynamicCableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import java.util.function.Predicate;
import ic2.core.model.MeshQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import ic2.core.model.BlockMesh;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric port of {@code ic2.forge.model.DynamicCableModelForge}.
 *
 * <p>The quad generation is unchanged; only the delivery changed. NeoForge passed a {@code
 * ModelData} snapshot produced by {@code getModelData(world, pos, state, tileData)}. Fabric's Indigo
 * renderer instead calls {@link #emitBlockQuads} with the world and position directly, so the
 * connection recalculation that used to happen in {@code getModelData} happens there.
 */
@Environment(EnvType.CLIENT)
final class DynamicCableModelFabric extends DynamicCableModel<List<MeshQuad>[], List<MeshQuad>[]>
    implements FabricBlockStateModel {
  private static final int STRIDE = 8;
  private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath("ic2", "cable");

  DynamicCableModelFabric(CableType type, int insulation, CableFoam foam, boolean active) {
    super(type, insulation, foam, active);
  }

  private static float map(float value, float start, float end) {
    return start + value * (end - start);
  }

  private static void vertex(
      int vertex, float x, float y, float z, float u, float v, int normals, int[] out) {
    int offset = vertex * STRIDE;
    out[offset++] = Float.floatToRawIntBits(x);
    out[offset++] = Float.floatToRawIntBits(y);
    out[offset++] = Float.floatToRawIntBits(z);
    out[offset++] = -1;
    out[offset++] = Float.floatToRawIntBits(u);
    out[offset++] = Float.floatToRawIntBits(v);
    out[++offset] = normals;
  }

  private static int packNormals(float nx, float ny, float nz) {
    return mapFloatToByte(nx) | mapFloatToByte(ny) << 8 | mapFloatToByte(nz) << 16;
  }

  private static int mapFloatToByte(float f) {
    assert f >= -1.0F && f <= 1.0F;
    return Math.round(f * 127.0F) & 0xFF;
  }

  private static int getIdx(Direction dir) {
    return dir == null ? 6 : dir.ordinal();
  }

  // UnbakedModel.bake (3-arg), inherited through DynamicCableModel/UnbakedModel.
  public BlockMesh bake(
      ModelBaker bakery,
      Function<Material, TextureAtlasSprite> spriteGetter,
      ModelState modelTransform) {
    return super.bake(bakery, spriteGetter, modelTransform, MODEL_ID);
  }

  @Override
  public void emitQuads(QuadEmitter emitter, BlockAndTintGetter world, BlockPos pos,
      BlockState state, RandomSource random, Predicate<Direction> cullTest) {
    List<MeshQuad>[] mesh = this.getMesh(state);
    for (int i = 0; i < mesh.length; i++) {
      Direction cull = i == 6 ? null : Direction.values()[i];
      if (cull == null || !cullTest.test(cull)) QuadEmit.emitAll(emitter, mesh[i], cull);
    }
  }

  public List<MeshQuad> getQuads(BlockState state, Direction side, RandomSource random) {
    List<MeshQuad>[] mesh = state == null ? generateMesh(DyeColor.BLACK, 0) : getMesh(state);
    return mesh[side == null ? 6 : side.ordinal()];
  }

  protected List<MeshQuad>[] generateMesh(DyeColor color, int connections) {
    List<MeshQuad>[] mesh = new List[7];

    for (int i = 0; i < mesh.length; i++) {
      mesh[i] = new ArrayList<>();
    }

    this.generateQuads(color, connections, mesh);

    for (int i = 0; i < mesh.length; i++) {
      if (mesh[i].isEmpty()) {
        mesh[i] = Collections.emptyList();
      }
    }

    return mesh;
  }

  protected void emitQuad(
      List<MeshQuad>[] emitter,
      Direction face,
      float left,
      float bottom,
      float right,
      float top,
      float depth,
      TextureAtlasSprite texture) {
    float xs;
    float ys;
    float zs;
    float xe;
    float ye;
    float ze;
    switch (face) {
      case DOWN:
        ye = depth;
        ys = depth;
        xs = left;
        zs = bottom;
        xe = right;
        ze = top;
        break;
      case UP:
        ys = ye = 1.0F - depth;
        xs = left;
        zs = bottom;
        xe = right;
        ze = top;
        break;
      case NORTH:
        ze = depth;
        zs = depth;
        xs = left;
        ys = bottom;
        xe = right;
        ye = top;
        break;
      case SOUTH:
        zs = ze = 1.0F - depth;
        xs = left;
        ys = bottom;
        xe = right;
        ye = top;
        break;
      case WEST:
        xe = depth;
        xs = depth;
        ys = bottom;
        zs = left;
        ye = top;
        ze = right;
        break;
      case EAST:
        xs = xe = 1.0F - depth;
        ys = bottom;
        zs = left;
        ye = top;
        ze = right;
        break;
      default:
        throw new IllegalStateException();
    }

    this.emitQuad(emitter, face, xs, ys, zs, xe, ye, ze, texture);
  }

  protected void emitQuad(
      List<MeshQuad>[] emitter,
      Direction face,
      float xs,
      float ys,
      float zs,
      float xe,
      float ye,
      float ze,
      TextureAtlasSprite texture) {
    int[] data = new int[32];
    float us = texture.getU0();
    float vs = texture.getV0();
    float ue = texture.getU1();
    float ve = texture.getV1();
    int normals = packNormals(face.getStepX(), face.getStepY(), face.getStepZ());
    float depth;
    switch (face) {
      case DOWN:
        depth = ys;
        vertex(0, xs, ys, ze, map(xs, us, ue), map(1.0F - ze, vs, ve), normals, data);
        vertex(1, xs, ys, zs, map(xs, us, ue), map(1.0F - zs, vs, ve), normals, data);
        vertex(2, xe, ys, zs, map(xe, us, ue), map(1.0F - zs, vs, ve), normals, data);
        vertex(3, xe, ys, ze, map(xe, us, ue), map(1.0F - ze, vs, ve), normals, data);
        break;
      case UP:
        depth = 1.0F - ye;
        vertex(0, xs, ye, zs, map(xs, us, ue), map(zs, vs, ve), normals, data);
        vertex(1, xs, ye, ze, map(xs, us, ue), map(ze, vs, ve), normals, data);
        vertex(2, xe, ye, ze, map(xe, us, ue), map(ze, vs, ve), normals, data);
        vertex(3, xe, ye, zs, map(xe, us, ue), map(zs, vs, ve), normals, data);
        break;
      case NORTH:
        depth = zs;
        vertex(0, xe, ye, zs, map(1.0F - xe, us, ue), map(1.0F - ye, vs, ve), normals, data);
        vertex(1, xe, ys, zs, map(1.0F - xe, us, ue), map(1.0F - ys, vs, ve), normals, data);
        vertex(2, xs, ys, zs, map(1.0F - xs, us, ue), map(1.0F - ys, vs, ve), normals, data);
        vertex(3, xs, ye, zs, map(1.0F - xs, us, ue), map(1.0F - ye, vs, ve), normals, data);
        break;
      case SOUTH:
        depth = 1.0F - ze;
        vertex(0, xs, ye, ze, map(xs, us, ue), map(1.0F - ye, vs, ve), normals, data);
        vertex(1, xs, ys, ze, map(xs, us, ue), map(1.0F - ys, vs, ve), normals, data);
        vertex(2, xe, ys, ze, map(xe, us, ue), map(1.0F - ys, vs, ve), normals, data);
        vertex(3, xe, ye, ze, map(xe, us, ue), map(1.0F - ye, vs, ve), normals, data);
        break;
      case WEST:
        depth = xs;
        vertex(0, xs, ye, zs, map(zs, us, ue), map(1.0F - ye, vs, ve), normals, data);
        vertex(1, xs, ys, zs, map(zs, us, ue), map(1.0F - ys, vs, ve), normals, data);
        vertex(2, xs, ys, ze, map(ze, us, ue), map(1.0F - ys, vs, ve), normals, data);
        vertex(3, xs, ye, ze, map(ze, us, ue), map(1.0F - ye, vs, ve), normals, data);
        break;
      case EAST:
        depth = 1.0F - xe;
        vertex(0, xe, ye, ze, map(1.0F - ze, us, ue), map(1.0F - ye, vs, ve), normals, data);
        vertex(1, xe, ys, ze, map(1.0F - ze, us, ue), map(1.0F - ys, vs, ve), normals, data);
        vertex(2, xe, ys, zs, map(1.0F - zs, us, ue), map(1.0F - ys, vs, ve), normals, data);
        vertex(3, xe, ye, zs, map(1.0F - zs, us, ue), map(1.0F - ye, vs, ve), normals, data);
        break;
      default:
        throw new IllegalStateException();
    }

    MeshQuad quad = new MeshQuad(data, -1, face, texture, true);
    Direction cullFace = Math.abs(depth) < 1.0E-5 ? face : null;
    emitter[getIdx(cullFace)].add(quad);
  }

}
