package ic2.core.item.armor;

import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItem;
import ic2.api.item.IItemHudInfo;
import ic2.core.IC2;
import ic2.core.item.ElectricItemTooltipHandler;
import ic2.core.ref.Ic2ArmorMaterials;
import ic2.core.util.Ic2Tooltip;
import ic2.core.util.KeyboardClient;
import ic2.core.util.StackUtil;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class ItemArmorNightVisionGoggles extends ItemArmorUtility
    implements IElectricItem, IItemHudInfo , ic2.core.item.Ic2InventoryTickItem {
  public ItemArmorNightVisionGoggles(Properties settings) {
    super(Ic2ArmorMaterials.NIGHT_VISION_GOGGLES.holder(), settings, EquipmentSlot.HEAD);
  }

  @Override
  public boolean canProvideEnergy(ItemStack stack) {
    return false;
  }

  @Override
  public double getMaxCharge(ItemStack stack) {
    return 200000.0;
  }

  @Override
  public int getTier(ItemStack stack) {
    return 1;
  }

  @Override
  public double getTransferLimit(ItemStack stack) {
    return 200.0;
  }

  @Override
  public List<String> getHudInfo(ItemStack stack, boolean advanced) {
    List<String> info = new LinkedList<>();
    info.add(ElectricItem.manager.getToolTip(stack));
    return info;
  }

  public void tickInventory(
      @NotNull ItemStack stack,
      @NotNull Level world,
      @NotNull Entity entity,
      int slot,
      boolean selected) {
    if (entity instanceof Player player) {
      if (player.getItemBySlot(this.getEquipmentSlot()) == stack) {
        CompoundTag nbtData = StackUtil.getOrCreateNbtData(stack);
        boolean active = nbtData.getBooleanOr("active", false);
        byte toggleTimer = nbtData.getByteOr("toggle_timer", (byte) 0);
        if (IC2.keyboard.isAltKeyDown(player)
            && IC2.keyboard.isModeSwitchKeyDown(player)
            && toggleTimer == 0) {
          toggleTimer = 10;
          active = !active;
          if (IC2.sideProxy.isSimulating()) {
            nbtData.putBoolean("active", active);
            ic2.core.item.ItemNbt.commitFields(stack, nbtData, "active");
            if (active) {
              IC2.sideProxy.messagePlayer(player, "ic2.night_vision.mode.enabled");
            } else {
              IC2.sideProxy.messagePlayer(player, "ic2.night_vision.mode.disabled");
            }
          }
        }

        ItemArmorNanoSuit.getNightVisionOrNot(stack, player, nbtData, toggleTimer, active);
      }
    }
  }

  public void appendHoverText(
      @NotNull ItemStack stack,
      Item.TooltipContext world,
      net.minecraft.world.item.component.TooltipDisplay tooltipDisplay,
      java.util.function.Consumer<Component> tooltipOutput,
      @NotNull TooltipFlag context) {
    List<Component> tooltip = new java.util.ArrayList<>();
    try {
    ElectricItemTooltipHandler.addTooltip(stack, tooltip);
    Ic2Tooltip.add(
        tooltip,
        Component.translatable(
            "item.ic2.tooltip.night_vision.toggle",
            KeyboardClient.altKey.getTranslatedKeyMessage(),
            KeyboardClient.modeSwitchKey.getTranslatedKeyMessage()));
  
    } finally {
      tooltip.forEach(tooltipOutput);
    }
  }

  public boolean isValidRepairItem(
      @NotNull ItemStack par1ItemStack, @NotNull ItemStack par2ItemStack) {
    return false;
  }
  @Override
  public void inventoryTick(net.minecraft.world.item.ItemStack stack,
      net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity entity,
      net.minecraft.world.entity.EquipmentSlot equipmentSlot) {
    int inventorySlot = ic2.core.item.Ic2InventoryTickItem.findInventorySlot(entity, stack);
    this.tickInventory(stack, level, entity, inventorySlot,
        equipmentSlot == net.minecraft.world.entity.EquipmentSlot.MAINHAND);
  }


}
