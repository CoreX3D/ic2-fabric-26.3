package ic2.core.item.armor;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.Item.Properties;

public class ItemArmorUtility extends Item {
  private final EquipmentSlot equipmentSlot;

  public EquipmentSlot getEquipmentSlot() {
    return this.equipmentSlot;
  }

  public ItemArmorUtility(Holder<ArmorMaterial> material, Properties settings, EquipmentSlot slot) {
    super(ItemArmorIC2.armorProperties(settings, material.value(), fromSlot(slot)));
    this.equipmentSlot = slot;
  }

  private static ArmorType fromSlot(EquipmentSlot slot) {
    return switch (slot) {
      case HEAD -> ArmorType.HELMET;
      case CHEST -> ArmorType.CHESTPLATE;
      case LEGS -> ArmorType.LEGGINGS;
      case FEET -> ArmorType.BOOTS;
      default -> throw new IllegalArgumentException("Invalid slot: " + slot);
    };
  }
}
