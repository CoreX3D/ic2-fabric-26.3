package ic2.core.gametest;

import ic2.core.block.wiring.AbstractCableBlock;
import ic2.core.ref.Ic2Blocks;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public class CableGameTests {
  private static final String EMPTY = "ic2:gametest/empty3x3x3";

  private static final BlockPos WEST_CABLE_POS = new BlockPos(1, 1, 1);
  private static final BlockPos EAST_CABLE_POS = new BlockPos(2, 1, 1);

  // cables connect when placed next to each other and drop the connection again once the neighbor
  // is gone
  @GameTest(structure = EMPTY, maxTicks = 100)
  public void cableConnectionStateFollowsNeighbors(GameTestHelper helper) {
    helper.setBlock(WEST_CABLE_POS, Ic2Blocks.COPPER_CABLE);
    helper.setBlock(EAST_CABLE_POS, Ic2Blocks.COPPER_CABLE);

    helper.assertBlockProperty(WEST_CABLE_POS, AbstractCableBlock.EAST, true);
    helper.assertBlockProperty(EAST_CABLE_POS, AbstractCableBlock.WEST, true);
    helper.assertBlockProperty(WEST_CABLE_POS, AbstractCableBlock.WEST, false);
    helper.assertBlockProperty(WEST_CABLE_POS, AbstractCableBlock.UP, false);

    helper.setBlock(EAST_CABLE_POS, Blocks.AIR);

    helper.assertBlockProperty(WEST_CABLE_POS, AbstractCableBlock.EAST, false);
    helper.succeed();
  }

  // a machine placed and removed next to an existing cable must update the cable's connection via
  // neighborChanged
  @GameTest(structure = EMPTY, maxTicks = 100)
  public void cableDisconnectsFromRemovedMachine(GameTestHelper helper) {
    helper.setBlock(WEST_CABLE_POS, Ic2Blocks.COPPER_CABLE);
    helper.setBlock(EAST_CABLE_POS, Ic2Blocks.MACERATOR);

    helper.assertBlockProperty(WEST_CABLE_POS, AbstractCableBlock.EAST, true);

    helper.setBlock(EAST_CABLE_POS, Blocks.AIR);

    helper.assertBlockProperty(WEST_CABLE_POS, AbstractCableBlock.EAST, false);
    helper.succeed();
  }
}
