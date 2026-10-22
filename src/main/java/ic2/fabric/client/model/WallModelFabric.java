package ic2.fabric.client.model;

import ic2.core.block.comp.Obscuration;
import ic2.core.block.misc.WallBlock;
import ic2.core.block.tileentity.TileEntityWall;
import ic2.core.item.tool.ItemObscurator;
import java.util.ArrayList;
import java.util.Collection;
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
import net.minecraft.client.Minecraft;
import ic2.core.model.MeshQuad;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import ic2.core.model.BlockMesh;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric port of {@code ic2.forge.model.WallModelForge}.
 *
 * <p>The construction walls are the one IC2 block whose model is assembled from another block's
 * model plus the obscuration overlays sprayed onto it, so the mesh depends on the block entity at
 * each position. On NeoForge that meant {@code getModelData}; on Fabric the same work happens in
 * {@link #emitBlockQuads}, which Indigo already calls per position.
 */
@Environment(EnvType.CLIENT)
public final class WallModelFabric implements BlockMesh, FabricBlockStateModel {
  private static final float OVERLAY_OFFSET = 0.001F;
  private final java.util.Map<DyeColor, BlockMesh> wallModels = new java.util.EnumMap<>(DyeColor.class);

  WallModelFabric(ModelBaker baker) {
    for (DyeColor color : DyeColor.values()) {
      wallModels.put(color, BlockMesh.bake(baker,
          Identifier.fromNamespaceAndPath("ic2", "block/cf/wall_" + color.getSerializedName()),
          net.minecraft.client.renderer.block.dispatch.BlockModelRotation.IDENTITY));
    }
  }

  public Collection<Identifier> getDependencies() {
    return Collections.emptyList();
  }

  public void resolveParents(Function<Identifier, UnbakedModel> resolver) {}

  public BlockMesh bake(
      ModelBaker bakery,
      Function<Material, TextureAtlasSprite> spriteGetter,
      ModelState modelTransform) {
    return this;
  }

  private List<MeshQuad>[] buildDefaultMesh(DyeColor color) {
    Block wallBlock = WallBlock.get(color);
    if (wallBlock == null) {
      wallBlock = WallBlock.get(WallBlock.DEFAULT_COLOR);
    }

    return buildMeshForState(wallBlock.defaultBlockState(), color, null);
  }

  private List<MeshQuad>[] buildMesh(BlockState state, TileEntityWall wall) {
    DyeColor color = wall.getColor();
    Block wallBlock = WallBlock.get(color);
    if (wallBlock == null) {
      wallBlock = WallBlock.get(WallBlock.DEFAULT_COLOR);
    }

    BlockState baseState = wallBlock.defaultBlockState();
    Obscuration obscuration = wall.getComponent(Obscuration.class);
    Obscuration.ObscurationData[] obscurations =
        obscuration != null ? obscuration.getRenderState() : null;
    return buildMeshForState(baseState, color, obscurations);
  }

  private List<MeshQuad>[] buildMeshForState(
      BlockState baseState, DyeColor color, Obscuration.ObscurationData[] obscurations) {
    BlockMesh baseModel = wallModels.get(color);
    RandomSource rand = RandomSource.create(42L);
    List<MeshQuad>[] mesh = new List[6];

    for (Direction side : Direction.values()) {
      List<MeshQuad> faceQuads = new ArrayList<>(baseModel.getQuads(baseState, side, rand));
      Obscuration.ObscurationData data = obscurations != null ? obscurations[side.ordinal()] : null;
      if (data != null) {
        ItemObscurator.ObscuredRenderInfo renderInfo =
            ItemObscurator.getRenderInfo(data.state(), data.side());
        if (renderInfo != null && renderInfo.uvs.length == data.colorMultipliers().length * 4) {
          for (int texture = 0; texture < data.colorMultipliers().length; texture++) {
            MeshQuad overlay =
                OverlayRenderUtil.applyBlockOverlay(
                    OverlayRenderUtil.createBlockOverlayQuad(side, OVERLAY_OFFSET),
                    renderInfo,
                    texture,
                    data.colorMultipliers()[texture],
                    side);
            faceQuads.add(overlay);
          }
        }
      }

      mesh[side.ordinal()] = faceQuads.isEmpty() ? Collections.emptyList() : faceQuads;
    }

    return mesh;
  }

  private static TextureAtlasSprite resolveParticleIcon(TileEntityWall wall) {
    Obscuration obscuration = wall.getComponent(Obscuration.class);
    Obscuration.ObscurationData[] obscurations =
        obscuration != null ? obscuration.getRenderState() : null;
    Obscuration.ObscurationData topData =
        obscurations != null ? obscurations[Direction.UP.ordinal()] : null;
    if (topData != null) {
      ItemObscurator.ObscuredRenderInfo renderInfo =
          ItemObscurator.getRenderInfo(topData.state(), topData.side());
      if (renderInfo != null && renderInfo.sprites.length > 0) {
        return renderInfo.sprites[0];
      }
    }

    Block wallBlock = WallBlock.get(wall.getColor());
    return ModelUtil.getBlockModel(wallBlock.defaultBlockState())
        .getParticleIcon();
  }

  @Override
  public void emitQuads(QuadEmitter emitter, BlockAndTintGetter world, BlockPos pos,
      BlockState state, RandomSource random, Predicate<Direction> cullTest) {
    List<MeshQuad>[] mesh =
        world.getBlockEntity(pos) instanceof TileEntityWall wall
            ? buildMesh(state, wall)
            : buildDefaultMesh(WallBlock.DEFAULT_COLOR);

    for (int i = 0; i < mesh.length; i++) {
      Direction cull = Direction.values()[i];
      if (!cullTest.test(cull)) QuadEmit.emitAll(emitter, mesh[i], cull);
    }
  }

  @Override
  public List<MeshQuad> getQuads(BlockState state, Direction side, RandomSource random) {
    return side == null ? List.of() : buildDefaultMesh(WallBlock.DEFAULT_COLOR)[side.ordinal()];
  }

  @Override
  public boolean useAmbientOcclusion() {
    return true;
  }

  @Override
  public boolean isGui3d() {
    return true;
  }

  @Override
  public boolean usesBlockLight() {
    return true;
  }

  @Override
  public boolean isCustomRenderer() {
    return false;
  }

  @Override
  public TextureAtlasSprite getParticleIcon() {
    return wallModels.get(WallBlock.DEFAULT_COLOR).getParticleIcon();
  }

  @Override public Material.Baked particleMaterial(BlockAndTintGetter world, BlockPos pos, BlockState state) {
    return world.getBlockEntity(pos) instanceof TileEntityWall wall
        ? new Material.Baked(resolveParticleIcon(wall), false) : particleMaterial();
  }

  @Override public int materialFlags(BlockAndTintGetter world, BlockPos pos,
      BlockState state, RandomSource random) {
    int flags = materialFlags();
    if (world.getBlockEntity(pos) instanceof TileEntityWall wall) {
      Obscuration obscuration = wall.getComponent(Obscuration.class);
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
