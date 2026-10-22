package ic2.core.block.beam;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

public class BeamRenderer extends EntityRenderer<ParticleEntity, EntityRenderState> {
  private static final Identifier texture =
      Identifier.fromNamespaceAndPath("ic2", "textures/models/beam.png");

  public BeamRenderer(Context context) {
    super(context);
  }

  @Override
  public EntityRenderState createRenderState() { return new EntityRenderState(); }

  @Override
  public void submit(EntityRenderState state, PoseStack matrices, SubmitNodeCollector nodes, CameraRenderState camera) {
    int light = state.lightCoords;
    float u1 = 0.0F;
    float u2 = 1.0F;
    float v1 = 0.0F;
    float v2 = 1.0F;
    float scale = 0.1F;
    int red = 255;
    int green = 255;
    int blue = 255;
    int alpha = 255;
    matrices.pushPose();
    matrices.rotate(camera.orientation);
    nodes.submitCustomGeometry(matrices, RenderTypes.entityTranslucentCull(texture), (entry, vertexConsumer) -> {
    for (int i = 0; i < 4; i++) {
      float x;
      float u;
      if (i < 2) {
        x = -scale;
        u = u1;
      } else {
        x = scale;
        u = u2;
      }

      float y;
      float v;
      if (i != 0 && i != 3) {
        y = scale;
        v = v2;
      } else {
        y = -scale;
        v = v1;
      }

      vertexConsumer
          .addVertex(entry, x, y, 0.0F)
          .setColor(red, green, blue, alpha)
          .setUv(u, v)
          .setOverlay(OverlayTexture.NO_OVERLAY)
          .setLight(light)
          .setNormal(entry, 0.0F, 1.0F, 0.0F);
    }

    });
    matrices.popPose();
    super.submit(state, matrices, nodes, camera);
  }

}
