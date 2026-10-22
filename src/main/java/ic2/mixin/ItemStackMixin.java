package ic2.mixin;

import ic2.core.item.ElectricItemAttributes;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.apache.commons.lang3.function.TriConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets IC2's electric equipment vary its attribute modifiers per stack.
 *
 * <p>NeoForge exposed {@code ItemAttributeModifierEvent}; Fabric has no equivalent, and 1.21 bakes
 * the {@code minecraft:attribute_modifiers} component from {@code
 * Item#getDefaultAttributeModifiers()} so it cannot depend on the stack. Injecting at the head of
 * {@code ItemStack#forEachModifier} replaces the emission for IC2's own items, which is the same
 * point NeoForge's event ran from.
 *
 * <p>Both overloads must be covered. In 26.3 the group overload also emits the tooltip display rule,
 * while the single-slot overload emits the modifiers used by equipped items. IC2's dynamic values
 * use the standard attribute display rule.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
  @Inject(
      method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void ic2$electricItemModifiers(
      EquipmentSlot slot,
      BiConsumer<Holder<Attribute>, AttributeModifier> consumer,
      CallbackInfo callback) {
    if (ElectricItemAttributes.apply((ItemStack) (Object) this, slot, consumer)) {
      callback.cancel();
    }
  }

  @Inject(
      method = "forEachModifier(Lnet/minecraft/world/entity/EquipmentSlotGroup;Lorg/apache/commons/lang3/function/TriConsumer;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void ic2$electricItemModifiersForGroup(
      EquipmentSlotGroup group,
      TriConsumer<Holder<Attribute>, AttributeModifier, ItemAttributeModifiers.Display> consumer,
      CallbackInfo callback) {
    if (ElectricItemAttributes.apply(
        (ItemStack) (Object) this,
        group,
        (attribute, modifier) ->
            consumer.accept(attribute, modifier, ItemAttributeModifiers.Display.attributeModifiers()))) {
      callback.cancel();
    }
  }
}
