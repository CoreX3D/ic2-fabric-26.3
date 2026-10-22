package ic2.core.item;

import ic2.core.ref.Ic2Items;
import ic2.core.util.StackUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ItemTinCan extends Item {
  public ItemTinCan(Properties settings) {
    super(settings);
  }

  public InteractionResult use(Level world, Player player, InteractionHand hand) {
    ItemStack stack = StackUtil.get(player, hand);
    return !world.isClientSide() && player.getFoodData().needsFood()
        ? this.onEaten(player, stack)
        : InteractionResult.PASS;
  }

  public InteractionResult onEaten(Player player, ItemStack stack) {
    int amount = Math.min(StackUtil.getSize(stack), 20 - player.getFoodData().getFoodLevel());
    if (amount <= 0) {
      return InteractionResult.PASS;
    } else {
      ItemStack emptyStack = new ItemStack(Ic2Items.TIN_CAN, amount);
      if (StackUtil.storeInventoryItem(emptyStack, player, true)) {
        player.getFoodData().eat(amount, amount);
        stack = StackUtil.decSize(stack, amount);
        StackUtil.storeInventoryItem(emptyStack, player, false);
        return InteractionResult.SUCCESS.heldItemTransformedTo(stack);
      } else {
        return InteractionResult.PASS;
      }
    }
  }
}
