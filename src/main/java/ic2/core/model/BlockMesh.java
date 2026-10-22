package ic2.core.model;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Shared mesh operations for IC2's procedural geometry and vanilla model parts. */
public interface BlockMesh extends BlockStateModel {
  List<MeshQuad> getQuads(BlockState state, Direction side, RandomSource random);
  TextureAtlasSprite getParticleIcon();
  default ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }
  default boolean useAmbientOcclusion() { return true; }
  default boolean isGui3d() { return true; }
  default boolean usesBlockLight() { return true; }
  default boolean isCustomRenderer() { return false; }
  @Override default Material.Baked particleMaterial() { return new Material.Baked(getParticleIcon(), false); }
  @Override default int materialFlags() {
    int flags = 0;
    var random = RandomSource.create(42);
    for (int i = 0; i < 7; i++) {
      for (MeshQuad quad : getQuads(null, i == 6 ? null : Direction.values()[i], random)) {
        flags |= quad.toNative().materialInfo().flags();
      }
    }
    return flags;
  }
  @Override default void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
    List<List<BakedQuad>> faces = new ArrayList<>(7);
    for (int i = 0; i < 7; i++) {
      faces.add(getQuads(null, i == 6 ? null : Direction.values()[i], random).stream().map(MeshQuad::toNative).toList());
    }
    Material.Baked particle = particleMaterial();
    boolean ao = useAmbientOcclusion();
    int flags = materialFlags();
    parts.add(new BlockStateModelPart() {
      public List<BakedQuad> getQuads(Direction side) { return faces.get(side == null ? 6 : side.ordinal()); }
      public boolean useAmbientOcclusion() { return ao; }
      public Material.Baked particleMaterial() { return particle; }
      public int materialFlags() { return flags; }
    });
  }
  static BlockMesh wrap(BlockStateModel model) {
    if (model instanceof BlockMesh mesh) return mesh;
    return new BlockMesh() {
      public List<MeshQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);
        List<MeshQuad> quads = new ArrayList<>();
        for (var part : parts) for (var quad : part.getQuads(side)) quads.add(MeshQuad.fromNative(quad));
        return quads;
      }
      public TextureAtlasSprite getParticleIcon() { return model.particleMaterial().sprite(); }
      public int materialFlags() { return model.materialFlags(); }
    };
  }
  static BlockMesh bake(ModelBaker baker, Identifier id, ModelState state) {
    var resolved = baker.getModel(id);
    var slots = resolved.getTopTextureSlots();
    var quads = resolved.bakeTopGeometry(slots, baker, state);
    var particle = resolved.resolveParticleMaterial(slots, baker);
    return new BlockMesh() {
      public List<MeshQuad> getQuads(BlockState blockState, Direction side, RandomSource random) {
        return quads.getQuads(side).stream().map(MeshQuad::fromNative).toList();
      }
      public TextureAtlasSprite getParticleIcon() { return particle.sprite(); }
      public ItemTransforms getTransforms() { return resolved.getTopTransforms(); }
      public boolean useAmbientOcclusion() { return resolved.getTopAmbientOcclusion(); }
      public boolean usesBlockLight() {
        return resolved.getTopGuiLight() == net.minecraft.client.resources.model.UnbakedModel.GuiLight.SIDE;
      }
      public int materialFlags() { return quads.materialFlags(); }
    };
  }
}
