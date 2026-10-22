package ic2.core.item.tool;

import ic2.core.IC2;
import ic2.core.block.machine.tileentity.TileEntityTeleporter;
import ic2.core.util.Ic2Tooltip;
import ic2.core.util.StackUtil;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

public class ItemFrequencyTransmitter extends Item {
  public ItemFrequencyTransmitter(Properties settings) {
    super(settings);
  }

  public @NotNull InteractionResult use(
      @NotNull Level world, @NotNull Player player, @NotNull InteractionHand hand) {
    ItemStack stack = StackUtil.get(player, hand);
    if (IC2.sideProxy.isSimulating()) {
      CompoundTag nbtData = StackUtil.getOrCreateNbtData(stack);
      boolean hadJustSet = nbtData.getBooleanOr("targetJustSet", false);
      if (nbtData.getBooleanOr("targetSet", false) && !hadJustSet) {
        nbtData.putBoolean("targetSet", false);
        StackUtil.setTag(stack, nbtData);
        IC2.sideProxy.messagePlayer(player, "ic2.frequency_transmitter.unlink_target");
      }

      if (hadJustSet) {
        nbtData.putBoolean("targetJustSet", false);
        StackUtil.setTag(stack, nbtData);
      }
    }

    return InteractionResult.SUCCESS.heldItemTransformedTo(stack);
  }

  public @NotNull InteractionResult useOn(UseOnContext context) {
    Level world = context.getLevel();
    Player player = context.getPlayer();
    BlockPos pos = context.getClickedPos();
    InteractionHand hand = context.getHand();
    if (player == null) {
      return InteractionResult.PASS;
    } else if (world.isClientSide()) {
      return InteractionResult.PASS;
    } else if (!(world.getBlockEntity(pos) instanceof TileEntityTeleporter tp)) {
      return InteractionResult.PASS;
    } else {
      CompoundTag nbtData = StackUtil.getOrCreateNbtData(StackUtil.get(player, hand));
      boolean targetSet = nbtData.getBooleanOr("targetSet", false);
      boolean justSetTarget = true;
      BlockPos target =
          new BlockPos(
              nbtData.getIntOr("targetX", 0), nbtData.getIntOr("targetY", 0), nbtData.getIntOr("targetZ", 0));
      if (!targetSet) {
        targetSet = true;
        target = tp.getBlockPos();
        IC2.sideProxy.messagePlayer(
            player,
            "ic2.frequency_transmitter.link_target",
            target.getX(),
            target.getY(),
            target.getZ());
      } else if (tp.getBlockPos().equals(target)) {
        IC2.sideProxy.messagePlayer(player, "ic2.frequency_transmitter.cannot_link_to_self");
      } else if (tp.hasTarget() && tp.getTarget().equals(target)) {
        IC2.sideProxy.messagePlayer(player, "ic2.frequency_transmitter.link_already_established");
      } else {
        BlockEntity targetTe = world.getBlockEntity(target);
        if (targetTe instanceof TileEntityTeleporter) {
          tp.setTarget(target);
          ((TileEntityTeleporter) targetTe).setTarget(pos);
          IC2.sideProxy.messagePlayer(
              player,
              "ic2.frequency_transmitter.link_target",
              target.getX(),
              target.getY(),
              target.getZ());
        } else {
          justSetTarget = false;
          targetSet = false;
        }
      }

      nbtData.putBoolean("targetSet", targetSet);
      nbtData.putBoolean("targetJustSet", justSetTarget);
      nbtData.putInt("targetX", target.getX());
      nbtData.putInt("targetY", target.getY());
      nbtData.putInt("targetZ", target.getZ());
      StackUtil.setTag(StackUtil.get(player, hand), nbtData);
      return InteractionResult.SUCCESS;
    }
  }

  public void appendHoverText(
      @NotNull ItemStack stack,
      Item.TooltipContext world,
      net.minecraft.world.item.component.TooltipDisplay tooltipDisplay,
      java.util.function.Consumer<Component> tooltipOutput,
      @NotNull TooltipFlag advanced) {
    List<Component> tooltip = new java.util.ArrayList<>();
    try {
    CompoundTag nbtData = StackUtil.getOrCreateNbtData(stack);
    if (nbtData.getBooleanOr("targetSet", false)) {
      Ic2Tooltip.add(
          tooltip,
          Component.translatable(
              "ic2.frequency_transmitter.tooltip.target",
              nbtData.getIntOr("targetX", 0),
              nbtData.getIntOr("targetY", 0),
              nbtData.getIntOr("targetZ", 0)));
    } else {
      Ic2Tooltip.add(tooltip, Component.translatable("ic2.frequency_transmitter.tooltip.blank"));
    }
  
    } finally {
      tooltip.forEach(tooltipOutput);
    }
  }
}
