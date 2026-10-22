package ic2.core.item;

import ic2.api.item.ElectricItem;
import ic2.core.util.Ic2Tooltip;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ElectricItemTooltipHandler {
  public static void addTooltip(ItemStack stack, List<Component> out) {
    if (stack != null && ElectricItem.manager.getMaxCharge(stack) > 0.0) {
      String tooltip = ElectricItem.manager.getToolTip(stack);
      if (tooltip != null && !tooltip.trim().isEmpty()) {
        Ic2Tooltip.add(out, Component.literal(tooltip));
        if (ic2.core.IC2.sideProxy.isRendering() && isShiftDown()) {
          Ic2Tooltip.add(
              out,
              Component.translatable(
                  "ic2.item.tooltip.power_tier", ElectricItem.manager.getTier(stack)));
        }
      }
    }
  }

  @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
  private static boolean isShiftDown() {
    return net.minecraft.client.Minecraft.getInstance().hasShiftDown();
  }
}
