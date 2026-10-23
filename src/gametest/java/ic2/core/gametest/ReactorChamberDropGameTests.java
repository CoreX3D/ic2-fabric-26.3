package ic2.core.gametest;

import ic2.core.block.reactor.tileentity.TileEntityNuclearReactorElectric;
import ic2.core.block.reactor.tileentity.TileEntityReactorChamberElectric;
import ic2.core.ref.Ic2Blocks;
import ic2.core.ref.Ic2Items;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Counts actual dropped stacks after real block lifecycle and neighbor notifications. */
public class ReactorChamberDropGameTests {
  private static final String EMPTY = "ic2:gametest/empty7x7x7";
  private static final BlockPos CORE = new BlockPos(3, 3, 3);
  private static final BlockPos FIRST_ORPHAN = new BlockPos(3, 2, 3);
  private static final BlockPos SECOND_ORPHAN = FIRST_ORPHAN.east();

  @GameTest(structure = EMPTY, maxTicks = 80)
  public void adjacentUnsupportedChambersReturnExactlyTwoItems(GameTestHelper helper) {
    layFloor(helper);
    ServerPlayer player = helper.makeMockServerPlayerInLevel();
    player.setGameMode(GameType.SURVIVAL);
    BlockPos away = helper.absolutePos(new BlockPos(6, 5, 6));
    player.setPos(Vec3.atCenterOf(away));
    ItemStack chambers = new ItemStack(Ic2Blocks.REACTOR_CHAMBER, 2);
    player.setItemInHand(InteractionHand.MAIN_HAND, chambers);

    placeChamber(helper, player, chambers, FIRST_ORPHAN);
    placeChamber(helper, player, chambers, SECOND_ORPHAN);
    helper.assertTrue(chambers.isEmpty(), "placing two chambers must consume exactly two items");
    // The second placement can already remove its neighbor. Also notify the remaining position
    // through the native Level API, as another adjacent block placement would do in survival.
    notifyNeighbors(helper, FIRST_ORPHAN);
    notifyNeighbors(helper, SECOND_ORPHAN);

    helper.runAtTickTime(
        20,
        () -> {
          helper.assertBlockPresent(Blocks.AIR, FIRST_ORPHAN);
          helper.assertBlockPresent(Blocks.AIR, SECOND_ORPHAN);
          assertDropCount(helper, Ic2Blocks.REACTOR_CHAMBER.asItem(), 2,
              "two unsupported adjacent chambers, including repeated neighbor updates");
          helper.succeed();
        });
  }

  @GameTest(structure = EMPTY, maxTicks = 80)
  public void validChambersSurviveUpdatesAndOneRemovalDoesNotCopyCoreInventory(
      GameTestHelper helper) {
    layFloor(helper);
    TileEntityNuclearReactorElectric reactor = placeFullReactor(helper);
    reactor.reactorSlot.put(0, 0, new ItemStack(Ic2Items.URANIUM_FUEL_ROD));
    reactor.reactorSlot.put(1, 0, new ItemStack(Ic2Items.REACTOR_COOLANT_CELL));

    helper.runAtTickTime(
        5,
        () -> {
          for (Direction direction : Direction.values()) {
            notifyNeighbors(helper, CORE.relative(direction));
            helper.assertBlockPresent(Ic2Blocks.REACTOR_CHAMBER, CORE.relative(direction));
          }
          assertDropCount(helper, Ic2Blocks.REACTOR_CHAMBER.asItem(), 0,
              "valid chambers must not drop on ordinary neighbor notifications");
          helper.getLevel().destroyBlock(helper.absolutePos(CORE.east()), true);
        });

    helper.runAtTickTime(
        25,
        () -> {
          helper.assertBlockPresent(Ic2Blocks.NUCLEAR_REACTOR, CORE);
          helper.assertBlockPresent(Blocks.AIR, CORE.east());
          for (Direction direction : Direction.values()) {
            if (direction != Direction.EAST) {
              helper.assertBlockPresent(Ic2Blocks.REACTOR_CHAMBER, CORE.relative(direction));
            }
          }
          helper.assertValueEqual(reactor.getReactorSize(), 8, "columns after one chamber removal");
          assertDropCount(helper, Ic2Blocks.MACHINE.asItem(), 1,
              "ordinary breaking of one valid chamber must return exactly one machine casing");
          assertDropCount(helper, Ic2Blocks.REACTOR_CHAMBER.asItem(), 0,
              "ordinary breaking must preserve the chamber's native machine-casing loot");
          assertDropCount(helper, Ic2Items.URANIUM_FUEL_ROD, 0,
              "a removed chamber must not copy the core's fuel into the world");
          assertDropCount(helper, Ic2Items.REACTOR_COOLANT_CELL, 0,
              "a removed chamber must not copy the core's components into the world");
          assertSlot(helper, reactor, 0, 0, Ic2Items.URANIUM_FUEL_ROD);
          assertSlot(helper, reactor, 1, 0, Ic2Items.REACTOR_COOLANT_CELL);
          helper.succeed();
        });
  }

