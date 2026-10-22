package ic2.core;

import ic2.api.item.ElectricItem;
import ic2.api.item.HudMode;
import ic2.api.item.IElectricItem;
import ic2.api.item.IItemHudInfo;
import ic2.api.item.IItemHudProvider;
import ic2.core.item.armor.ItemArmorQuantumSuit;
import ic2.core.item.armor.jetpack.IJetpack;
import ic2.core.ref.Ic2Items;
import ic2.core.util.StackUtil;
import ic2.core.util.Util;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class GuiOverlayer {
  private static final Identifier BACKGROUND =
      Identifier.fromNamespaceAndPath("ic2", "textures/gui/guioverlay.png");
  private final Minecraft mc;

  public GuiOverlayer(Minecraft mc) {
    this.mc = mc;
  }

  public void render(GuiGraphicsExtractor guiGraphics) {
    assert this.mc.player != null;
    ItemStack helm = this.mc.player.getItemBySlot(EquipmentSlot.HEAD);
    if (!StackUtil.isEmpty(helm)
        && helm.getItem() instanceof IItemHudProvider
        && ((IItemHudProvider) helm.getItem()).doesProvideHUD(helm)) {
      HudMode hudMode = ((IItemHudProvider) helm.getItem()).getHudMode(helm);
      if (hudMode.shouldDisplay()) {
        ItemStack boots = this.mc.player.getItemBySlot(EquipmentSlot.FEET);
        ItemStack legs = this.mc.player.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack chestplate = this.mc.player.getItemBySlot(EquipmentSlot.CHEST);


        guiGraphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, 0, 0, 71, 69, 256, 256);
        guiGraphics.item(helm, 5, 4);
        guiGraphics.text(this.mc.font, mapCharge(helm) + "%", 25, 9, 0xFFFFFFFF);
        if (StackUtil.getOrCreateNbtData(helm).getBooleanOr("night_vision", false)) {
          guiGraphics.item(Ic2Items.NIGHT_VISION_GOGGLES.getDefaultInstance(), 50, 4);
        }

        if (!StackUtil.isEmpty(chestplate)) {
          int charge = getCharge(chestplate);
          if (charge >= 0) {
            guiGraphics.text(this.mc.font, charge + "%", 25, 25, 0xFFFFFFFF);
            guiGraphics.item(chestplate, 5, 20);

            Item chestItem = chestplate.getItem();
            if (chestItem instanceof IJetpack jetpack && jetpack.isJetpackActive(chestplate)) {
              boolean hoverMode = StackUtil.getOrCreateNbtData(chestplate).getBooleanOr("hover_mode", false);
              ItemStack jetpackIcon =
                  hoverMode
                      ? Ic2Items.JETPACK_ELECTRIC.getDefaultInstance()
                      : Ic2Items.JETPACK.getDefaultInstance();
              guiGraphics.item(jetpackIcon, 50, 20);
            }
          }
        }

        if (!StackUtil.isEmpty(legs)) {
          int charge = getCharge(legs);
          if (charge >= 0) {
            guiGraphics.text(this.mc.font, charge + "%", 25, 41, 0xFFFFFFFF);
            guiGraphics.item(legs, 5, 36);
            if (legs.getItem() instanceof ItemArmorQuantumSuit) {
              CompoundTag legsNbt = StackUtil.getOrCreateNbtData(legs);
              if (!legsNbt.contains("speed_enabled") || legsNbt.getBooleanOr("speed_enabled", false)) {
                guiGraphics.item(Ic2Items.NANO_LEGGINGS.getDefaultInstance(), 50, 36);
              }
            }
          }
        }

        if (!StackUtil.isEmpty(boots)) {
          int charge = getCharge(boots);
          if (charge >= 0) {
            guiGraphics.text(this.mc.font, charge + "%", 25, 56, 0xFFFFFFFF);
            guiGraphics.item(boots, 5, 52);
            if (boots.getItem() instanceof ItemArmorQuantumSuit) {
              CompoundTag bootsNbt = StackUtil.getOrCreateNbtData(boots);
              if (!bootsNbt.contains("jump_enabled") || bootsNbt.getBooleanOr("jump_enabled", false)) {
                guiGraphics.item(Ic2Items.NANO_BOOTS.getDefaultInstance(), 50, 52);
              }
            }
          }
        }

        if (hudMode.hasTooltip()) {
          ItemStack rightItem = this.mc.player.getMainHandItem();
          ItemStack leftItem = this.mc.player.getOffhandItem();
          int nextLine = 83;

          if (!StackUtil.isEmpty(rightItem)) {
            guiGraphics.item(rightItem, 5, 74);
            guiGraphics.text(
                this.mc.font, rightItem.getHoverName().getString(), 30, 78, 0xFFFFFFFF);

            List<String> info = new LinkedList<>();
            if (rightItem.getItem() instanceof IItemHudInfo) {
              info.addAll(
                  ((IItemHudInfo) rightItem.getItem())
                      .getHudInfo(rightItem, hudMode == HudMode.ADVANCED));
              if (!info.isEmpty()) {
                for (int l = 0; l < info.size(); l++) {
                  guiGraphics.text(this.mc.font, info.get(l), 8, 83 + (l + 1) * 14, 0xFFFFFFFF);
                }
              }

              nextLine += (info.size() + 1) * 14;
            } else {
              List<Component> tooltip =
                  rightItem.getTooltipLines(
                      Item.TooltipContext.of(this.mc.level),
                      this.mc.player,
                      hudMode == HudMode.ADVANCED
                          ? TooltipFlag.Default.ADVANCED
                          : TooltipFlag.Default.NORMAL);
              if (tooltip.size() > 1) {
                for (int l = 1; l < tooltip.size(); l++) {
                  guiGraphics.text(
                      this.mc.font, tooltip.get(l).getString(), 8, 83 + l * 14, 0xFFFFFFFF);
                }
              }

              nextLine += tooltip.size() * 14;
            }

            nextLine += 8;
          }

          if (!StackUtil.isEmpty(leftItem)) {
            guiGraphics.item(leftItem, 5, nextLine - 9);
            guiGraphics.text(
                this.mc.font, leftItem.getHoverName().getString(), 30, nextLine - 5, 0xFFFFFFFF);

            List<String> info = new LinkedList<>();
            if (leftItem.getItem() instanceof IItemHudInfo) {
              info.addAll(
                  ((IItemHudInfo) leftItem.getItem())
                      .getHudInfo(leftItem, hudMode == HudMode.ADVANCED));
              if (!info.isEmpty()) {
                for (int l = 0; l < info.size(); l++) {
                  guiGraphics.text(
                      this.mc.font, info.get(l), 8, nextLine + (l + 1) * 14, 0xFFFFFFFF);
                }
              }
            } else {
              List<Component> tooltip =
                  leftItem.getTooltipLines(
                      Item.TooltipContext.of(this.mc.level),
                      this.mc.player,
                      hudMode == HudMode.ADVANCED
                          ? TooltipFlag.Default.ADVANCED
                          : TooltipFlag.Default.NORMAL);
              if (tooltip.size() > 1) {
                for (int l = 1; l < tooltip.size(); l++) {
                  guiGraphics.text(
                      this.mc.font, tooltip.get(l).getString(), 8, nextLine + l * 14, 0xFFFFFFFF);
                }
              }
            }
          }
        }
      }
    }
  }

  private static int getCharge(ItemStack stack) {
    Item item = stack.getItem();
    if (item instanceof IItemHudProvider.IItemHudBarProvider) {
      return ((IItemHudProvider.IItemHudBarProvider) item).getBarPercent(stack);
    } else if (item instanceof IElectricItem) {
      return mapCharge(stack);
    } else {
      return stack.isDamageableItem()
          ? (int) Util.map(1.0 - (double) stack.getDamageValue() / stack.getMaxDamage(), 1.0, 100.0)
          : -1;
    }
  }

  private static int mapCharge(ItemStack stack) {
    double charge = ElectricItem.manager.getCharge(stack);
    double maxCharge =
        charge
            + ElectricItem.manager.charge(
                stack, Double.POSITIVE_INFINITY, Integer.MAX_VALUE, true, true);
    return (int) Util.map(charge, maxCharge, 100.0);
  }
}
