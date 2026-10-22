package ic2.core.crop;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ic2.api.crops.CropCard;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Renders crops which use the generic crop-stick block instead of a dedicated crop block. */
public class CropStickRenderer implements BlockEntityRenderer<TileEntityCrop, CropStickRenderer.State> {
  public static class State extends BlockEntityRenderState { Identifier texture; }
  public CropStickRenderer(BlockEntityRendererProvider.Context context) {}

  @Override
  public State createRenderState() { return new State(); }

  @Override
  public void extractRenderState(TileEntityCrop cropTile, State state, float partialTick,
      Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breaking) {
    BlockEntityRenderer.super.extractRenderState(cropTile, state, partialTick, cameraPos, breaking);
    state.texture = null;
    CropCard crop = cropTile.getCrop();
    if (crop == null) {
      return;
    }

    List<Identifier> textures = crop.getTexturesLocation();
    if (textures.isEmpty()) {
      return;
    }

    int textureIndex = Math.max(0, Math.min(cropTile.getCurrentAge() - 1, textures.size() - 1));
    state.texture = asTextureFile(textures.get(textureIndex));
  }

  @Override
  public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
      CameraRenderState cameraState) {
    if (state.texture == null) return;
    int light = state.lightCoords;
    collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(state.texture), (pose, vertices) -> {
      quad(vertices, pose, 0.0F, 0.001F, 0.5F, 1.0F, 1.001F, 0.5F, light);
      quad(vertices, pose, 0.5F, 0.001F, 0.0F, 0.5F, 1.001F, 1.0F, light);
    });
  }

  private static Identifier asTextureFile(Identifier texture) {
    String path = texture.getPath();
    // CropCard's API retains IC2's old "blocks/" texture convention.
    if (path.startsWith("blocks/")) {
      path = "block/" + path.substring("blocks/".length());
    }
    return Identifier.fromNamespaceAndPath(
        texture.getNamespace(), "textures/" + path + ".png");
  }

  private static void quad(
      VertexConsumer vertices,
      PoseStack.Pose pose,
      float x0,
      float y0,
      float z0,
      float x1,
      float y1,
      float z1,
      int light) {
    vertex(vertices, pose, x0, y1, z0, 0.0F, 0.0F, light);
    vertex(vertices, pose, x0, y0, z0, 0.0F, 1.0F, light);
    vertex(vertices, pose, x1, y0, z1, 1.0F, 1.0F, light);
    vertex(vertices, pose, x1, y1, z1, 1.0F, 0.0F, light);
  }

  private static void vertex(
      VertexConsumer vertices,
      PoseStack.Pose pose,
      float x,
      float y,
      float z,
      float u,
      float v,
      int light) {
    vertices
        .addVertex(pose.pose(), x, y, z)
        .setColor(255, 255, 255, 255)
        .setUv(u, v)
        .setOverlay(OverlayTexture.NO_OVERLAY)
        .setLight(light)
        .setNormal(pose, 0.0F, 1.0F, 0.0F);
  }
}
