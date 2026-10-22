package ic2.mixin;

import ic2.core.recipe.RecipeBookStackRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep valid IC ingredients available to both recipe-book accounting and server placement. */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
  @Inject(method = "isUsableForCrafting", at = @At("HEAD"), cancellable = true)
  private static void ic2$allowIcCraftingIngredients(ItemStack stack,
      CallbackInfoReturnable<Boolean> callback) {
    if (!RecipeBookStackRules.canAccountStack(stack)) {
      callback.setReturnValue(false);
      return;
    }
    // IC recipes accept used IC tools, armor and rotors; goggles also encode charge as damage.
    // Move the original stack so recipe matching, charge transfer and remainders stay authoritative.
    // Keep vanilla's named/enchantment protections and its treatment of all non-IC equipment.
    if (!stack.isEmpty() && stack.isDamageableItem()
        && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("ic2")
        && stack.getDamageValue() < stack.getMaxDamage()
        && !stack.isEnchanted() && !stack.has(DataComponents.CUSTOM_NAME)) {
      callback.setReturnValue(true);
    }
  }
}
