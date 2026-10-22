package ic2.client.gametest;

import com.mojang.blaze3d.vertex.VertexConsumer;
import ic2.fabric.DensityDrivenFluid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRendering;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;

/** Exercises the real, mixed-in tessellator after resource loading, not a mirror of its math. */
final class GasFluidRenderChecks {
  private static final BlockPos POS = new BlockPos(3, 70, 3);
  private static final float BASE_Y = POS.getY() & 15;

  static List<String> validate(Minecraft client) {
    List<String> evidence = new ArrayList<>();
    for (String id : List.of("ic2:steam", "ic2:superheated_steam", "ic2:air", "ic2:hydrogen", "ic2:oxygen")) {
      check(client, id, true, evidence);
    }
    // Biogas is flagged gaseous but has positive density, so it must stay bottom-filled.
    for (String id : List.of("minecraft:water", "minecraft:lava", "ic2:coolant", "ic2:biogas")) {
      check(client, id, false, evidence);
    }
    return evidence;
  }

  private static void check(Minecraft client, String id, boolean upward, List<String> evidence) {
    var fluid = BuiltInRegistries.FLUID.getValue(Identifier.parse(id));
    require(fluid instanceof FlowingFluid, "Missing flowing fluid " + id);
    if (upward) {
      require(FluidRenderingRegistry.get(fluid)
              == FluidRenderingRegistry.get(((FlowingFluid) fluid).getFlowing()),
          id + ": source and flowing gas handlers differ");
    }
    FluidState state = ((FlowingFluid) fluid).getFlowing(4, false);
    BlockState block = state.createLegacyBlock();
    var model = client.getModelManager().getFluidStateModelSet().get(state);
    require(!model.stillMaterial().sprite().contents().name().getPath().equals("missingno"), "Missing fluid sprite " + id);
    require(!model.flowingMaterial().sprite().contents().name().getPath().equals("missingno"), "Missing flowing sprite " + id);
    Scene flat = new Scene(client);
    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
      flat.blocks.put(POS.offset(dx, 0, dz), block);
    }
    List<Vertex> mesh = render(client, flat, block, state);
    float surface = upward ? BASE_Y + 1 - 4f / 9f + .001f : BASE_Y + 4f / 9f - .001f;
    float fixed = upward ? BASE_Y + 1 - .001f : BASE_Y + .001f;
    require(mesh.size() == 12, id + ": expected surface, surface backface and fixed face, got " + mesh.size());
    require(close(mesh.getFirst().y, surface), id + ": wrong free surface height " + mesh.getFirst().y);
    require(close(mesh.get(8).y, fixed), id + ": wrong fixed face height " + mesh.get(8).y);
    require(normalY(mesh, 0) * (upward ? -1 : 1) > 0, id + ": free surface winding points inside");
    require(normalY(mesh, 8) * (upward ? 1 : -1) > 0, id + ": fixed face winding points inside");
    for (Vertex vertex : mesh) {
      require(vertex.u >= 0 && vertex.u <= 1 && vertex.v >= 0 && vertex.v <= 1,
          id + ": invalid atlas UV");
      require(vertex.y >= BASE_Y && vertex.y <= BASE_Y + 1, id + ": vertex outside its block");
    }

    // A block on the free-surface side fills the whole volume; the same fluid on
    // the fixed-face side only hides that face and must not alter the height.
    BlockPos freeNeighbor = upward ? POS.below() : POS.above();
    BlockPos fixedNeighbor = upward ? POS.above() : POS.below();
    flat.blocks.put(fixedNeighbor, block);
    List<Vertex> againstSame = render(client, flat, block, state);
    require(againstSame.size() == 8 && close(againstSame.getFirst().y, surface),
        id + ": wrong fixed neighbor culling: vertices=" + againstSame.size()
            + ", surface=" + againstSame.getFirst().y + ", expected=" + surface);
    flat.blocks.remove(fixedNeighbor);
    flat.blocks.put(freeNeighbor, block);
    List<Vertex> full = render(client, flat, block, state);
    require(full.size() == 4 && close(full.getFirst().y, fixed),
        id + ": internal fluid face was not culled: vertices=" + full.size()
            + ", fixed=" + full.getFirst().y + ", expected=" + fixed);

