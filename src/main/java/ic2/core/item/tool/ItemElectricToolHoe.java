package ic2.core.item.tool;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class ItemElectricToolHoe extends ItemElectricTool {
  private static final float MINING_SPEED = 16.0F;

  public ItemElectricToolHoe(Properties settings) {
    super(settings, 50, ToolMaterial.IRON, List.of(BlockTags.MINEABLE_WITH_HOE));
    this.maxCharge = 10000;
    this.transferLimit = 100;
    this.tier = 1;
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    ItemStack stack = context.getItemInHand();
    if (!this.canUse(stack) || context.getClickedFace() == Direction.DOWN) {
      return InteractionResult.PASS;
    }

    // The 26.3 vanilla hoe rules are data driven. Reuse them with durability cost replaced by EU.
    var rules = Items.IRON_HOE.components().get(DataComponents.BLOCK_TRANSFORMER);
    if (rules == null) return InteractionResult.PASS;
    BlockTransformer electricTransformer = new BlockTransformer(rules.value().transforms().stream()
        .map(rule -> new BlockTransformer.BlockTransformData(
            rule.blockStateProvider(), rule.sound(), rule.particle(), rule.disallowedFaces(),
            rule.loot(), rule.dropStrategy(), rule.updateFromNeighbors(), rule.transformType(),
            false, 0))
        .toList());
    InteractionResult result = electricTransformer.transformBlock(context);
    if (result.consumesAction() && !context.getLevel().isClientSide()) {
      this.consumeEnergy(stack, this.operationEnergyCost, context.getPlayer());
    }
    return result;
  }

  @Override
  public float getDestroySpeed(ItemStack stack, BlockState state) {
    float speed = super.getDestroySpeed(stack, state);
    return speed == 1.0F ? speed : speed * MINING_SPEED / ToolMaterial.IRON.speed();
  }
}
