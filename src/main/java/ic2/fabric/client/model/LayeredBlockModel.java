package ic2.fabric.client.model;

import ic2.core.model.BlockMesh;
import ic2.core.model.MeshQuad;
import java.util.List;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Applies the block's explicitly registered cutout/translucency setting to each material. */
final class LayeredBlockModel implements BlockMesh {
  private final BlockMesh source;
  private final ChunkSectionLayer layer;
  LayeredBlockModel(BlockStateModel source, ChunkSectionLayer layer) {
    this.source = BlockMesh.wrap(source);
    this.layer = layer;
  }
  @Override public List<MeshQuad> getQuads(BlockState state, Direction side, RandomSource random) {
    return source.getQuads(state, side, random).stream().map(quad -> quad.withLayer(layer)).toList();
  }
  @Override public TextureAtlasSprite getParticleIcon() { return source.getParticleIcon(); }
  @Override public boolean useAmbientOcclusion() { return source.useAmbientOcclusion(); }
}
