package ic2.core.gametest;

import ic2.core.block.machine.tileentity.TileEntityElectricFurnace;
import ic2.core.block.machine.tileentity.TileEntityMacerator;
import ic2.core.block.machine.tileentity.TileEntityMetalFormer;
import ic2.core.item.ElectricItemManager;
import ic2.core.ref.Ic2Blocks;
import ic2.core.ref.Ic2Items;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Acceptance checks for vanilla copper across registration and real machine processing. */
public final class VanillaCopperGameTests {
  private static final String EMPTY = "ic2:gametest/empty3x3x3";
  private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void baseCopperResourcesUseOnlyMinecraftRegistryEntries(GameTestHelper helper) {
    for (String path : new String[] {
        "copper_ore", "deepslate_copper_ore", "raw_copper", "raw_copper_block",
        "copper_ingot", "copper_block"
    }) {
      Identifier duplicate = Identifier.fromNamespaceAndPath("ic2", path);
      helper.assertFalse(BuiltInRegistries.ITEM.containsKey(duplicate),
          "IC2 must not register a second base copper item: " + duplicate);
      helper.assertFalse(BuiltInRegistries.BLOCK.containsKey(duplicate),
          "IC2 must not register a second base copper block: " + duplicate);
    }
    helper.assertTrue(BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath("ic2", "copper_dust")),
        "the IC processing material copper dust must remain available");
    helper.assertTrue(BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath("ic2", "copper_plate")),
        "the IC processing material copper plate must remain available");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 400)
  public void maceratorProcessesVanillaRawCopper(GameTestHelper helper) {
    helper.setBlock(MACHINE_POS, Ic2Blocks.MACERATOR);
    var machine = helper.getBlockEntity(MACHINE_POS, TileEntityMacerator.class);
    machine.dischargeSlot.put(0,
        ElectricItemManager.getCharged(Ic2Items.RE_BATTERY, Double.POSITIVE_INFINITY));
    machine.inputSlot.put(0, new ItemStack(Items.RAW_COPPER));
    helper.succeedWhen(() -> {
      ItemStack result = machine.outputSlot.get(0);
      helper.assertTrue(result.is(Ic2Items.CRUSHED_COPPER) && result.getCount() == 2,
          "one vanilla raw copper must produce two IC crushed copper");
      helper.assertTrue(machine.inputSlot.isEmpty(), "the raw copper input must be consumed");
    });
  }

  @GameTest(structure = EMPTY, maxTicks = 200)
  public void electricFurnaceReturnsVanillaCopperIngot(GameTestHelper helper) {
    helper.setBlock(MACHINE_POS, Ic2Blocks.ELECTRIC_FURNACE);
    var machine = helper.getBlockEntity(MACHINE_POS, TileEntityElectricFurnace.class);
    machine.dischargeSlot.put(0,
        ElectricItemManager.getCharged(Ic2Items.RE_BATTERY, Double.POSITIVE_INFINITY));
    machine.inputSlot.put(0, new ItemStack(Ic2Items.COPPER_DUST));
    helper.succeedWhen(() -> {
      ItemStack result = machine.outputSlot.get(0);
      helper.assertTrue(result.is(Items.COPPER_INGOT) && result.getCount() == 1,
          "IC copper dust must smelt into the vanilla copper ingot");
      helper.assertTrue(machine.inputSlot.isEmpty(), "the dust input must be consumed");
    });
  }

  @GameTest(structure = EMPTY, maxTicks = 300)
  public void metalFormerRollsVanillaCopperIngot(GameTestHelper helper) {
    helper.setBlock(MACHINE_POS, Ic2Blocks.METAL_FORMER);
    var machine = helper.getBlockEntity(MACHINE_POS, TileEntityMetalFormer.class);
    machine.setMode(1);
    machine.dischargeSlot.put(0,
        ElectricItemManager.getCharged(Ic2Items.RE_BATTERY, Double.POSITIVE_INFINITY));
    machine.inputSlot.put(0, new ItemStack(Items.COPPER_INGOT));
    helper.succeedWhen(() -> {
      ItemStack result = machine.outputSlot.get(0);
      helper.assertTrue(result.is(Ic2Items.COPPER_PLATE) && result.getCount() == 1,
          "the metal former must accept a vanilla copper ingot for the IC copper plate");
      helper.assertTrue(machine.inputSlot.isEmpty(), "the ingot input must be consumed");
    });
  }
}
