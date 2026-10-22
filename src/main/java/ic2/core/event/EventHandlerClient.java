package ic2.core.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import ic2.api.item.ElectricItem;
import ic2.api.item.IEnhancedOverlayProvider;
import ic2.core.GuiOverlayer;
import ic2.core.IC2;
import ic2.core.fluid.FluidHandler;
import ic2.core.item.IHandHeldInventory;
import ic2.core.item.armor.jetpack.JetpackLogic;
import ic2.core.item.tool.AbstractItemNanoSaber;
import ic2.core.item.tool.ContainerToolbox;
import ic2.core.item.upgrade.ItemUpgradeModule;
import ic2.core.network.RpcHandler;
import ic2.core.proxy.SideProxyClient;
import ic2.core.sound.SoundManagerClient;
import ic2.core.util.StackUtil;
import ic2.core.util.RotationUtil;
import ic2.core.util.Util;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

public class EventHandlerClient {
  public static void onClientSetup() {
    List<Block> fluidBlocks = new ArrayList<>();
    for (Block block : BuiltInRegistries.BLOCK.stream().toList()) {
      Identifier id = BuiltInRegistries.BLOCK.getKey(block);
      if (id != null
          && "ic2".equals(id.getNamespace())
          && id.getPath().startsWith("fluid_block_")) {
        fluidBlocks.add(block);
      }
    }

    if (!fluidBlocks.isEmpty()) {
      SideProxyClient.envProxy.registerBlockLayer(
          ChunkSectionLayer.TRANSLUCENT, fluidBlocks.toArray(Block[]::new));
    }

    SideProxyClient.envProxy.registerModelPredicateProvider(
        IC2.getIdentifier("charge"),
        (stack, world, entity, seed) -> (float) ElectricItem.manager.getChargeLevel(stack));

    for (Direction direction : Util.ALL_DIRS) {
      SideProxyClient.envProxy.registerModelPredicateProvider(
          IC2.getIdentifier(direction.getName()),
          (stack, world, entity, seed) ->
              stack.getItem() instanceof ItemUpgradeModule
                      && ItemUpgradeModule.getDirection(stack) == direction
                  ? 1.0F
                  : 0.0F);
    }

    SideProxyClient.envProxy.registerModelPredicateProvider(
        IC2.getIdentifier("is_activated"),
        (stack, world, entity, seed) ->
            stack.getItem() instanceof AbstractItemNanoSaber nanoSaber
                ? nanoSaber.getActiveData(stack, world)
                : 0f);
    SideProxyClient.envProxy.registerModelPredicateProvider(
        IC2.getIdentifier("tool_box_open"),
        (stack, world, entity, seed) -> {
          if (!(entity instanceof Player player)) {
            return 0.0F;
          } else {
            ItemStack mainHandItem;
            boolean open =
                player.containerMenu instanceof ContainerToolbox
                    && (StackUtil.checkItemEquality(mainHandItem = player.getMainHandItem(), stack)
                        || StackUtil.checkItemEquality(player.getOffhandItem(), stack)
                            && !(mainHandItem.getItem() instanceof IHandHeldInventory));
            return open ? 1.0F : 0.0F;
          }
        });
  }

  public static void onSoundSetup() {}

  public static void livingEntityPreRender(
      LivingEntity entity,
      LivingEntityRenderer<?, ?, ?> renderer) {}

  public static void livingEntityPostRender(
      LivingEntity entity,
      LivingEntityRenderer<?, ?, ?> renderer) {}

  public static float onSetupFogDensity(BlockState state) {
    Fluid fluid = FluidHandler.getWorldFluid(state);
    if (fluid != null
        && "ic2"
            .equals(Objects.requireNonNull(BuiltInRegistries.FLUID.getKey(fluid)).getNamespace())) {
      int density = FluidHandler.getDensity(fluid);
      return (float) Util.map(Math.abs(density), 20000.0, 2.0);
    } else {
      return -1.0F;
    }
  }

  public static int onRenderFogColor(BlockState state) {
    Fluid fluid = FluidHandler.getWorldFluid(state);
    return fluid != null && "ic2".equals(BuiltInRegistries.FLUID.getKey(fluid).getNamespace())
        ? FluidHandler.getColor(fluid)
        : -1;
  }

  private static final RenderStateDataKey<BlockOverlay> BLOCK_OVERLAY =
      RenderStateDataKey.create(() -> "ic2:enhanced_block_overlay");

  public static void registerBlockHighlight() {
    LevelExtractionEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register(
        EventHandlerClient::extractBlockHighlight);
    LevelRenderEvents.COLLECT_SUBMITS.register(EventHandlerClient::submitBlockHighlight);
  }

  private static void extractBlockHighlight(LevelExtractionContext context, HitResult hit) {
    // Render states are reused. Clear the previous target even when the player looks into air.
    context.levelState().setData(BLOCK_OVERLAY, null);
    Player player = SideProxyClient.mc.player;
    if (player == null || context.levelState().blockOutlineRenderState == null
        || !(hit instanceof BlockHitResult target) || hit.getType() != Type.BLOCK) {
      return;
    }
    Level world = context.level();
    BlockPos pos = target.getBlockPos();
    ItemStack inHand = StackUtil.get(player, InteractionHand.MAIN_HAND);
    if (world.getWorldBorder().isWithinBounds(pos)
        && inHand.getItem() instanceof IEnhancedOverlayProvider provider
        && provider.providesEnhancedOverlay(world, pos, target.getDirection(), player, inHand)) {
      context.levelState().setData(BLOCK_OVERLAY,
          new BlockOverlay(pos.immutable(), target.getDirection(), RotationUtil.rotateByRay(target)));
    }
  }

