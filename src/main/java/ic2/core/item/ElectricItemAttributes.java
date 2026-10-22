package ic2.core.item;

import ic2.api.item.ElectricItem;
import ic2.core.item.armor.ItemArmorElectric;
import ic2.core.item.armor.ItemArmorNanoSuit;
import ic2.core.item.armor.ItemArmorQuantumSuit;
import ic2.core.item.tool.AbstractItemNanoSaber;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Per-stack attribute modifiers for IC2's electric equipment.
 *
 * <p>NeoForge provided this through {@code ItemAttributeModifierEvent}, which was fired while a
 * stack's modifiers were being collected and could remove and re-add entries. Fabric has no such
 * callback — and 1.21 stores attribute modifiers in the {@code minecraft:attribute_modifiers}
 * component, which is baked from {@code Item#getDefaultAttributeModifiers()} and therefore cannot
 * vary per stack — so {@code ic2.mixin.ItemStackMixin} calls into this class from
 * {@code ItemStack#forEachModifier(EquipmentSlot, BiConsumer)}.
 *
 * <p>The nano saber's damage and speed follow its charge and activation state, and charged electric
 * armour replaces the item's base armour value.
 */
public final class ElectricItemAttributes {
  private static final Identifier NANO_SABER_DAMAGE =
      Identifier.fromNamespaceAndPath("ic2", "nano_saber_damage");
  private static final Identifier NANO_SABER_SPEED =
      Identifier.fromNamespaceAndPath("ic2", "nano_saber_speed");
  private static final Identifier LEGACY_CHARGED_ARMOR =
      Identifier.fromNamespaceAndPath("ic2", "charged_armor");
  private static final Map<EquipmentSlot, Identifier> CHARGED_ARMOR = chargedArmorIds();

  private ElectricItemAttributes() {}

  /**
   * Group counterpart of {@link #apply(ItemStack, EquipmentSlot, BiConsumer)}.
   *
   * <p>The native group overload compares exact groups, whereas the equipment overload tests whether
   * an entry includes that slot. A helmet consequently belongs to HEAD only, not to additional ANY
   * and ARMOR tooltip sections.
   */
  public static boolean apply(
      ItemStack stack,
      EquipmentSlotGroup group,
      BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
    EquipmentSlot slot = dynamicSlot(stack);
    if (slot == null || group != EquipmentSlotGroup.bySlot(slot)) {
      return false;
    }

    stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
        .forEach(group, withoutReplacedAttributes(stack.getItem(), consumer));
    emitDynamicModifiers(stack, slot, consumer);
    EnchantmentHelper.forEachModifier(stack, group, consumer);
    return true;
  }

  /**
   * Emits the modifiers for {@code stack} in {@code slot}.
   *
   * @return {@code true} when this class supplied the modifiers and the vanilla component list must
   *     be skipped, {@code false} to let vanilla emission proceed unchanged
   */
  public static boolean apply(
      ItemStack stack,
      EquipmentSlot slot,
      BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
    if (slot != dynamicSlot(stack)) {
      return false;
    }

    stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
        .forEach(slot, withoutReplacedAttributes(stack.getItem(), consumer));
    emitDynamicModifiers(stack, slot, consumer);
    EnchantmentHelper.forEachModifier(stack, slot, consumer);
    return true;
  }

  private static EquipmentSlot dynamicSlot(ItemStack stack) {
    Item item = stack.getItem();
    if (item instanceof AbstractItemNanoSaber) {
      return EquipmentSlot.MAINHAND;
    }

    if ((item instanceof ItemArmorNanoSuit || item instanceof ItemArmorQuantumSuit)
        && item instanceof ItemArmorElectric electric
        && ElectricItem.manager.getCharge(stack) >= electric.getEnergyPerDamage()) {
      return electric.getEquipmentSlot();
    }

    return null;
  }

  private static void emitDynamicModifiers(
      ItemStack stack,
      EquipmentSlot slot,
      BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
    Item item = stack.getItem();
    if (item instanceof AbstractItemNanoSaber) {
      int damage = 4;
      float speed = -3.0F;
      if (ElectricItem.manager.canUse(stack, 400.0) && AbstractItemNanoSaber.isActive(stack)) {
        damage = 20;
        speed = 0.0F;
      }

      // Mirrors the NeoForge handler, which removed all existing attack modifiers first.
      consumer.accept(
          Attributes.ATTACK_DAMAGE,
          new AttributeModifier(NANO_SABER_DAMAGE, damage, AttributeModifier.Operation.ADD_VALUE));
      consumer.accept(
          Attributes.ATTACK_SPEED,
          new AttributeModifier(NANO_SABER_SPEED, speed, AttributeModifier.Operation.ADD_VALUE));
      return;
    }

    if (item instanceof ItemArmorElectric electric) {
      int[] protection =
          item instanceof ItemArmorNanoSuit
              ? ItemArmorNanoSuit.CHARGED_PROTECTION
              : item instanceof ItemArmorQuantumSuit ? ItemArmorQuantumSuit.CHARGED_PROTECTION : null;
      consumer.accept(
          Attributes.ARMOR,
          new AttributeModifier(
              CHARGED_ARMOR.get(slot), protection[slot.getIndex()], AttributeModifier.Operation.ADD_VALUE));
    }
  }

  private static BiConsumer<Holder<Attribute>, AttributeModifier> withoutReplacedAttributes(
      Item item, BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
    return (attribute, modifier) -> {
      boolean replaced = item instanceof AbstractItemNanoSaber
          ? attribute == Attributes.ATTACK_DAMAGE || attribute == Attributes.ATTACK_SPEED
          : attribute == Attributes.ARMOR;
      if (!replaced) consumer.accept(attribute, modifier);
    };
  }

  private static Map<EquipmentSlot, Identifier> chargedArmorIds() {
    Map<EquipmentSlot, Identifier> ids = new EnumMap<>(EquipmentSlot.class);
    for (EquipmentSlot slot : EquipmentSlot.values()) {
      if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
        ids.put(slot, Identifier.fromNamespaceAndPath("ic2", "charged_armor/" + slot.getName()));
      }
    }
    return Map.copyOf(ids);
  }

  /**
   * Recomputes ordinary armour without the IC2 values already handled by EU absorption.
   *
   * <p>Subtracting a numeric total would mishandle multiplied attributes and their native bounds.
   * A detached instance preserves the base, every other modifier and vanilla rounding without
   * changing the entity or its synced attributes. The legacy ID also covers an equipped stack
   * whose previous modifier has not yet been refreshed.
   */
  public static float getArmorWithoutElectricProtection(LivingEntity entity, float nativeArmor) {
    AttributeInstance armor = entity.getAttribute(Attributes.ARMOR);
    if (armor == null || armor.getModifiers().stream()
        .noneMatch(modifier -> isChargedArmorModifier(modifier.id()))) {
      return nativeArmor;
    }

    AttributeInstance ordinaryArmor = new AttributeInstance(armor.getAttribute(), ignored -> {});
    ordinaryArmor.setBaseValue(armor.getBaseValue());
    for (AttributeModifier modifier : armor.getModifiers()) {
      if (!isChargedArmorModifier(modifier.id())) ordinaryArmor.addTransientModifier(modifier);
    }
    return Mth.floor(ordinaryArmor.getValue());
  }

  private static boolean isChargedArmorModifier(Identifier id) {
    return LEGACY_CHARGED_ARMOR.equals(id) || CHARGED_ARMOR.containsValue(id);
  }
}
