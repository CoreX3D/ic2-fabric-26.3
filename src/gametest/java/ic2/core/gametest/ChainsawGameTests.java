package ic2.core.gametest;

import ic2.api.item.ElectricItem;
import ic2.core.item.ElectricItemManager;
import ic2.core.item.tool.ItemElectricToolChainsaw;
import ic2.core.ref.Ic2Items;
import ic2.core.util.StackUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public class ChainsawGameTests {
  private static final String EMPTY = "ic2:gametest/empty3x3x3";

  private static final double MAX_CHARGE = 30000.0;
  private static final double USE_COST = 100.0;

  private static final BlockPos WOOL_POS = new BlockPos(1, 1, 1);

  private static ItemElectricToolChainsaw chainsaw() {
    return (ItemElectricToolChainsaw) Ic2Items.CHAINSAW;
  }

  // by default the chainsaw acts as shears, dropping the shearable block itself
  @GameTest(structure = EMPTY, maxTicks = 100)
  public void chainsawShearsWoolBlock(GameTestHelper helper) {
    ServerPlayer player = helper.makeMockServerPlayerInLevel();
    player.setGameMode(GameType.SURVIVAL);
    ItemStack stack = ElectricItemManager.getCharged(Ic2Items.CHAINSAW, Double.POSITIVE_INFINITY);
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);

    helper.setBlock(WOOL_POS, Blocks.WOOL.white());
    InteractionResult result =
        chainsaw()
            .onBlockStartBreak(
                player,
                helper.getLevel(),
                InteractionHand.MAIN_HAND,
                helper.absolutePos(WOOL_POS),
                Direction.UP);

    helper.assertValueEqual(result, InteractionResult.SUCCESS, "shear result");
    helper.assertValueEqual(
        ElectricItem.manager.getCharge(stack), MAX_CHARGE - USE_COST, "charge after shearing");
    helper.succeedWhen(
        () -> {
          helper.assertBlockPresent(Blocks.AIR, WOOL_POS);
          helper.assertItemEntityPresent(Items.WOOL.white(), WOOL_POS, 2.0);
        });
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void chainsawModeSwitchDisablesShearing(GameTestHelper helper) {
    ServerPlayer player = helper.makeMockServerPlayerInLevel();
    player.setGameMode(GameType.SURVIVAL);
    ItemStack stack = ElectricItemManager.getCharged(Ic2Items.CHAINSAW, Double.POSITIVE_INFINITY);
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);

    Ic2GameTestUtil.pressModeSwitchKey(player);
    try {
      chainsaw().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
    } finally {
      Ic2GameTestUtil.releaseModeSwitchKey(player);
    }

    helper.assertTrue(
        StackUtil.getOrCreateNbtData(stack).getBooleanOr("disableShear", false),
        "mode switch should disable shearing");

    helper.setBlock(WOOL_POS, Blocks.WOOL.white());
    InteractionResult result =
        chainsaw()
            .onBlockStartBreak(
                player,
                helper.getLevel(),
                InteractionHand.MAIN_HAND,
                helper.absolutePos(WOOL_POS),
                Direction.UP);

    helper.assertValueEqual(result, InteractionResult.PASS, "shear result with shearing disabled");
    helper.assertBlockPresent(Blocks.WOOL.white(), WOOL_POS);
    helper.assertValueEqual(
        ElectricItem.manager.getCharge(stack),
        MAX_CHARGE,
        "charge must be untouched with shearing disabled");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void chainsawWithoutChargeCannotShear(GameTestHelper helper) {
    ServerPlayer player = helper.makeMockServerPlayerInLevel();
    player.setGameMode(GameType.SURVIVAL);
    ItemStack stack = new ItemStack(Ic2Items.CHAINSAW);
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);

    helper.setBlock(WOOL_POS, Blocks.WOOL.white());
    InteractionResult result =
        chainsaw()
            .onBlockStartBreak(
                player,
                helper.getLevel(),
                InteractionHand.MAIN_HAND,
                helper.absolutePos(WOOL_POS),
                Direction.UP);

    helper.assertValueEqual(result, InteractionResult.PASS, "shear result without charge");
    helper.assertBlockPresent(Blocks.WOOL.white(), WOOL_POS);
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void chainsawShearsSheep(GameTestHelper helper) {
    ServerPlayer player = helper.makeMockServerPlayerInLevel();
    player.setGameMode(GameType.SURVIVAL);
    ItemStack stack = ElectricItemManager.getCharged(Ic2Items.CHAINSAW, Double.POSITIVE_INFINITY);
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);

    Sheep sheep = helper.spawn(net.minecraft.world.entity.EntityTypes.SHEEP, new BlockPos(1, 1, 1));
    sheep.setColor(DyeColor.WHITE);
    sheep.setSheared(false);

    InteractionResult result =
        chainsaw().interactLivingEntity(stack, player, sheep, InteractionHand.MAIN_HAND);

    helper.assertValueEqual(result, InteractionResult.SUCCESS, "sheep shear result");
    helper.assertTrue(sheep.isSheared(), "sheep should be sheared");
    helper.assertValueEqual(
        ElectricItem.manager.getCharge(stack),
        MAX_CHARGE - USE_COST,
        "charge after shearing a sheep");
    helper.succeedWhen(
        () -> helper.assertItemEntityPresent(Items.WOOL.white(), new BlockPos(1, 1, 1), 2.0));
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void chainsawAttackCostsEnergy(GameTestHelper helper) {
    ServerPlayer player = helper.makeMockServerPlayerInLevel();
    player.setGameMode(GameType.SURVIVAL);
    ItemStack stack = ElectricItemManager.getCharged(Ic2Items.CHAINSAW, Double.POSITIVE_INFINITY);
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);

    Pig pig = helper.spawn(net.minecraft.world.entity.EntityTypes.PIG, new BlockPos(1, 1, 1));
    chainsaw().onAttackEntity(player, pig);

    helper.assertValueEqual(
        ElectricItem.manager.getCharge(stack), MAX_CHARGE - USE_COST, "charge after one attack");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void chainsawMiningCostsEnergy(GameTestHelper helper) {
    ServerPlayer player = helper.makeMockServerPlayerInLevel();
    player.setGameMode(GameType.SURVIVAL);
    ItemStack stack = ElectricItemManager.getCharged(Ic2Items.CHAINSAW, Double.POSITIVE_INFINITY);

    helper.setBlock(WOOL_POS, Blocks.OAK_LOG);
    BlockPos pos = helper.absolutePos(WOOL_POS);
    stack
        .getItem()
        .mineBlock(stack, helper.getLevel(), helper.getLevel().getBlockState(pos), pos, player);

    helper.assertValueEqual(
        ElectricItem.manager.getCharge(stack), MAX_CHARGE - USE_COST, "charge after mining a log");
    helper.succeed();
  }
}
