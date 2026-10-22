package ic2.core.gametest;

import ic2.core.ref.Ic2Fluids;
import ic2.core.fluid.FluidHandler;
import ic2.fabric.DensityDrivenFluid;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public class FluidPlacementGameTests {
  private static final String TEMPLATE = "ic2:gametest/empty3x9x3";
  private static final BlockPos SOURCE_POS = new BlockPos(1, 3, 1);

  // HydrogenBlock overrides onPlace/neighborChanged; if the LiquidBlock supers are skipped, a
  // placed source never gets a fluid tick scheduled and never spreads (upstream 8bee6d7b).
  @GameTest(structure = TEMPLATE, maxTicks = 60)
  public void placedHydrogenSourceSpreads(GameTestHelper helper) {
    assertPlacedSourceSpreads(helper, Ic2Fluids.HYDROGEN.still());
  }

  // HotWaterBlock.onPlace only scheduled its cool-down block tick, never the fluid tick
  @GameTest(structure = TEMPLATE, maxTicks = 60)
  public void placedHotWaterSourceSpreads(GameTestHelper helper) {
    assertPlacedSourceSpreads(helper, Ic2Fluids.HOT_WATER.still());
  }

  // Original Forge viscosity 250000 gives a 1250-tick flow delay, after basalt formation at 360.
  @GameTest(structure = TEMPLATE, maxTicks = 400)
  public void pahoehoeSolidifiesBeforeItsSlowFlowTick(GameTestHelper helper) {
    buildVerticalTube(helper);
    helper.setBlock(SOURCE_POS, Ic2Fluids.PAHOEHOE_LAVA.still().defaultFluidState().createLegacyBlock());
    helper.runAfterDelay(370, () -> {
      helper.assertBlockPresent(Blocks.BASALT, SOURCE_POS);
      helper.assertBlockPresent(Blocks.AIR, SOURCE_POS.above());
      helper.assertBlockPresent(Blocks.AIR, SOURCE_POS.below());
      helper.succeed();
    });
  }

  @GameTest(structure = TEMPLATE, maxTicks = 80)
  public void negativeDensityGasRisesWithoutDuplicatingSources(GameTestHelper helper) {
    assertVerticalFlow(helper, Ic2Fluids.AIR.still(), true);
  }

  @GameTest(structure = TEMPLATE, maxTicks = 80)
  public void zeroDensityGasRisesWithoutDuplicatingSources(GameTestHelper helper) {
    assertVerticalFlow(helper, Ic2Fluids.OXYGEN.still(), true);
  }

  // A downward-only slope search chooses west. Check the first scheduled spread, before the
  // newly placed flowing cell can tick and fill any other part of the chamber.
  @GameTest(structure = TEMPLATE, maxTicks = 20)
  public void zeroDensityGasChoosesUpwardEscapeBeforeDownwardHole(GameTestHelper helper) {
    for (int x = 0; x < 3; x++) {
      for (int y = 0; y < 9; y++) {
        for (int z = 0; z < 3; z++) {
          helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
      }
    }
    BlockPos east = SOURCE_POS.east();
    BlockPos west = SOURCE_POS.west();
    helper.setBlock(east, Blocks.AIR);
    helper.setBlock(east.above(), Blocks.AIR);
    helper.setBlock(west, Blocks.AIR);
    helper.setBlock(west.below(), Blocks.AIR);
    Fluid oxygen = Ic2Fluids.OXYGEN.still();
    helper.setBlock(SOURCE_POS, oxygen.defaultFluidState().createLegacyBlock());
    helper.runAfterDelay(3, () -> {
      var level = helper.getLevel();
      FluidState source = level.getFluidState(helper.absolutePos(SOURCE_POS));
      FluidState eastFlow = level.getFluidState(helper.absolutePos(east));
      helper.assertTrue(source.getType().isSame(oxygen) && source.isSource(),
          "gas slope search must preserve its source");
      helper.assertTrue(eastFlow.getType().isSame(oxygen) && !eastFlow.isSource(),
          "the first gas spread must select the horizontal route with an upward escape");
      helper.assertTrue(level.getFluidState(helper.absolutePos(west)).isEmpty(),
          "a downward hole must not attract the first gas spread");
      helper.assertTrue(level.getFluidState(helper.absolutePos(east.above())).isEmpty(),
          "the assertion must run before the new flowing cell receives its next scheduled tick");
      helper.succeed();
    });
  }

  @GameTest(structure = TEMPLATE, maxTicks = 80)
  public void positiveDensityLiquidFallsWithoutDuplicatingSources(GameTestHelper helper) {
    assertVerticalFlow(helper, Ic2Fluids.COOLANT.still(), false);
  }

  @GameTest(structure = TEMPLATE, maxTicks = 80)
  public void equalDensityCoolantPreservesWaterSource(GameTestHelper helper) {
    assertDensityDisplacement(helper, Ic2Fluids.COOLANT.still(),
        Fluids.WATER.defaultFluidState(), false, 45);
  }

  @GameTest(structure = TEMPLATE, maxTicks = 80)
  public void risingGasPreservesWaterSource(GameTestHelper helper) {
    assertDensityDisplacement(helper, Ic2Fluids.OXYGEN.still(),
        Fluids.WATER.defaultFluidState(), false, 45);
  }

  @GameTest(structure = TEMPLATE, maxTicks = 40)
  public void coolantPreservesLowFlowingLava(GameTestHelper helper) {
    // Vanilla permits replacing lava below 4/9 height. Check after coolant's 15-tick flow,
    // before the isolated lava cell's own 30-tick update would naturally make it recede.
    assertDensityDisplacement(helper, Ic2Fluids.COOLANT.still(),
        Fluids.FLOWING_LAVA.getFlowing(1, false), false, 20);
  }

  @GameTest(structure = TEMPLATE, maxTicks = 300)
  public void denserCreosoteDisplacesWater(GameTestHelper helper) {
    assertDensityDisplacement(helper, Ic2Fluids.CREOSOTE.still(),
        Fluids.WATER.defaultFluidState(), true, 270);
  }

  @GameTest(structure = TEMPLATE, maxTicks = 300)
  public void denserCreosoteDisplacesLava(GameTestHelper helper) {
    assertDensityDisplacement(helper, Ic2Fluids.CREOSOTE.still(),
        Fluids.LAVA.defaultFluidState(), true, 270);
  }

  private static void assertDensityDisplacement(
      GameTestHelper helper, Fluid incoming, FluidState existing, boolean replaces, int delay) {
    buildVerticalTube(helper);
    boolean upward = DensityDrivenFluid.isUpward(incoming);
    BlockPos target = upward ? SOURCE_POS.above() : SOURCE_POS.below();
    helper.setBlock(upward ? target.above() : target.below(), Blocks.STONE);
    helper.setBlock(upward ? SOURCE_POS.below() : SOURCE_POS.above(), Blocks.STONE);
    helper.setBlock(target, existing.createLegacyBlock());
    helper.setBlock(SOURCE_POS, incoming.defaultFluidState().createLegacyBlock());
    helper.runAfterDelay(delay, () -> {
      var level = helper.getLevel();
      FluidState actual = level.getFluidState(helper.absolutePos(target));
      FluidState source = level.getFluidState(helper.absolutePos(SOURCE_POS));
      helper.assertTrue(source.isSource() && source.getType().isSame(incoming),
          "density displacement must preserve the incoming source");
      if (replaces) {
        helper.assertTrue(actual.getType().isSame(incoming) && !actual.isSource(),
            "a denser IC2 fluid must displace vanilla liquid without duplicating its source");
      } else {
        helper.assertTrue(actual.getType().isSame(existing.getType())
            && actual.isSource() == existing.isSource() && actual.getAmount() == existing.getAmount(),
            "a lighter or equal-density IC2 fluid must preserve the vanilla liquid");
      }
      helper.succeed();
    });
  }

  private static void buildVerticalTube(GameTestHelper helper) {
    for (int x = 0; x < 3; x++) {
      for (int y = 0; y < 9; y++) {
        for (int z = 0; z < 3; z++) {
          helper.setBlock(new BlockPos(x, y, z),
              x == 1 && z == 1 && y > 0 && y < 8 ? Blocks.AIR : Blocks.STONE);
        }
      }
    }
  }

  private static void assertVerticalFlow(GameTestHelper helper, Fluid fluid, boolean upward) {
    buildVerticalTube(helper);
    helper.setBlock(SOURCE_POS, fluid.defaultFluidState().createLegacyBlock());
    helper.runAfterDelay(45, () -> {
      var level = helper.getLevel();
      BlockPos sourcePos = helper.absolutePos(SOURCE_POS);
      BlockPos targetPos = upward ? sourcePos.above() : sourcePos.below();
      BlockPos oppositePos = upward ? sourcePos.below() : sourcePos.above();
      FluidState source = level.getFluidState(sourcePos);
      FluidState target = level.getFluidState(targetPos);
      helper.assertTrue(source.isSource() && source.getType().isSame(fluid),
          "flow must preserve the original source");
      helper.assertTrue(target.getType().isSame(fluid) && !target.isSource(),
          "fluid must flow " + (upward ? "upward" : "downward") + " without creating another source");
      helper.assertTrue(level.getFluidState(oppositePos).isEmpty(),
          "fluid must not flow against its density direction");
      for (int y = 1; y < 8; y++) {
        if (y == SOURCE_POS.getY()) continue;
        helper.assertTrue(!level.getFluidState(helper.absolutePos(new BlockPos(1, y, 1))).isSource(),
            "flowing cells must never duplicate a source");
      }
      var simulated = FluidHandler.drainWorldFluid(level.getBlockState(sourcePos), level, sourcePos, true);
      helper.assertTrue(simulated != null && simulated.getAmountMb() == 1000
          && level.getFluidState(sourcePos).isSource(), "simulated drain must leave the source intact");
      var drained = FluidHandler.drainWorldFluid(level.getBlockState(sourcePos), level, sourcePos, false);
      helper.assertTrue(drained != null && drained.getAmountMb() == 1000
          && level.getFluidState(sourcePos).isEmpty(), "real drain must produce exactly one bucket");
      var twice = FluidHandler.drainWorldFluid(level.getBlockState(sourcePos), level, sourcePos, false);
      helper.assertTrue(twice == null || twice.isEmpty(), "drained source cannot be drained twice");
      helper.succeed();
    });
  }

  // UUMatterBlock.neighborChanged now calls the LiquidBlock super; its custom interaction
  // (adjacent lava source -> obsidian) must keep working, and the placed source must still flow
  @GameTest(structure = TEMPLATE, maxTicks = 60)
  public void uuMatterKeepsLavaInteractionAndSpreads(GameTestHelper helper) {
    buildBasinFloor(helper);
    helper.setBlock(
        SOURCE_POS, Ic2Fluids.UU_MATTER.still().defaultFluidState().createLegacyBlock());
    helper.setBlock(SOURCE_POS.west(), Blocks.LAVA);

    helper.succeedWhen(
        () -> {
          helper.assertBlockPresent(Blocks.OBSIDIAN, SOURCE_POS.west());
          FluidState east = helper.getLevel().getFluidState(helper.absolutePos(SOURCE_POS.east()));
          helper.assertTrue(
              east.getType().isSame(Ic2Fluids.UU_MATTER.still()),
              "the UU matter source must spread away from the converted lava");
        });
  }

  private static void buildBasinFloor(GameTestHelper helper) {
    for (int x = 0; x <= 2; x++) {
      for (int z = 0; z <= 2; z++) {
        helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
      }
    }
  }

  private static void assertPlacedSourceSpreads(GameTestHelper helper, Fluid fluid) {
    if (DensityDrivenFluid.isUpward(fluid)) {
      for (int x = 0; x < 3; x++) {
        for (int z = 0; z < 3; z++) helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
      }
    } else {
      buildBasinFloor(helper);
    }
    helper.setBlock(SOURCE_POS, fluid.defaultFluidState().createLegacyBlock());

    BlockPos[] neighbors = {
      SOURCE_POS.west(), SOURCE_POS.east(), SOURCE_POS.north(), SOURCE_POS.south()
    };
    helper.succeedWhen(
        () -> {
          FluidState source = helper.getLevel().getFluidState(helper.absolutePos(SOURCE_POS));
          helper.assertTrue(
              source.isSource() && source.getType().isSame(fluid),
              "the fluid source must remain at the placement position");
          for (BlockPos neighbor : neighbors) {
            FluidState state = helper.getLevel().getFluidState(helper.absolutePos(neighbor));
            if (state.getType().isSame(fluid) && !state.isSource()) {
              return;
            }
          }
          helper.fail("the placed fluid source must spread to a neighbor");
        });
  }
}