  @GameTest(structure = EMPTY, maxTicks = 80)
  public void removingCoreDropsSixChambersAndInventoryExactlyOnce(GameTestHelper helper) {
    layFloor(helper);
    TileEntityNuclearReactorElectric reactor = placeFullReactor(helper);
    reactor.reactorSlot.put(0, 0, new ItemStack(Ic2Items.URANIUM_FUEL_ROD));
    reactor.reactorSlot.put(1, 0, new ItemStack(Ic2Items.REACTOR_COOLANT_CELL));

    helper.runAtTickTime(
        5,
        () -> {
          helper.assertValueEqual(reactor.getReactorSize(), 9, "columns before core removal");
          helper.getLevel().destroyBlock(helper.absolutePos(CORE), true);
          for (Direction direction : Direction.values()) {
            notifyNeighbors(helper, CORE.relative(direction));
          }
        });

    helper.runAtTickTime(
        25,
        () -> {
          helper.assertBlockPresent(Blocks.AIR, CORE);
          for (Direction direction : Direction.values()) {
            helper.assertBlockPresent(Blocks.AIR, CORE.relative(direction));
          }
          assertDropCount(helper, Ic2Blocks.REACTOR_CHAMBER.asItem(), 6,
              "removing the core must return each of its six chambers exactly once");
          assertDropCount(helper, Ic2Items.URANIUM_FUEL_ROD, 1,
              "core fuel must be dropped exactly once");
          assertDropCount(helper, Ic2Items.REACTOR_COOLANT_CELL, 1,
              "core component must be dropped exactly once");
          helper.succeed();
        });
  }

  @GameTest(structure = EMPTY, maxTicks = 80)
  public void destroyingLoadedChamberDoesNotSpillItsReactorsInventory(GameTestHelper helper) {
    layFloor(helper);
    helper.setBlock(CORE, Ic2Blocks.NUCLEAR_REACTOR);
    helper.setBlock(CORE.east(), Ic2Blocks.REACTOR_CHAMBER);
    TileEntityNuclearReactorElectric reactor =
        helper.getBlockEntity(CORE, TileEntityNuclearReactorElectric.class);
    reactor.reactorSlot.put(0, 0, new ItemStack(Ic2Items.URANIUM_FUEL_ROD));
    reactor.reactorSlot.put(1, 0, new ItemStack(Ic2Items.REACTOR_HEAT_VENT));

    helper.runAtTickTime(
        5,
        () -> {
          TileEntityReactorChamberElectric chamber =
              helper.getBlockEntity(CORE.east(), TileEntityReactorChamberElectric.class);
          helper.assertValueEqual(chamber.getContainerSize(), reactor.getContainerSize(),
              "loaded chamber must proxy its live core inventory before removal");
          chamber.destroyChamber(true);
        });

    helper.runAtTickTime(
        25,
        () -> {
          helper.assertBlockPresent(Ic2Blocks.NUCLEAR_REACTOR, CORE);
          helper.assertBlockPresent(Blocks.AIR, CORE.east());
          assertDropCount(helper, Ic2Blocks.REACTOR_CHAMBER.asItem(), 1,
              "destroying a loaded chamber must drop that chamber once");
          assertDropCount(helper, Ic2Items.URANIUM_FUEL_ROD, 0,
              "chamber removal must not spill fuel through its Container proxy");
          assertDropCount(helper, Ic2Items.REACTOR_HEAT_VENT, 0,
              "chamber removal must not spill components through its Container proxy");
          assertSlot(helper, reactor, 0, 0, Ic2Items.URANIUM_FUEL_ROD);
          assertSlot(helper, reactor, 1, 0, Ic2Items.REACTOR_HEAT_VENT);
          helper.succeed();
        });
  }

