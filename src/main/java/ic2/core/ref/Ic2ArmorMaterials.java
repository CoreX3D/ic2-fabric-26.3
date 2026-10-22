package ic2.core.ref;

import com.google.common.base.Suppliers;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.ArmorMaterial;

public enum Ic2ArmorMaterials {
  BRONZE(
      "ic2:ic2_bronze",
      15,
      new int[] {2, 5, 6, 2},
      9,
      0.0F),
  ALLOY("ic2:ic2_alloy", 50, new int[] {4, 7, 9, 4}, 12, 2.0F),
  NANO_SUIT("ic2:ic2_nano", 0, new int[] {0, 0, 0, 0}, 0, 2.0F),
  QUANTUM_SUIT("ic2:ic2_quantum", 0, new int[] {0, 0, 0, 0}, 0, 2.0F),
  NIGHT_VISION_GOGGLES("ic2:ic2_night_vision", 0, new int[] {0, 0, 0, 3}, 0, 2.0F),
  // Legacy special armor absorbed 5% normal damage per piece; one point is the closest vanilla fit.
  HAZMAT("ic2:ic2_hazmat", SoundEvents.ARMOR_EQUIP_LEATHER, new int[] {1, 1, 1, 1}, 0.0F),
  CF_PACK("ic2:ic2_cf_pack", SoundEvents.ARMOR_EQUIP_IRON),
  JET_PACK("ic2:ic2_jet_pack", SoundEvents.ARMOR_EQUIP_IRON, new int[] {0, 0, 8, 0}, 2.0F),
  JET_PACK_ELECTRIC(
      "ic2:ic2_jet_pack_electric", SoundEvents.ARMOR_EQUIP_IRON, new int[] {0, 0, 8, 0}, 2.0F),
  BAT_PACK("ic2:ic2_bat_pack", SoundEvents.ARMOR_EQUIP_IRON, new int[] {0, 0, 8, 0}, 2.0F),
  ADVANCED_BAT_PACK(
      "ic2:ic2_advanced_bat_pack", SoundEvents.ARMOR_EQUIP_IRON, new int[] {0, 0, 8, 0}, 2.0F),
  ENERGY_PACK("ic2:ic2_energy_pack", SoundEvents.ARMOR_EQUIP_IRON, new int[] {0, 0, 8, 0}, 2.0F),
  LAP_PACK("ic2:ic2_lap_pack", SoundEvents.ARMOR_EQUIP_IRON, new int[] {0, 0, 8, 0}, 2.0F);

  private static final int[] BASE_DURABILITY = new int[] {13, 15, 16, 11};
  private final String name;
  private final int durabilityMultiplier;
  private final int[] protectionAmounts;
  private final int enchantAbility;
  private final Holder<SoundEvent> equipSound;
  private final float toughness;
  private final float knockbackResistance;
  private final Supplier<Holder<ArmorMaterial>> holder;

  Ic2ArmorMaterials(
      String name,
      int durabilityMultiplier,
      int[] protectionAmounts,
      int enchantAbility,
      float toughness) {
    this.name = name;
    this.durabilityMultiplier = durabilityMultiplier;
    this.protectionAmounts = protectionAmounts;
    this.enchantAbility = enchantAbility;
    this.equipSound = SoundEvents.ARMOR_EQUIP_IRON;
    this.toughness = toughness;
    this.knockbackResistance = 0.0F;
    this.holder = Suppliers.memoize(this::build);
  }

  Ic2ArmorMaterials(String name, Holder<SoundEvent> equipSound) {
    this(name, equipSound, new int[] {0, 0, 0, 0}, 0.0F);
  }

  Ic2ArmorMaterials(
      String name, Holder<SoundEvent> equipSound, int[] protectionAmounts, float toughness) {
    this.name = name;
    this.durabilityMultiplier = 0;
    this.enchantAbility = 0;
    this.equipSound = equipSound;
    this.knockbackResistance = 0.0F;
    this.protectionAmounts = protectionAmounts;
    this.toughness = toughness;
    this.holder = Suppliers.memoize(this::build);
  }

  private Holder<ArmorMaterial> build() {
    Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
    for (ArmorType type : ArmorType.values()) {
      int index = type.getSlot().getIndex();
      if (index >= 0 && index < this.protectionAmounts.length) {
        defense.put(type, this.protectionAmounts[index]);
      }
    }

    return Holder.direct(new ArmorMaterial(
        this.durabilityMultiplier, defense, this.enchantAbility, this.equipSound,
        this.toughness, this.knockbackResistance,
        net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
            Identifier.fromNamespaceAndPath("ic2", "repairs_" + name().toLowerCase(java.util.Locale.ROOT) + "_armor")),
        net.minecraft.resources.ResourceKey.create(
            net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, Identifier.parse(this.name))));
  }

  public Holder<ArmorMaterial> holder() {
    return this.holder.get();
  }

  public int getDurabilityForType(ArmorType type) {
    return BASE_DURABILITY[type.getSlot().getIndex()] * this.durabilityMultiplier;
  }

  public String getName() {
    return this.name;
  }
}
