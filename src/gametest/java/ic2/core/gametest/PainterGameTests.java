package ic2.core.gametest;

import ic2.core.item.tool.ItemToolPainter;
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
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

public class PainterGameTests {
  private static final String EMPTY = "ic2:gametest/empty3x3x3";

  private static final BlockPos TARGET_POS = new BlockPos(1, 1, 1);

  private static ServerPlayer makePlayer(GameTestHelper helper) {
    ServerPlayer player = helper.makeMockServerPlayerInLevel();
    player.setGameMode(GameType.SURVIVAL);
    return player;
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void painterDyesWool(GameTestHelper helper) {
    helper.setBlock(TARGET_POS, Blocks.WOOL.white());
    ServerPlayer player = makePlayer(helper);
    ItemStack stack = new ItemStack(Ic2Items.RED_PAINTER);
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);

    InteractionResult result =
        stack.getItem().useOn(Ic2GameTestUtil.useOn(helper, player, TARGET_POS, Direction.UP));

    helper.assertValueEqual(result, InteractionResult.SUCCESS, "painter use result");
    helper.assertBlockPresent(Blocks.WOOL.red(), TARGET_POS);
    helper.assertValueEqual(
        player.getMainHandItem().getDamageValue(), 1, "painter durability used");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void painterDyesGlass(GameTestHelper helper) {
    helper.setBlock(TARGET_POS, Blocks.GLASS);
    ServerPlayer player = makePlayer(helper);
    ItemStack stack = new ItemStack(Ic2Items.BLUE_PAINTER);
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);

    InteractionResult result =
        stack.getItem().useOn(Ic2GameTestUtil.useOn(helper, player, TARGET_POS, Direction.UP));

    helper.assertValueEqual(result, InteractionResult.SUCCESS, "painter use result");
    helper.assertBlockPresent(Blocks.STAINED_GLASS.blue(), TARGET_POS);
    helper.succeed();
  }

  // painting a block that already has the painter's color does nothing
  @GameTest(structure = EMPTY, maxTicks = 100)
  public void painterSameColorDoesNothing(GameTestHelper helper) {
    helper.setBlock(TARGET_POS, Blocks.WOOL.red());
    ServerPlayer player = makePlayer(helper);
    ItemStack stack = new ItemStack(Ic2Items.RED_PAINTER);
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);

    InteractionResult result =
        stack.getItem().useOn(Ic2GameTestUtil.useOn(helper, player, TARGET_POS, Direction.UP));

    helper.assertValueEqual(result, InteractionResult.PASS, "painter use result");
    helper.assertBlockPresent(Blocks.WOOL.red(), TARGET_POS);
    helper.assertValueEqual(
        player.getMainHandItem().getDamageValue(), 0, "painter must not take damage");
    helper.succeed();
  }

  // the blank painter has no color and can't paint anything
  @GameTest(structure = EMPTY, maxTicks = 100)
  public void blankPainterDoesNothing(GameTestHelper helper) {
    helper.setBlock(TARGET_POS, Blocks.WOOL.white());
    ServerPlayer player = makePlayer(helper);
    ItemStack stack = new ItemStack(Ic2Items.PAINTER);
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);

    InteractionResult result =
        stack.getItem().useOn(Ic2GameTestUtil.useOn(helper, player, TARGET_POS, Direction.UP));

    helper.assertValueEqual(result, InteractionResult.PASS, "blank painter use result");
    helper.assertBlockPresent(Blocks.WOOL.white(), TARGET_POS);
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void painterDyesSheep(GameTestHelper helper) {
    ServerPlayer player = makePlayer(helper);
    ItemStack stack = new ItemStack(Ic2Items.RED_PAINTER);
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);

    Sheep sheep = helper.spawn(net.minecraft.world.entity.EntityTypes.SHEEP, TARGET_POS);
    sheep.setColor(DyeColor.WHITE);

    InteractionResult result =
        ((ItemToolPainter) stack.getItem())
            .interactLivingEntity(stack, player, sheep, InteractionHand.MAIN_HAND);

    helper.assertValueEqual(result, InteractionResult.SUCCESS, "painter use result");
    helper.assertValueEqual(sheep.getColor(), DyeColor.RED, "sheep color after painting");
    helper.succeed();
  }

  // a used-up painter reverts to the blank painter
  @GameTest(structure = EMPTY, maxTicks = 100)
  public void depletedPainterTurnsBlank(GameTestHelper helper) {
    helper.setBlock(TARGET_POS, Blocks.WOOL.white());
    ServerPlayer player = makePlayer(helper);
    ItemStack stack = new ItemStack(Ic2Items.RED_PAINTER);
    stack.setDamageValue(stack.getMaxDamage() - 1);
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);

    InteractionResult result =
        stack.getItem().useOn(Ic2GameTestUtil.useOn(helper, player, TARGET_POS, Direction.UP));

    helper.assertValueEqual(result, InteractionResult.SUCCESS, "painter use result");
    helper.assertBlockPresent(Blocks.WOOL.red(), TARGET_POS);
    helper.assertValueEqual(
        player.getMainHandItem().getItem(), Ic2Items.PAINTER, "hand should hold the blank painter");
    helper.succeed();
  }

  // with auto refill on, a used-up painter is replaced by a spare from the inventory
  @GameTest(structure = EMPTY, maxTicks = 100)
  public void depletedPainterAutoRefillsFromInventory(GameTestHelper helper) {
    helper.setBlock(TARGET_POS, Blocks.WOOL.white());
    ServerPlayer player = makePlayer(helper);
    ItemStack stack = new ItemStack(Ic2Items.RED_PAINTER);
    stack.setDamageValue(stack.getMaxDamage() - 1);
    StackUtil.updateNbtData(stack, nbt -> nbt.putBoolean("autoRefill", true));
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    player.getInventory().add(new ItemStack(Ic2Items.RED_PAINTER));

    InteractionResult result =
        stack.getItem().useOn(Ic2GameTestUtil.useOn(helper, player, TARGET_POS, Direction.UP));

    helper.assertValueEqual(result, InteractionResult.SUCCESS, "painter use result");
    helper.assertBlockPresent(Blocks.WOOL.red(), TARGET_POS);
    helper.assertValueEqual(
        player.getMainHandItem().getItem(),
        Ic2Items.RED_PAINTER,
        "hand should hold the spare painter");
    helper.assertTrue(
        player.getInventory().contains(new ItemStack(Ic2Items.PAINTER)),
        "blank painter should be returned to the inventory");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void painterModeSwitchTogglesAutoRefill(GameTestHelper helper) {
    ServerPlayer player = makePlayer(helper);
    ItemStack stack = new ItemStack(Ic2Items.RED_PAINTER);
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);

    Ic2GameTestUtil.pressModeSwitchKey(player);
    try {
      stack.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
      helper.assertTrue(
          StackUtil.getOrCreateNbtData(stack).getBooleanOr("autoRefill", false),
          "auto refill should be enabled");

      stack.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
      helper.assertFalse(
          StackUtil.getOrCreateNbtData(stack).getBooleanOr("autoRefill", false),
          "auto refill should be disabled again");
    } finally {
      Ic2GameTestUtil.releaseModeSwitchKey(player);
    }

    helper.succeed();
  }
}
