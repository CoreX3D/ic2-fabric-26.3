package ic2.core.entity.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import ic2.core.IC2;
import ic2.core.entity.LaserBulletEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class LaserBulletEntityRenderer extends EntityRenderer<LaserBulletEntity, LaserBulletEntityRenderer.State> {
  private static final Identifier TEXTURE = IC2.getIdentifier("textures/models/laser.png");

  public LaserBulletEntityRenderer(Context context) {
    super(context);
  }

  @Override
  public State createRenderState() { return new State(); }

  @Override
  public void extractRenderState(LaserBulletEntity entity, State state, float partialTick) {
    super.extractRenderState(entity, state, partialTick);
    state.yRot = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
    state.xRot = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
  }

  @Override
  public void submit(State state, PoseStack matrixStack, SubmitNodeCollector nodes, CameraRenderState camera) {
    matrixStack.pushPose();
    matrixStack.rotateDegrees(Axis.YP, state.yRot - 90.0F);
    matrixStack.rotateDegrees(Axis.ZP, state.xRot);
    matrixStack.rotateDegrees(Axis.XP, 45.0F);
    matrixStack.scale(0.05625F, 0.05625F, 0.05625F);
    matrixStack.translate(-4.0, 0.0, 0.0);
    int i = state.lightCoords;
    nodes.submitCustomGeometry(matrixStack, RenderTypes.entityCutout(TEXTURE), (entry, vertexConsumer) -> {
    this.vertex(entry, vertexConsumer, -7, -2, -2, 0.0F, 0.15625F, -1, 0, 0, i);
    this.vertex(entry, vertexConsumer, -7, -2, 2, 0.15625F, 0.15625F, -1, 0, 0, i);
    this.vertex(entry, vertexConsumer, -7, 2, 2, 0.15625F, 0.3125F, -1, 0, 0, i);
    this.vertex(entry, vertexConsumer, -7, 2, -2, 0.0F, 0.3125F, -1, 0, 0, i);
    this.vertex(entry, vertexConsumer, -7, 2, -2, 0.0F, 0.15625F, 1, 0, 0, i);
    this.vertex(entry, vertexConsumer, -7, 2, 2, 0.15625F, 0.15625F, 1, 0, 0, i);
    this.vertex(entry, vertexConsumer, -7, -2, 2, 0.15625F, 0.3125F, 1, 0, 0, i);
    this.vertex(entry, vertexConsumer, -7, -2, -2, 0.0F, 0.3125F, 1, 0, 0, i);

    });
    for (int u = 0; u < 4; u++) {
      matrixStack.rotateDegrees(Axis.XP, 90.0F);
      nodes.submitCustomGeometry(matrixStack, RenderTypes.entityCutout(TEXTURE), (entry, vertexConsumer) -> {
      this.vertex(entry, vertexConsumer, -8, -2, 0, 0.0F, 0.0F, 0, 1, 0, i);
      this.vertex(entry, vertexConsumer, 8, -2, 0, 0.5F, 0.0F, 0, 1, 0, i);
      this.vertex(entry, vertexConsumer, 8, 2, 0, 0.5F, 0.15625F, 0, 1, 0, i);
      this.vertex(entry, vertexConsumer, -8, 2, 0, 0.0F, 0.15625F, 0, 1, 0, i);
      });
    }

    matrixStack.popPose();
    super.submit(state, matrixStack, nodes, camera);
  }

  public void vertex(
      Pose pose,
      VertexConsumer vertexConsumer,
      int x,
      int y,
      int z,
      float u,
      float v,
      int normalX,
      int normalZ,
      int normalY,
      int light) {
    vertexConsumer
        .addVertex(pose, (float) x, (float) y, (float) z)
        .setColor(255, 255, 255, 255)
        .setUv(u, v)
        .setOverlay(OverlayTexture.NO_OVERLAY)
        .setLight(light)
        .setNormal(pose, normalX, normalY, normalZ);
  }

  public static class State extends EntityRenderState {
    public float yRot;
    public float xRot;
  }
}
