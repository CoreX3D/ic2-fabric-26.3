package ic2.mixin;

import ic2.api.item.IPlayerDropHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Prediction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drives {@link IPlayerDropHandler#onDroppedByPlayer(ItemStack, Player)}.
 *
 * <p>26.3 moved item entity creation from Player into {@code LivingEntity#createItemStackToDrop}.
 * Both ordinary and scattered player drops pass through this method. Restrict the callback to
 * players and preserve the null result used to cancel item entity creation.
 */
@Mixin(LivingEntity.class)
public abstract class PlayerMixin {
  @Inject(
      method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",
      at = @At("HEAD"), cancellable = true)
  private void ic2$onClientDroppedByPlayer(
      ItemStack stack, boolean traceItem, Prediction prediction,
      CallbackInfoReturnable<ItemEntity> callback) {
    // Vanilla returns from drop on the client before creating the item entity. Electric tools
    // still need their client drop callback to stop sounds when they leave the inventory.
    if ((Object) this instanceof Player player && player.level().isClientSide()
        && !stack.isEmpty() && stack.getItem() instanceof IPlayerDropHandler handler
        && !handler.onDroppedByPlayer(stack, player)) {
      callback.setReturnValue(null);
    }
  }

  @Inject(
      method = "createItemStackToDrop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
      at = @At("HEAD"),
      cancellable = true)
  private void ic2$onDroppedByPlayer(
      ItemStack stack,
      boolean dropAround,
      boolean traceItem,
      CallbackInfoReturnable<ItemEntity> callback) {
    if (stack.isEmpty() || !((Object) this instanceof Player player)) {
      return;
    }

    if (stack.getItem() instanceof IPlayerDropHandler handler
        && !handler.onDroppedByPlayer(stack, player)) {
      callback.setReturnValue(null);
    }
  }
}
