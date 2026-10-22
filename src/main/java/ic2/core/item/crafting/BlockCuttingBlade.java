package ic2.core.item.crafting;

import ic2.api.item.IBlockCuttingBlade;
import ic2.core.item.type.BlockCuttingBladeType;
import ic2.core.util.Ic2Tooltip;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

public class BlockCuttingBlade extends Item implements IBlockCuttingBlade {
  private final BlockCuttingBladeType type;

  public BlockCuttingBlade(Properties settings, BlockCuttingBladeType type) {
    super(settings);
    this.type = type;
  }

  @Override
  public int getHardness(ItemStack stack) {
    return switch (this.type) {
      case iron -> 3;
      case steel -> 6;
      case diamond -> 9;
    };
  }

  public void appendHoverText(
      @NotNull ItemStack stack,
      Item.TooltipContext world,
      net.minecraft.world.item.component.TooltipDisplay tooltipDisplay,
      java.util.function.Consumer<Component> tooltipOutput,
      @NotNull TooltipFlag advanced) {
    List<Component> tooltip = new java.util.ArrayList<>();
    try {
    Ic2Tooltip.add(
        tooltip,
        Component.translatable(
            switch (type) {
              case iron -> "ic2.iron_cutting_blade.info";
              case steel -> "ic2.steel_cutting_blade.info";
              case diamond -> "ic2.diamond_cutting_blade.info";
            }));
    Ic2Tooltip.add(
        tooltip, Component.translatable("ic2.cutting_blade.hardness", this.getHardness(stack)));
  
    } finally {
      tooltip.forEach(tooltipOutput);
    }
  }
}
