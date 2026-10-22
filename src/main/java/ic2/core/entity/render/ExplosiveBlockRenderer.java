package ic2.core.entity.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ic2.api.entity.block.ExplosiveEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.TntMinecartRenderer;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.entity.state.TntRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

public class ExplosiveBlockRenderer extends EntityRenderer<ExplosiveEntity, TntRenderState> {
  private final BlockModelResolver blockModelResolver;

  public ExplosiveBlockRenderer(Context context) {
    super(context);
    this.shadowRadius = 0.5F;
    this.blockModelResolver = context.getBlockModelResolver();
  }

  @Override
  public TntRenderState createRenderState() { return new TntRenderState(); }

  @Override
  public void extractRenderState(ExplosiveEntity entity, TntRenderState state, float partialTick) {
    super.extractRenderState(entity, state, partialTick);
    state.fuseRemainingInTicks = entity.getFuse() - partialTick + 1.0F;
    this.blockModelResolver.update(state.blockState, entity.renderBlockState, TntRenderer.BLOCK_DISPLAY_CONTEXT);
  }

  @Override
  public void submit(TntRenderState state, PoseStack poses, SubmitNodeCollector nodes, CameraRenderState camera) {
    poses.pushPose();
    poses.translate(0.0F, 0.5F, 0.0F);
    if (state.fuseRemainingInTicks < 10.0F) {
      float scale = 1.0F + TntRenderer.getSwellAmount(state.fuseRemainingInTicks);
      poses.scale(scale, scale, scale);
    }
    poses.rotateDegrees(Axis.YP, -90.0F);
    poses.translate(-0.5F, -0.5F, 0.5F);
    poses.rotateDegrees(Axis.YP, 90.0F);
    if (!state.blockState.isEmpty()) {
      TntMinecartRenderer.submitWhiteSolidBlock(state.blockState, poses, nodes, state.lightCoords,
          TntRenderer.isLit(state.fuseRemainingInTicks), state.outlineColor);
    }
    poses.popPose();
    super.submit(state, poses, nodes, camera);
  }
}
