package ic2.core.block.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ic2.api.tile.IRotorProvider;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;

public class KineticGeneratorRenderer<T extends BlockEntity & IRotorProvider>
    implements BlockEntityRenderer<T, KineticGeneratorRenderer.State> {
  public static class State extends BlockEntityRenderState {
    int diameter;
    float angle;
    Identifier texture;
    Direction facing;
  }
  private static final float DEGREES_PER_TICK = 50.0F;
  private static final Int2ReferenceMap<ModelPart> rotorModels = new Int2ReferenceOpenHashMap<>();

  public KineticGeneratorRenderer(Context ctx) {}

  @Override public State createRenderState() { return new State(); }

  @Override public void extractRenderState(T windGen, State state, float tickDelta,
      Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breaking) {
    BlockEntityRenderer.super.extractRenderState(windGen, state, tickDelta, cameraPos, breaking);
    state.diameter = windGen.getRotorDiameter();
    state.angle = (windGen.getAngle() + windGen.getRotorAnimationSpeed() * DEGREES_PER_TICK
        * (Minecraft.getInstance().isPaused() ? 0.0F : tickDelta)) % 360.0F;
    state.texture = windGen.getRotorRenderTexture();
    state.facing = windGen.getFacing();
    if (windGen.getLevel() != null) {
      state.lightCoords = net.minecraft.util.LightCoordsUtil.getLightCoords(
          windGen.getLevel(), windGen.getBlockPos().relative(state.facing));
    }
  }

  @Override public boolean shouldRenderOffScreen() { return true; }

  @Override public void submit(State state, PoseStack matrices, SubmitNodeCollector collector,
      CameraRenderState cameraState) {
    int diameter = state.diameter;
    if (diameter != 0) {
      float angle = state.angle;
      Identifier rotorRL = state.texture;
      ModelPart model = (ModelPart) rotorModels.get(diameter);
      if (model == null) {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition modelPartData = modelData.getRoot();
        modelPartData.addOrReplaceChild(
            "1",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(0.0F, 0.0F, -4.0F, 1.0F, diameter * 8, 8.0F),
            PartPose.offsetAndRotation(-8.0F, 0.0F, 0.0F, 0.0F, -0.5F, 0.0F));
        modelPartData.addOrReplaceChild(
            "2",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(0.0F, 0.0F, -4.0F, 1.0F, diameter * 8, 8.0F),
            PartPose.offsetAndRotation(-8.0F, 0.0F, 0.0F, 3.1F, 0.5F, 0.0F));
        modelPartData.addOrReplaceChild(
            "3",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(0.0F, 0.0F, -4.0F, 1.0F, diameter * 8, 8.0F),
            PartPose.offsetAndRotation(-8.0F, 0.0F, 0.0F, 4.7F, 0.0F, 0.5F));
        modelPartData.addOrReplaceChild(
            "4",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(0.0F, 0.0F, -4.0F, 1.0F, diameter * 8, 8.0F),
            PartPose.offsetAndRotation(-8.0F, 0.0F, 0.0F, 1.5F, 0.0F, -0.5F));
        model = LayerDefinition.create(modelData, 32, 256).bakeRoot();
        rotorModels.put(diameter, model);
      }

      Direction facing = state.facing;
      matrices.pushPose();
      matrices.translate(0.5, 0.5, 0.5);
      switch (facing) {
        case NORTH:
          matrices.rotateDegrees(Axis.YP, -90.0F);
          break;
        case EAST:
          matrices.rotateDegrees(Axis.YP, -180.0F);
          break;
        case SOUTH:
          matrices.rotateDegrees(Axis.YP, -270.0F);
          break;
        case UP:
          matrices.rotateDegrees(Axis.ZP, -90.0F);
      }

      matrices.rotateDegrees(Axis.XP, angle);
      matrices.translate(-0.2, 0.0, 0.0);
      collector.submitModelPart(model, matrices, RenderTypes.entitySolid(rotorRL),
          state.lightCoords, OverlayTexture.NO_OVERLAY, null);
      matrices.popPose();
    }
  }
}