    Scene slabScene = new Scene(client);
    slabScene.blocks.put(POS, block);
    BlockState slab = Blocks.STONE_SLAB.defaultBlockState();
    slabScene.blocks.put(POS.north(), slab.setValue(SlabBlock.TYPE, SlabType.TOP));
    int againstTop = northVertices(render(client, slabScene, block, state));
    slabScene.blocks.put(POS.north(), slab.setValue(SlabBlock.TYPE, SlabType.BOTTOM));
    int againstBottom = northVertices(render(client, slabScene, block, state));
    require(upward ? againstTop == 0 && againstBottom == 8 : againstTop == 8 && againstBottom == 0,
        id + ": wrong slab side occlusion: top=" + againstTop + ", bottom=" + againstBottom);
    evidence.add("Fluid tessellator " + id + ": " + (upward ? "top" : "bottom")
        + " fill; correct height, winding, same-fluid and slab culling; " + mesh.size() + " vertices; "
        + (upward ? "custom gas dispatch bypasses replacement default renderer." : "replacement default renderer retained."));
  }

  private static List<Vertex> render(Minecraft client, Scene scene, BlockState block, FluidState state) {
    Capture capture = new Capture();
    FluidRenderer renderer = new FluidRenderer(client.getModelManager().getFluidStateModelSet());
    FluidRenderHandler handler = FluidRenderingRegistry.get(state.getType());
    require(handler != null, "No registered fluid render handler");
    int[] replacementDefaultCalls = {0};
    // Use the same public dispatch entry point as Sodium. Its replacement
    // default renderer must be bypassed only by upward gases, not every fluid.
    FluidRendering.render(renderer, handler, scene, POS, layer -> capture, block, state,
        new FluidRendering.DefaultRenderer() {
          @Override public void render(FluidRenderer nativeRenderer, FluidRenderHandler selectedHandler,
              BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output,
              BlockState blockState, FluidState fluidState) {
            replacementDefaultCalls[0]++;
            FluidRendering.DefaultRenderer.super.render(nativeRenderer, selectedHandler, level, pos,
                output, blockState, fluidState);
          }
        });
    int expectedCalls = DensityDrivenFluid.isUpward(state.getType()) ? 0 : 1;
    require(replacementDefaultCalls[0] == expectedCalls,
        "Wrong renderer dispatch for " + BuiltInRegistries.FLUID.getKey(state.getType())
            + ": replacement default calls=" + replacementDefaultCalls[0] + ", expected=" + expectedCalls);
    require(!capture.vertices.isEmpty(), "Fluid tessellator emitted no vertices");
    return capture.vertices;
  }

  private static int northVertices(List<Vertex> mesh) {
    return (int) mesh.stream().filter(v -> close(v.z, (POS.getZ() & 15) + .001f)).count();
  }

  private static float normalY(List<Vertex> mesh, int start) {
    Vertex a = mesh.get(start), b = mesh.get(start + 1), c = mesh.get(start + 2);
    return (b.z - a.z) * (c.x - a.x) - (b.x - a.x) * (c.z - a.z);
  }

  private static boolean close(float a, float b) { return Math.abs(a - b) < .0001f; }
  private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
  private record Vertex(float x, float y, float z, float u, float v) {}

  private static final class Scene implements BlockAndTintGetter {
    final Map<BlockPos, BlockState> blocks = new HashMap<>();
    final Minecraft client;
    Scene(Minecraft client) { this.client = client; }
    @Override public BlockState getBlockState(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
    @Override public int getHeight() { return client.level.getHeight(); }
    @Override public int getMinY() { return client.level.getMinY(); }
    @Override public LevelLightEngine getLightEngine() { return client.level.getLightEngine(); }
    @Override public int getBrightness(LightLayer layer, BlockPos pos) { return 15; }
    @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) { return 0xFFFFFF; }
    @Override public CardinalLighting cardinalLighting() { return CardinalLighting.DEFAULT; }
  }

  private static final class Capture implements VertexConsumer {
    final List<Vertex> vertices = new ArrayList<>();
    @Override public void addVertex(float x, float y, float z, int color, float u, float v,
        int overlay, int light, float nx, float ny, float nz) {
      vertices.add(new Vertex(x, y, z, u, v));
    }
    private AssertionError changedFormat() { return new AssertionError("FluidRenderer vertex API changed; update the test capture"); }
    @Override public VertexConsumer addVertex(float x, float y, float z) { throw changedFormat(); }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) { throw changedFormat(); }
    @Override public VertexConsumer setColor(int color) { throw changedFormat(); }
    @Override public VertexConsumer setUv(float u, float v) { throw changedFormat(); }
    @Override public VertexConsumer setUv1(int u, int v) { throw changedFormat(); }
    @Override public VertexConsumer setUv2(int u, int v) { throw changedFormat(); }
    @Override public VertexConsumer setUv3(float u, float v) { throw changedFormat(); }
    @Override public VertexConsumer setNormal(float x, float y, float z) { throw changedFormat(); }
    @Override public VertexConsumer setLineWidth(float width) { throw changedFormat(); }
  }
}
