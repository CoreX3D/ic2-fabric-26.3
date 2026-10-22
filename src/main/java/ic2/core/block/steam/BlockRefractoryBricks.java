package ic2.core.block.steam;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;

public class BlockRefractoryBricks extends Block {
  public BlockRefractoryBricks(Properties properties) {
    super(properties.strength(2.0F, 10.0F).requiresCorrectToolForDrops().sound(SoundType.STONE));
  }
}
