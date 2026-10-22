from pathlib import Path
root=Path('src/main/java')
owned=list((root/'ic2/fabric/client/model').glob('*.java'))+[root/'ic2/core/block/DynamicBeModel.java',root/'ic2/core/block/wiring/DynamicCableModel.java']
for p in owned:
 s=p.read_text()
 s=s.replace('net.minecraft.client.renderer.block.model.BakedQuad','ic2.core.model.MeshQuad').replace('BakedQuad','MeshQuad')
 s=s.replace('net.minecraft.client.resources.model.BakedModel','ic2.core.model.BlockMesh').replace('BakedModel','BlockMesh')
 s=s.replace('net.minecraft.client.resources.model.Material','net.minecraft.client.resources.model.sprite.Material')
 s=s.replace('net.minecraft.client.resources.model.ModelState','net.minecraft.client.renderer.block.dispatch.ModelState')
 s=s.replace('net.minecraft.client.renderer.block.model.ItemTransforms','net.minecraft.client.resources.model.cuboid.ItemTransforms')
 s=s.replace('import net.minecraft.client.renderer.block.model.ItemOverrides;\n','')
 s=s.replace('net.minecraft.world.level.BlockAndTintGetter','net.minecraft.client.renderer.block.BlockAndTintGetter')
 s=s.replace('import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockMesh;','import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;')
 s=s.replace('implements FabricBlockMesh','implements FabricBlockStateModel')
 import re
 s=re.sub(r'  (?:@Override\s+)?public (?:@(?:NotNull|Nullable) )?ItemOverrides getOverrides\(\) \{\s*return ItemOverrides.EMPTY;\s*\}\n','',s)
 p.write_text(s)
