package ic2.core.block.misc;

import ic2.core.ref.Ic2Blocks;
import ic2.core.util.WorldUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class RubberWoodBlock extends Block {

  public RubberWoodBlock(Properties settings) {
    super(settings);
  }

  @Override
  public net.minecraft.world.InteractionResult useItemOn(
      ItemStack mainHandItem,
      BlockState state,
      Level world,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (mainHandItem.is(ItemTags.AXES)) {
      WorldUtil.strip(
          state,
          world,
          pos,
          player,
          mainHandItem,
          Ic2Blocks.STRIPPED_RUBBER_WOOD.defaultBlockState());
      return net.minecraft.world.InteractionResult.SUCCESS;
    } else {
      return super.useItemOn(mainHandItem, state, world, pos, player, hand, hit);
    }
  }
}