  @GameTest(structure = EMPTY, maxTicks = 80)
  public void repeatedDestructionOfRemovedChamberCannotDropOrRemoveReplacement(
      GameTestHelper helper) {
    layFloor(helper);
    helper.setBlock(CORE, Ic2Blocks.NUCLEAR_REACTOR);
    helper.setBlock(CORE.east(), Ic2Blocks.REACTOR_CHAMBER);
    TileEntityReactorChamberElectric chamber =
        helper.getBlockEntity(CORE.east(), TileEntityReactorChamberElectric.class);

    chamber.destroyChamber(true);
    helper.setBlock(CORE.east(), Blocks.STONE);
    // This models queued/reentrant callbacks retaining the old block entity after removal.
    chamber.destroyChamber(true);
    chamber.destroyChamber(false);

    helper.runAtTickTime(
        20,
        () -> {
          helper.assertBlockPresent(Blocks.STONE, CORE.east());
          helper.assertBlockPresent(Ic2Blocks.NUCLEAR_REACTOR, CORE);
          assertDropCount(helper, Ic2Blocks.REACTOR_CHAMBER.asItem(), 1,
              "a stale chamber entity must not duplicate its drop");
          helper.succeed();
        });
  }

  private static TileEntityNuclearReactorElectric placeFullReactor(GameTestHelper helper) {
    helper.setBlock(CORE, Ic2Blocks.NUCLEAR_REACTOR);
    for (Direction direction : Direction.values()) {
      helper.setBlock(CORE.relative(direction), Ic2Blocks.REACTOR_CHAMBER);
    }
    return helper.getBlockEntity(CORE, TileEntityNuclearReactorElectric.class);
  }

  private static void layFloor(GameTestHelper helper) {
    for (int x = 0; x < 7; x++) {
      for (int z = 0; z < 7; z++) {
        helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
      }
    }
  }

  private static void placeChamber(
      GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos relativePos) {
    BlockPos absolutePos = helper.absolutePos(relativePos);
    BlockHitResult hit =
        new BlockHitResult(Vec3.atCenterOf(absolutePos), Direction.UP, absolutePos, false);
    InteractionResult result =
        ((BlockItem) stack.getItem())
            .place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
    helper.assertTrue(result.consumesAction(), "placing chamber must succeed at " + relativePos);
  }

  private static void notifyNeighbors(GameTestHelper helper, BlockPos relativePos) {
    helper.getLevel().updateNeighborsAt(helper.absolutePos(relativePos), Blocks.STONE);
    for (Direction direction : Direction.values()) {
      helper.getLevel().updateNeighborsAt(
          helper.absolutePos(relativePos.relative(direction)), Blocks.STONE);
    }
  }

  private static void assertSlot(
      GameTestHelper helper, TileEntityNuclearReactorElectric reactor, int x, int y, Item item) {
    ItemStack stack = reactor.reactorSlot.get(x, y);
    helper.assertTrue(stack != null && stack.is(item) && stack.getCount() == 1,
        "core slot " + x + ", " + y + " must retain exactly one " + item + ", found " + stack);
  }

  private static void assertDropCount(
      GameTestHelper helper, Item item, int expected, String message) {
    BlockPos origin = helper.absolutePos(BlockPos.ZERO);
    AABB bounds = new AABB(origin).expandTowards(7.0, 8.0, 7.0).inflate(1.0);
    int actual = helper.getLevel()
        .getEntitiesOfClass(ItemEntity.class, bounds, entity -> entity.getItem().is(item))
        .stream()
        .mapToInt(entity -> entity.getItem().getCount())
        .sum();
    helper.assertValueEqual(actual, expected, message);
  }
}