  private static void submitBlockHighlight(LevelRenderContext context) {
    BlockOverlay overlay = context.levelState().getData(BLOCK_OVERLAY);
    if (overlay == null) {
      return;
    }
    Vec3 camera = context.levelState().cameraRenderState.pos;
    PoseStack poseStack = context.poseStack();
    poseStack.pushPose();
    poseStack.translate(overlay.pos().getX() - camera.x,
        overlay.pos().getY() - camera.y, overlay.pos().getZ() - camera.z);
    context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.linesDepthBias(),
        (pose, vertices) -> drawBlockOverlay(overlay, pose, vertices));
    poseStack.popPose();
  }

  /** The 1/4-face grid matches RotationUtil: center = front, edges = adjacent, corners = back. */
  private static void drawBlockOverlay(BlockOverlay overlay, PoseStack.Pose pose,
      VertexConsumer vertices) {
    Direction face = overlay.face();
    drawFaceRectangle(face, pose, vertices, 0, 0, 1, 1, 0xB3000000, 2.0F);
    for (float split : new float[] {0.25F, 0.75F}) {
      drawFaceLine(face, pose, vertices, split, 0, split, 1, 0xB3000000, 2.0F);
      drawFaceLine(face, pose, vertices, 0, split, 1, split, 0xB3000000, 2.0F);
    }

    float[] divisions = {0, 0.25F, 0.75F, 1};
    for (int u = 0; u < 3; u++) {
      for (int v = 0; v < 3; v++) {
        Vec3 center = facePoint(face, (divisions[u] + divisions[u + 1]) / 2,
            (divisions[v] + divisions[v + 1]) / 2);
        Direction selected = RotationUtil.rotateByHit(face,
            (float) center.x, (float) center.y, (float) center.z);
        if (selected == overlay.selected()) {
          drawFaceRectangle(face, pose, vertices, divisions[u], divisions[v],
              divisions[u + 1], divisions[v + 1], 0xFFFFB52E, 3.0F);
        }
      }
    }
  }

  private static void drawFaceRectangle(Direction face, PoseStack.Pose pose,
      VertexConsumer vertices, float u0, float v0, float u1, float v1, int color, float width) {
    drawFaceLine(face, pose, vertices, u0, v0, u1, v0, color, width);
    drawFaceLine(face, pose, vertices, u1, v0, u1, v1, color, width);
    drawFaceLine(face, pose, vertices, u1, v1, u0, v1, color, width);
    drawFaceLine(face, pose, vertices, u0, v1, u0, v0, color, width);
  }

  private static void drawFaceLine(Direction face, PoseStack.Pose pose, VertexConsumer vertices,
      float u0, float v0, float u1, float v1, int color, float width) {
    Vec3 from = facePoint(face, u0, v0);
    Vec3 to = facePoint(face, u1, v1);
    Vec3 normal = to.subtract(from).normalize();
    vertices.addVertex(pose, (float) from.x, (float) from.y, (float) from.z).setColor(color)
        .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z).setLineWidth(width);
    vertices.addVertex(pose, (float) to.x, (float) to.y, (float) to.z).setColor(color)
        .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z).setLineWidth(width);
  }

  private static Vec3 facePoint(Direction face, float u, float v) {
    // Slightly outside the selected face to avoid z-fighting with its texture.
    return switch (face) {
      case DOWN -> new Vec3(u, -0.002, v);
      case UP -> new Vec3(u, 1.002, v);
      case NORTH -> new Vec3(u, v, -0.002);
      case SOUTH -> new Vec3(u, v, 1.002);
      case WEST -> new Vec3(-0.002, v, u);
      case EAST -> new Vec3(1.002, v, u);
    };
  }

  private record BlockOverlay(BlockPos pos, Direction face, Direction selected) {}

  public static void onGuiCreate(
      Screen screen, List<GuiEventListener> widgets, Consumer<GuiEventListener> widgetAdder) {}

  private static final GuiOverlayer guiOverlayer = new GuiOverlayer(SideProxyClient.mc);

  public static void onRenderHotBar(GuiGraphicsExtractor guiGraphics) {
    guiOverlayer.render(guiGraphics);
  }

  public static SoundInstance onSoundPlayed(SoundInstance sound) {
    return SoundManagerClient.onSoundPlayed(sound);
  }

  public static void onDisconnect() {
    RpcHandler.onDisconnect();
    // Ensure looping jetpack audio cannot survive into the next multiplayer session.
    JetpackLogic.stopJetpackSound(null);
  }

  public static void onClientPlayerJoin(Player player) {
    if (!player.level().isClientSide()) {
      return;
    }

    // Reset client-global jetpack audio when entering a new client level/session.
    JetpackLogic.stopJetpackSound(null);
  }
}
