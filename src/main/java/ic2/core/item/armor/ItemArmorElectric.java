package ic2.core.item.armor;

import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItem;
import ic2.core.item.ElectricItemManager;
import ic2.core.item.ElectricItemTooltipHandler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public abstract class ItemArmorElectric extends ItemArmorIC2 implements IElectricItem {
  public static final UUID[] MODIFIERS =
      new UUID[] {
        UUID.fromString("845DB27C-C624-495F-8C9F-6020A9A58B6B"),
        UUID.fromString("D8499B04-0E66-4726-AB29-64469D734E0D"),
        UUID.fromString("9F3D476D-C118-4544-8365-64846904B48E"),
        UUID.fromString("2AD3F246-FEE1-4E67-B886-69FD380BB150")
      };
  protected final double maxCharge;
  protected final double transferLimit;
  protected final int tier;

  public ItemArmorElectric(
      Holder<ArmorMaterial> material,
      EquipmentSlot slot,
      Properties settings,
      double maxCharge,
      double transferLimit,
      int tier) {
    super(material, slot, settings);
    this.maxCharge = maxCharge;
    this.transferLimit = transferLimit;
    this.tier = tier;
  }

  public static float damageArmor(Player entity, DamageSource source, float amount) {
    // Void (/out_of_world) and /kill use BYPASSES_INVULNERABILITY; do not spend EU on them.
    if (source == null
        || amount <= 0.0F
        || !Float.isFinite(amount)
        || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
      return amount;
    }

    List<ArmorProtection> protection = new ArrayList<>();
    for (EquipmentSlot slot : EquipmentSlot.values()) {
      if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
        continue;
      }

      ItemStack stack = entity.getItemBySlot(slot);
      if (stack.getItem() instanceof ItemArmorElectric electricArmor) {
        double ratio = electricArmor.getBaseAbsorptionRatio() * electricArmor.getDamageAbsorptionRatio();
        int priority = electricArmor instanceof ItemArmorQuantumSuit ? 8 : 0;
        if (source.is(DamageTypeTags.IS_FALL)) {
          if (slot == EquipmentSlot.FEET && electricArmor instanceof ItemArmorNanoSuit) {
            ratio = amount < 8.0F ? 1.0 : 0.875;
            priority = 10;
          } else if (slot == EquipmentSlot.FEET && electricArmor instanceof ItemArmorQuantumSuit) {
            ratio = 1.0;
            priority = 10;
          } else if (slot == EquipmentSlot.LEGS && electricArmor instanceof ItemArmorQuantumSuit) {
            ratio = 0.8;
            priority = 9;
          }
        }
        int energyPerDamage = electricArmor.getEnergyPerDamage();
        double charge = ElectricItem.manager.getCharge(stack);
        if (ratio > 0.0 && energyPerDamage > 0 && charge > 0.0) {
          protection.add(new ArmorProtection(electricArmor, stack, slot, priority, ratio,
              charge / energyPerDamage));
        }
      }
    }

    protection.sort(Comparator.comparingInt(ArmorProtection::priority).reversed());
    double remainingDamage = amount;
    for (int start = 0; start < protection.size() && remainingDamage > 0.0;) {
      int end = start + 1;
      while (end < protection.size()
          && protection.get(end).priority() == protection.get(start).priority()) end++;
      remainingDamage -= absorbGroup(entity, source, remainingDamage, protection.subList(start, end));
      remainingDamage = Math.max(0.0, remainingDamage);
      start = end;
    }
    // A normalized full quantum set must not leave a floating-point sliver of accepted damage.
    return remainingDamage <= Math.ulp(amount) ? 0.0F : (float) remainingDamage;
  }

  private static double absorbGroup(Player player, DamageSource source, double incoming,
      List<ArmorProtection> pieces) {
    boolean[] capped = new boolean[pieces.size()];
    double ratioLeft = pieces.stream().mapToDouble(ArmorProtection::ratio).sum();
    double cappedDamage = 0.0;
    double scale;
    // Contributions at one priority add against the same incoming amount. Normalize totals above
    // 100%, redistributing only within each part's original ratio and remaining charge capacity.
    while (true) {
      scale = ratioLeft > 0.0
          ? Math.max(0.0, Math.min(1.0, (incoming - cappedDamage) / (incoming * ratioLeft))) : 0.0;
      boolean changed = false;
      for (int i = 0; i < pieces.size(); i++) {
        ArmorProtection piece = pieces.get(i);
        if (!capped[i] && incoming * piece.ratio() * scale > piece.maxDamage()) {
          capped[i] = true;
          cappedDamage += piece.maxDamage();
          ratioLeft -= piece.ratio();
          changed = true;
        }
      }
      if (!changed) break;
    }
    double absorbed = 0.0;
    for (int i = 0; i < pieces.size(); i++) {
      ArmorProtection piece = pieces.get(i);
      double damage = capped[i] ? piece.maxDamage() : incoming * piece.ratio() * scale;
      if (damage > 0.0) {
        piece.item().damageArmor(player, piece.stack(), source, damage, piece.slot());
        absorbed += damage;
      }
    }
    return Math.min(incoming, absorbed);
  }

  private record ArmorProtection(ItemArmorElectric item, ItemStack stack, EquipmentSlot slot,
      int priority, double ratio, double maxDamage) {}

  public abstract int getEnergyPerDamage();

  public abstract double getDamageAbsorptionRatio();

  protected double getBaseAbsorptionRatio() {
    return switch (this.getEquipmentSlot()) {
      case HEAD -> 0.15;
      case CHEST -> 0.4;
      case LEGS -> 0.3;
      case FEET -> 0.15;
      default -> 0.0;
    };
  }

  public boolean isEnchantable(ItemStack stack) {
    return false;
  }

  public void fillItemCategory(CreativeModeTab tab, NonNullList<ItemStack> subItems) {
    ElectricItemManager.addChargeVariants(this, subItems);
  }

  public void appendHoverText(
      ItemStack stack, Item.TooltipContext world, net.minecraft.world.item.component.TooltipDisplay tooltipDisplay,
      java.util.function.Consumer<Component> tooltipOutput, TooltipFlag context) {
    List<Component> tooltip = new java.util.ArrayList<>();
    try {
    ElectricItemTooltipHandler.addTooltip(stack, tooltip);
  
    } finally {
      tooltip.forEach(tooltipOutput);
    }
  }

  public void damageArmor(
      LivingEntity entity,
      ItemStack stack,
      DamageSource source,
      double damage,
      EquipmentSlot slot) {
    ElectricItem.manager.discharge(
        stack, damage * this.getEnergyPerDamage(), Integer.MAX_VALUE, true, false, false);
  }

  @Override
  public boolean canProvideEnergy(ItemStack stack) {
    return false;
  }

  @Override
  public double getMaxCharge(ItemStack stack) {
    return this.maxCharge;
  }

  @Override
  public int getTier(ItemStack stack) {
    return this.tier;
  }

  @Override
  public double getTransferLimit(ItemStack stack) {
    return this.transferLimit;
  }

  public boolean isBarVisible(ItemStack stack) {
    return ElectricItem.manager.getChargeLevel(stack) < 1.0;
  }

  public int getBarWidth(ItemStack stack) {
    return (int) Math.round(ElectricItem.manager.getChargeLevel(stack) * 13.0);
  }

  public int getBarColor(ItemStack stack) {
    return Mth.hsvToRgb((float) (ElectricItem.manager.getChargeLevel(stack) / 3.0), 1.0F, 1.0F);
  }

  // ElectricItemAttributes supplies the charged-armor protection through ItemStackMixin.
}
