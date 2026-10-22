package ic2.core.item.armor;

import ic2.api.item.IMetalArmor;
import ic2.core.ref.Ic2ArmorMaterials;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.ItemStack;

public class ItemArmorIC2 extends Item implements IMetalArmor {
  private final EquipmentSlot equipmentSlot;

  public EquipmentSlot getEquipmentSlot() {
    return this.equipmentSlot;
  }

  public ItemArmorIC2(Holder<ArmorMaterial> material, EquipmentSlot slot, Properties settings) {
    super(armorProperties(settings, material.value(), fromSlot(slot)));
    this.equipmentSlot = slot;
  }

  public ItemArmorIC2(Ic2ArmorMaterials material, EquipmentSlot slot, Properties settings) {
    this(material.holder(), slot, settings);
  }

  static Properties armorProperties(Properties settings, ArmorMaterial material, ArmorType type) {
    settings.stacksTo(1).attributes(material.createAttributes(type))
        .component(net.minecraft.core.component.DataComponents.EQUIPPABLE,
            net.minecraft.world.item.equipment.Equippable.builder(type.getSlot())
                .setEquipSound(material.equipSound()).setAsset(material.assetId()).build());
    if (material.durability() > 0) settings.durability(type.getDurability(material.durability()));
    if (material.enchantmentValue() > 0) settings.enchantable(material.enchantmentValue());
    return settings.repairable(material.repairIngredient());
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

  @Override
  public boolean isMetalArmor(ItemStack itemstack, Player player) {
    return true;
  }
}
