package ic2.core.item.armor;

import ic2.api.item.ElectricItem;
import ic2.api.item.HudMode;
import ic2.api.item.IItemHudProvider;
import ic2.core.IC2;
import ic2.core.util.Ic2Tooltip;
import ic2.core.util.KeyboardClient;
import ic2.core.util.StackUtil;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class ItemArmorNanoSuit extends ItemArmorElectric implements IItemHudProvider , ic2.core.item.Ic2InventoryTickItem {
  public static final int[] CHARGED_PROTECTION = new int[] {3, 6, 8, 3};

  public ItemArmorNanoSuit(
      Holder<ArmorMaterial> material, EquipmentSlot slot, Properties settings) {
    super(material, slot, settings.rarity(Rarity.UNCOMMON), 1000000.0, 1600.0, 3);
  }

  static void getNightVisionOrNot(
      @NotNull ItemStack stack,
      Player player,
      CompoundTag nbtData,
      byte toggleTimer,
      boolean isNightVisionEnabled) {
    if (IC2.sideProxy.isSimulating() && toggleTimer > 0) {
      nbtData.putByte("toggle_timer", --toggleTimer);
      ic2.core.item.ItemNbt.commitFields(stack, nbtData, "toggle_timer");
    }

    if (isNightVisionEnabled
        && IC2.sideProxy.isSimulating()
        && ElectricItem.manager.use(stack, 1.0, player)) {
      int skylight =
          player
              .level()
              .getMaxLocalRawBrightness(BlockPos.containing(player.position()));
      affectPlayer(player, skylight);
    }
  }

  static void affectPlayer(Player player, int skylight) {
    if (skylight > 8) {
      player.removeEffect(MobEffects.NIGHT_VISION);
      player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, true, true));
    } else {
      player.removeEffect(MobEffects.BLINDNESS);
      player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, true));
    }
  }

  @Override
  public int getEnergyPerDamage() {
    return 5000;
  }

  @Override
  public double getDamageAbsorptionRatio() {
    return 0.9;
  }

  public boolean absorbFall(ItemStack stack, float distance) {
    int fallDamage = Math.max((int) distance - 3, 0);
    if (fallDamage >= 8) {
      return false;
    }

    double energyCost = this.getEnergyPerDamage() * fallDamage;
    if (energyCost > ElectricItem.manager.getCharge(stack)) {
      return false;
    }

    ElectricItem.manager.discharge(stack, energyCost, Integer.MAX_VALUE, true, false, false);
    return true;
  }

  public void tickInventory(
      @NotNull ItemStack stack,
      @NotNull Level world,
      @NotNull Entity entity,
      int slot,
      boolean selected) {
    if (entity instanceof Player player) {
      CompoundTag nbtData = StackUtil.getOrCreateNbtData(stack);
      byte toggleTimer = nbtData.getByteOr("toggle_timer", (byte) 0);
      boolean ret = false;
      if (player.getItemBySlot(EquipmentSlot.HEAD) == stack) {
        boolean isNightVisionEnabled = nbtData.getBooleanOr("night_vision", false);
        short hubmode = nbtData.getShortOr("hud_mode", (short) 0);
        if (IC2.keyboard.isAltKeyDown(player)
            && IC2.keyboard.isModeSwitchKeyDown(player)
            && toggleTimer == 0) {
          toggleTimer = 10;
          isNightVisionEnabled = !isNightVisionEnabled;
          if (IC2.sideProxy.isSimulating()) {
            nbtData.putBoolean("night_vision", isNightVisionEnabled);
            ic2.core.item.ItemNbt.commitFields(stack, nbtData, "night_vision");
            if (isNightVisionEnabled) {
              IC2.sideProxy.messagePlayer(player, "ic2.night_vision.mode.enabled");
            } else {
              IC2.sideProxy.messagePlayer(player, "ic2.night_vision.mode.disabled");
            }
          }
        }

        if (IC2.keyboard.isAltKeyDown(player)
            && IC2.keyboard.isHudModeKeyDown(player)
            && toggleTimer == 0) {
          toggleTimer = 10;
          if (hubmode == HudMode.getMaxMode()) {
            hubmode = 0;
          } else {
            hubmode++;
          }

          if (IC2.sideProxy.isSimulating()) {
            nbtData.putShort("hud_mode", hubmode);
            ic2.core.item.ItemNbt.commitFields(stack, nbtData, "hud_mode");
            IC2.sideProxy.messagePlayer(
                player,
                HudMode.getFromID(hubmode).getTranslationKey());
          }
        }

        getNightVisionOrNot(stack, player, nbtData, toggleTimer, isNightVisionEnabled);
      }
    }
  }

  @Override
  @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
  public void appendHoverText(
      ItemStack stack, Item.TooltipContext world, net.minecraft.world.item.component.TooltipDisplay tooltipDisplay,
      java.util.function.Consumer<Component> tooltipOutput, TooltipFlag context) {
    List<Component> tooltip = new java.util.ArrayList<>();
    try {
    super.appendHoverText(stack, world, tooltipDisplay, tooltip::add, context);
    if (this.getEquipmentSlot() == EquipmentSlot.HEAD) {
      Ic2Tooltip.add(
          tooltip,
          Component.translatable(
              "item.ic2.tooltip.night_vision.toggle",
              KeyboardClient.altKey.getTranslatedKeyMessage(),
              KeyboardClient.modeSwitchKey.getTranslatedKeyMessage()));
    }
  
    } finally {
      tooltip.forEach(tooltipOutput);
    }
  }

  public @NotNull Rarity getRarity(@NotNull ItemStack stack) {
    return Rarity.UNCOMMON;
  }

  @Override
  public boolean doesProvideHUD(ItemStack stack) {
    return this.getEquipmentSlot() == EquipmentSlot.HEAD
        && ElectricItem.manager.getCharge(stack) > 0.0;
  }

  @Override
  public HudMode getHudMode(ItemStack stack) {
    return HudMode.getFromID(StackUtil.getOrCreateNbtData(stack).getShortOr("hud_mode", (short) 0));
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
