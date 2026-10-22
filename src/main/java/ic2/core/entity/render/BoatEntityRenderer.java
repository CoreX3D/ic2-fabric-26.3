package ic2.core.entity.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ic2.api.entity.boat.AbstractBoatEntity;
import ic2.api.entity.boat.BoatType;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.object.boat.BoatModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import org.joml.Quaternionf;

public class BoatEntityRenderer extends EntityRenderer<AbstractBoatEntity, BoatEntityRenderer.State> {
  private final BoatModel model;
  private final Model.Simple waterPatch;
  private final boolean chest;
  private final String modId;

  public BoatEntityRenderer(Context context, boolean chest, String modId) {
    super(context);
    this.shadowRadius = 0.8F;
    this.chest = chest;
    this.modId = modId;
    this.model = new BoatModel(context.bakeLayer(chest ? ModelLayers.OAK_CHEST_BOAT : ModelLayers.OAK_BOAT));
    this.waterPatch = new Model.Simple(context.bakeLayer(ModelLayers.BOAT_WATER_PATCH), texture -> RenderTypes.waterMask());
  }

  @Override
  public State createRenderState() { return new State(); }

  @Override
  public void extractRenderState(AbstractBoatEntity boat, State state, float partialTick) {
    super.extractRenderState(boat, state, partialTick);
    state.texture = Identifier.fromNamespaceAndPath(this.modId, getTextureLocation(boat.getOverrideBoatType(), this.chest));
    state.yRot = boat.getYRot(partialTick);
    state.hurtDir = boat.getHurtDir();
    state.hurtTime = boat.getHurtTime() - partialTick;
    state.damageTime = Math.max(boat.getDamage() - partialTick, 0.0F);
    state.bubbleAngle = boat.getBubbleAngle(partialTick);
    state.isUnderWater = boat.isUnderWater();
    state.rowingTimeLeft = boat.getRowingTime(0, partialTick);
    state.rowingTimeRight = boat.getRowingTime(1, partialTick);
  }

  @Override
  public void submit(State state, PoseStack poses, SubmitNodeCollector nodes, CameraRenderState camera) {
    poses.pushPose();
    poses.translate(0.0F, 0.375F, 0.0F);
    poses.rotateDegrees(Axis.YP, 180.0F - state.yRot);
    if (state.hurtTime > 0.0F) {
      poses.rotateDegrees(Axis.XP, Mth.sin(state.hurtTime) * state.hurtTime * state.damageTime / 10.0F * state.hurtDir);
    }
    if (!state.isUnderWater && !Mth.equal(state.bubbleAngle, 0.0F)) {
      poses.rotate(new Quaternionf().setAngleAxis(state.bubbleAngle * ((float) Math.PI / 180.0F), 1.0F, 0.0F, 1.0F));
    }
    poses.scale(-1.0F, -1.0F, 1.0F);
    poses.rotateDegrees(Axis.YP, 90.0F);
    nodes.submitModel(this.model, state, poses, state.texture, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
    if (!state.isUnderWater) {
      nodes.submitModel(this.waterPatch, Unit.INSTANCE, poses, state.texture, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
    }
    poses.popPose();
    super.submit(state, poses, nodes, camera);
  }

  protected String getTextureLocation(BoatType type, boolean chest) {
    return "textures/entity/" + (chest ? "chest_boat/" : "boat/") + type.getName() + ".png";
  }

  public static class State extends BoatRenderState {
    public Identifier texture;
  }
}
