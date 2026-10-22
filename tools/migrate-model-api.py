from pathlib import Path
import re
r=Path('src/main/java')
for name in ['ic2/core/block/DynamicBeModel.java','ic2/core/block/wiring/DynamicCableModel.java']:
 p=r/name;s=p.read_text().replace('implements UnbakedModel, BlockMesh','implements BlockMesh')
 s=s.replace('loader.bake(this.backingModelId, rotationContainer)','BlockMesh.bake(loader, this.backingModelId, rotationContainer)').replace('loader.bake(this.getActiveModelId(), rotationContainer)','BlockMesh.bake(loader, this.getActiveModelId(), rotationContainer)')
 s=s.replace('BuiltInRegistries.BLOCK.get(blockId)','BuiltInRegistries.BLOCK.getValue(blockId)')
 s=s.replace('Identifier atlas = TextureAtlas.LOCATION_BLOCKS;\n    return new Material(atlas, IC2.getIdentifier(path));','return new Material(IC2.getIdentifier(path));')
 s=s.replace('} else if (!this.foam.isPresent()) {','} else if (!this.hasFoam()) {')
 p.write_text(s)
p=r/'ic2/fabric/client/model/DynamicBeModelFabric.java';s=p.read_text()
s=s.replace('import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;','import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;\nimport java.util.function.Predicate;\nimport ic2.core.model.ModelUtil;\nimport net.minecraft.client.renderer.block.dispatch.BlockStateModel;')
s=s.replace('  private final Identifier modelId;','  private final Identifier modelId;\n  private BlockState wrappedState;')
s=s.replace('  private static int getIdx', '''  DynamicBeModelFabric(BlockState state, BlockStateModel model) {
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

  private static int getIdx''')
s=re.sub(r'BlockMesh refModel =\s*Minecraft.getInstance\(\)\s*\.getBlockRenderer\(\)\s*\.getBlockModelShaper\(\)\s*\.getBlockModel\(data.state\(\)\);','BlockMesh refModel = ModelUtil.getBlockModel(data.state());',s)
s=s.replace('  @Override\n  public BlockMesh bake(', '  public BlockMesh bake(')
s=s.replace('  public void emitBlockQuads(\n      BlockAndTintGetter world,\n      BlockState state,\n      BlockPos pos,\n      Supplier<RandomSource> randomSupplier,\n      RenderContext context)', '  public void emitQuads(QuadEmitter emitter, BlockAndTintGetter world, BlockPos pos,\n      BlockState state, RandomSource random, Predicate<Direction> cullTest)')
s=s.replace('    var emitter = context.getEmitter();\n    for (List<MeshQuad> quads : mesh) {\n      QuadEmit.emitAll(emitter, quads);\n    }','''    for (int i = 0; i < mesh.size(); i++) {
      Direction cull = i == 6 ? null : Direction.values()[i];
      if (cull == null || !cullTest.test(cull)) QuadEmit.emitAll(emitter, mesh.get(i), cull);
    }''')
p.write_text(s)
p=r/'ic2/fabric/client/model/DynamicCableModelFabric.java';s=p.read_text()
s=s.replace('import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;','import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;\nimport java.util.function.Predicate;')
s=s.replace('  @Override\n  public BlockMesh bake(', '  public BlockMesh bake(')
s=s.replace('  public void emitBlockQuads(\n      BlockAndTintGetter world,\n      BlockState state,\n      BlockPos pos,\n      Supplier<RandomSource> randomSupplier,\n      RenderContext context)', '  public void emitQuads(QuadEmitter emitter, BlockAndTintGetter world, BlockPos pos,\n      BlockState state, RandomSource random, Predicate<Direction> cullTest)')
a=s.index('    if (state.getBlock()');b=s.index('    List<MeshQuad>[] mesh',a)
s=s[:a]+s[b:]
s=s.replace('    var emitter = context.getEmitter();\n\n    for (List<MeshQuad> quads : mesh) {\n      QuadEmit.emitAll(emitter, quads);\n    }','''    for (int i = 0; i < mesh.length; i++) {
      Direction cull = i == 6 ? null : Direction.values()[i];
      if (cull == null || !cullTest.test(cull)) QuadEmit.emitAll(emitter, mesh[i], cull);
    }''')
s=s.replace('  protected List<MeshQuad>[] generateMesh', '''  public List<MeshQuad> getQuads(BlockState state, Direction side, RandomSource random) {
    List<MeshQuad>[] mesh = state == null ? generateMesh(DyeColor.BLACK, 0) : getMesh(state);
    return mesh[side == null ? 6 : side.ordinal()];
  }

  protected List<MeshQuad>[] generateMesh''')
p.write_text(s)
p=r/'ic2/fabric/client/model/WallModelFabric.java';s=p.read_text()
s=s.replace('implements UnbakedModel, BlockMesh, FabricBlockMesh','implements BlockMesh, FabricBlockStateModel')
s=s.replace('import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;','import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;\nimport java.util.function.Predicate;\nimport ic2.core.model.ModelUtil;')
s=s.replace('  @Override\n  public Collection<Identifier>', '  public Collection<Identifier>').replace('  @Override\n  public void resolveParents','  public void resolveParents').replace('  @Override\n  public BlockMesh bake','  public BlockMesh bake')
s=re.sub(r'Minecraft.getInstance\(\)\s*\.getBlockRenderer\(\)\s*\.getBlockModelShaper\(\)\s*\.getBlockModel\((baseState|wallBlock.defaultBlockState\(\)|WallBlock.get\(WallBlock.DEFAULT_COLOR\).defaultBlockState\(\))\)',r'ModelUtil.getBlockModel(\1)',s)
s=s.replace('  public void emitBlockQuads(\n      BlockAndTintGetter world,\n      BlockState state,\n      BlockPos pos,\n      Supplier<RandomSource> randomSupplier,\n      RenderContext context)', '  public void emitQuads(QuadEmitter emitter, BlockAndTintGetter world, BlockPos pos,\n      BlockState state, RandomSource random, Predicate<Direction> cullTest)')
s=s.replace('    var emitter = context.getEmitter();\n    for (List<MeshQuad> quads : mesh) {\n      QuadEmit.emitAll(emitter, quads);\n    }','''    for (int i = 0; i < mesh.length; i++) {
      Direction cull = Direction.values()[i];
      if (!cullTest.test(cull)) QuadEmit.emitAll(emitter, mesh[i], cull);
    }''')
s=s.replace('    return Collections.emptyList();\n  }\n\n  @Override\n  public boolean useAmbientOcclusion()', '    return side == null ? List.of() : buildDefaultMesh(WallBlock.DEFAULT_COLOR)[side.ordinal()];\n  }\n\n  @Override\n  public boolean useAmbientOcclusion()')
p.write_text(s)
