package ic2.core.item.armor.jetpack;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Draws a jetpack attached to another chest item using the 26.3 extraction/submit pipeline. */
@Environment(EnvType.CLIENT)
public final class LayerJetpackOverride
    extends RenderLayer<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
  private static final Identifier TEXTURE =
      Identifier.fromNamespaceAndPath("ic2", "textures/models/armor/ic2_jet_pack_layer_1.png");
  private static final RenderStateDataKey<AttachedJetpack> JETPACK =
      RenderStateDataKey.create(() -> "ic2:attached_jetpack");

  private final JetpackModel jetpackModel = new JetpackModel();

  private LayerJetpackOverride(
      RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> renderer) {
    super(renderer);
  }

  public static void register() {
    LivingEntityRenderLayerRegistrationCallback.EVENT.register(
        (entityType, renderer, helper, context) -> {
          if (renderer.getModel() instanceof HumanoidModel<?>) {
            attach(helper, renderer);
          }
        });
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void attach(
      LivingEntityRenderLayerRegistrationCallback.RegistrationHelper helper,
      LivingEntityRenderer<?, ?, ?> renderer) {
    // Every HumanoidModel consumes a HumanoidRenderState subtype. Keep the renderer/model pairing.
    helper.register(new LayerJetpackOverride((RenderLayerParent) renderer));
  }

  /** Called during entity extraction, before the world is handed over to rendering. */
  public static void extract(LivingEntity entity, LivingEntityRenderState state) {
    ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
    // Vanilla chestEquipment omits equipment without a humanoid armor asset, including elytra.
    // Snapshot the actual slot so those items can carry an attached jetpack too.
    state.setData(JETPACK,
        JetpackHandler.hasJetpackAttached(chest) ? new AttachedJetpack(chest.hasFoil()) : null);
  }

  @Override
  public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
      HumanoidRenderState state, float yRot, float xRot) {
    AttachedJetpack attached = state.getData(JETPACK);
    if (attached == null) {
      return;
    }

    // LivingEntityRenderer has animated its parent model before submitting layers. Capture its
    // poses instead of mutating its visibility: deferred submissions may still need that model.
    HumanoidModel<HumanoidRenderState> parent = getParentModel();
    JetpackPose pose = new JetpackPose(parent.root().storePose(), parent.body.storePose(),
        parent.rightArm.storePose(), parent.leftArm.storePose());
    collector.order(2).submitModel(jetpackModel, pose, poseStack,
        attached.foil() ? RenderTypes.armorCutoutNoCullGlint(TEXTURE)
            : RenderTypes.armorCutoutNoCull(TEXTURE),
        packedLight, OverlayTexture.NO_OVERLAY, state.outlineColor);
  }

  private record AttachedJetpack(boolean foil) {}

  private record JetpackPose(PartPose root, PartPose body, PartPose rightArm, PartPose leftArm) {}

  /** Dedicated chest geometry keeps player sleeves, capes, hats and armor-stand sticks untouched. */
  private static final class JetpackModel extends Model<JetpackPose> {
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;

    private JetpackModel() {
      super(LayerDefinition.create(HumanoidModel.createArmorMeshSet(
          new CubeDeformation(0.5F), new CubeDeformation(1.02F)).chest(), 64, 32).bakeRoot(),
          RenderTypes::armorCutoutNoCull);
      body = root.getChild("body");
      rightArm = root.getChild("right_arm");
      leftArm = root.getChild("left_arm");
    }

    @Override
    public void setupAnim(JetpackPose pose) {
      resetPose();
      root.loadPose(pose.root());
      body.loadPose(pose.body());
      rightArm.loadPose(pose.rightArm());
      leftArm.loadPose(pose.leftArm());
    }
  }
}
