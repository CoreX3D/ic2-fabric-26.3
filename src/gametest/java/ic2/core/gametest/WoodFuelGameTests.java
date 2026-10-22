package ic2.core.gametest;

import ic2.api.info.Info;
import ic2.core.block.comp.Energy;
import ic2.core.block.generator.tileentity.TileEntityGenerator;
import ic2.core.block.heatgenerator.tileentity.TileEntitySolidHeatGenerator;
import ic2.core.block.invslot.InvSlotConsumableFuel;
import ic2.core.block.machine.tileentity.TileEntityIronFurnace;
import ic2.core.ref.Ic2Blocks;
import ic2.core.ref.Ic2Items;
import ic2.core.slot.SlotInvSlot;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/** Fuel checks run after server data and item component maps have loaded. */
public class WoodFuelGameTests {
  private static final String EMPTY = "ic2:gametest/empty3x3x3";
  private static final BlockPos GENERATOR_POS = new BlockPos(1, 1, 1);

  @GameTest(structure = EMPTY)
  public void allVanillaLogsAndPlanksFollowNativeFuelValues(GameTestHelper helper) {
    helper.setBlock(GENERATOR_POS, Ic2Blocks.GENERATOR);
    TileEntityGenerator generator = getTe(helper, GENERATOR_POS, TileEntityGenerator.class);
    AbstractContainerMenu menu = generator.createServerScreenHandler(1, helper.makeMockServerPlayerInLevel());
    Slot fuelSlot = findFuelSlot(menu, generator.fuelSlot);
    int checked = 0;
    int burnable = 0;
    for (Item item : BuiltInRegistries.ITEM) {
      if (!"minecraft".equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) continue;
      ItemStack stack = new ItemStack(item);
      if (!stack.is(ItemTags.LOGS_THAT_BURN) && !stack.is(ItemTags.PLANKS)) continue;
      int expected = nativeBurnTime(helper, stack);
      String name = BuiltInRegistries.ITEM.getKey(item).toString();
      helper.assertValueEqual(Info.getItemInfo().getFuelValue(stack, false), expected, name + " native fuel ticks");
      helper.assertValueEqual(fuelSlot.mayPlace(stack), expected > 0, name + " generator GUI insertion");
      checked++;
      if (expected > 0) burnable++;
    }
    helper.assertTrue(checked >= 40 && burnable >= 40, "must check every loaded vanilla log/plank family");
    helper.assertValueEqual(nativeBurnTime(helper, new ItemStack(Items.BIRCH_LOG)), 300, "native birch fuel ticks");
    helper.succeed();
  }

  @GameTest(structure = EMPTY)
  public void allRubberWoodVariantsAreRealCookingFuels(GameTestHelper helper) {
    helper.setBlock(GENERATOR_POS, Ic2Blocks.GENERATOR);
    TileEntityGenerator generator = getTe(helper, GENERATOR_POS, TileEntityGenerator.class);
    Slot slot = findFuelSlot(generator.createServerScreenHandler(1, helper.makeMockServerPlayerInLevel()), generator.fuelSlot);
    for (Item item : rubberWoods()) {
      ItemStack stack = new ItemStack(item);
      String name = BuiltInRegistries.ITEM.getKey(item).toString();
      helper.assertValueEqual(nativeBurnTime(helper, stack), 300, name + " vanilla furnace fuel ticks");
      helper.assertValueEqual(Info.getItemInfo().getFuelValue(stack, false), 300, name + " IC fuel ticks");
      helper.assertTrue(slot.mayPlace(stack), name + " must fit the actual generator fuel slot");
    }
    for (List<Item> pair : List.of(
        List.of(Ic2Items.RUBBER_BUTTON, Items.OAK_BUTTON),
        List.of(Ic2Items.RUBBER_DOOR, Items.OAK_DOOR),
        List.of(Ic2Items.RUBBER_FENCE, Items.OAK_FENCE),
        List.of(Ic2Items.RUBBER_FENCE_GATE, Items.OAK_FENCE_GATE),
        List.of(Ic2Items.RUBBER_PRESSURE_PLATE, Items.OAK_PRESSURE_PLATE),
        List.of(Ic2Items.RUBBER_SIGN, Items.OAK_SIGN),
        List.of(Ic2Items.RUBBER_SLAB, Items.OAK_SLAB),
        List.of(Ic2Items.RUBBER_STAIRS, Items.OAK_STAIRS),
        List.of(Ic2Items.RUBBER_TRAPDOOR, Items.OAK_TRAPDOOR))) {
      ItemStack stack = new ItemStack(pair.getFirst());
      helper.assertValueEqual(nativeBurnTime(helper, stack), nativeBurnTime(helper, new ItemStack(pair.getLast())),
          pair.getFirst() + " fuel time must match its native wood counterpart");
      helper.assertTrue(slot.mayPlace(stack), "generator accepts wooden derivative " + pair.getFirst());
    }
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 130)
  public void generatorBurnsBirchLogIntoEu(GameTestHelper helper) {
    assertWoodEnergy(helper, Items.BIRCH_LOG);
  }

  @GameTest(structure = EMPTY, maxTicks = 130)
  public void generatorBurnsRubberPlanksIntoEu(GameTestHelper helper) {
    assertWoodEnergy(helper, Ic2Items.RUBBER_PLANKS);
  }

  @GameTest(structure = EMPTY)
  public void ironFurnaceAndSolidHeatGeneratorAcceptRubberWood(GameTestHelper helper) {
    helper.setBlock(new BlockPos(0, 1, 1), Ic2Blocks.IRON_FURNACE);
    helper.setBlock(new BlockPos(2, 1, 1), Ic2Blocks.SOLID_HEAT_GENERATOR);
    TileEntityIronFurnace furnace = getTe(helper, new BlockPos(0, 1, 1), TileEntityIronFurnace.class);
    TileEntitySolidHeatGenerator heat = getTe(helper, new BlockPos(2, 1, 1), TileEntitySolidHeatGenerator.class);
    var player = helper.makeMockServerPlayerInLevel();
    Slot furnaceSlot = findFuelSlot(furnace.createServerScreenHandler(1, player), furnace.fuelSlot);
    Slot heatSlot = findFuelSlot(heat.createServerScreenHandler(2, player), heat.fuelSlot);
    for (Item item : rubberWoods()) {
      ItemStack stack = new ItemStack(item);
      helper.assertTrue(furnaceSlot.mayPlace(stack), "iron furnace accepts " + item);
      helper.assertTrue(heatSlot.mayPlace(stack), "solid heat generator accepts " + item);
    }
    heatSlot.set(new ItemStack(Ic2Items.RUBBER_LOG));
    helper.assertTrue(heat.gainFuel(), "solid heat generator consumes rubber log");
    helper.assertValueEqual(heat.fuel, 75, "rubber log heat generator fuel ticks");
    helper.assertTrue(heat.fuelSlot.isEmpty(), "heat generator consumes one log");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 210)
  public void ironFurnaceSmeltsUsingRubberPlanks(GameTestHelper helper) {
    helper.setBlock(GENERATOR_POS, Ic2Blocks.IRON_FURNACE);
    TileEntityIronFurnace furnace = getTe(helper, GENERATOR_POS, TileEntityIronFurnace.class);
    Slot slot = findFuelSlot(furnace.createServerScreenHandler(1, helper.makeMockServerPlayerInLevel()), furnace.fuelSlot);
    helper.assertTrue(slot.mayPlace(new ItemStack(Ic2Items.RUBBER_PLANKS)), "rubber planks can be inserted in iron furnace");
    slot.set(new ItemStack(Ic2Items.RUBBER_PLANKS));
    furnace.inputSlot.put(0, new ItemStack(Items.COBBLESTONE));
    helper.succeedWhen(() -> {
      helper.assertTrue(furnace.outputSlot.get(0).is(Items.STONE), "rubber planks must smelt cobblestone into stone");
      helper.assertTrue(furnace.fuelSlot.isEmpty(), "iron furnace consumes the rubber plank");
    });
  }

  @GameTest(structure = EMPTY)
  public void nonFuelsStayRejectedAndCustomFuelValuesStayIntact(GameTestHelper helper) {
    helper.setBlock(GENERATOR_POS, Ic2Blocks.GENERATOR);
    TileEntityGenerator generator = getTe(helper, GENERATOR_POS, TileEntityGenerator.class);
    Slot slot = findFuelSlot(generator.createServerScreenHandler(1, helper.makeMockServerPlayerInLevel()), generator.fuelSlot);
    for (Item item : List.of(Items.CRIMSON_STEM, Items.WARPED_STEM, Items.CRIMSON_PLANKS, Items.WARPED_PLANKS, Items.STONE, Items.COBBLESTONE)) {
      ItemStack stack = new ItemStack(item);
      helper.assertValueEqual(nativeBurnTime(helper, stack), 0, item + " native nonfuel");
      helper.assertValueEqual(Info.getItemInfo().getFuelValue(stack, false), 0, item + " IC nonfuel");
      helper.assertTrue(!slot.mayPlace(stack), "generator must reject " + item);
    }
    helper.assertValueEqual(Info.getItemInfo().getFuelValue(new ItemStack(Ic2Items.RUBBER_SAPLING), false), 80, "rubber sapling retains original IC fuel value");
    helper.assertValueEqual(Info.getItemInfo().getFuelValue(new ItemStack(Ic2Items.COKE), false), 3200, "coke retains original IC fuel value");
    helper.assertValueEqual(Info.getItemInfo().getFuelValue(new ItemStack(Items.COAL), false), 1600, "coal fuel value");
    helper.assertFalse(Info.getItemInfo().isFuel(new ItemStack(Items.LAVA_BUCKET), false),
        "solid-fuel generator must continue to reject lava");
    helper.assertTrue(Info.getItemInfo().isFuel(new ItemStack(Items.LAVA_BUCKET), true),
        "lava-enabled iron furnace must still accept lava");
    helper.assertValueEqual(Info.getItemInfo().getFuelValue(new ItemStack(Items.LAVA_BUCKET), true), 2000,
        "IC lava fuel time keeps its original one-tenth scaling");
    helper.succeed();
  }

  private static void assertWoodEnergy(GameTestHelper helper, Item wood) {
    helper.setBlock(GENERATOR_POS, Ic2Blocks.GENERATOR);
    TileEntityGenerator generator = getTe(helper, GENERATOR_POS, TileEntityGenerator.class);
    Slot slot = findFuelSlot(generator.createServerScreenHandler(1, helper.makeMockServerPlayerInLevel()), generator.fuelSlot);
    ItemStack stack = new ItemStack(wood);
    helper.assertTrue(slot.mayPlace(stack), "wood must fit actual generator menu fuel slot");
    slot.set(stack);
    helper.succeedWhen(() -> {
      helper.assertTrue(generator.fuelSlot.isEmpty(), "generator consumes one wood item");
      helper.assertValueEqual(generator.fuel, 0, "generator has finished burning wood");
      Ic2GameTestAssertions.assertNear(helper, generator.getComponent(Energy.class).getEnergy(), 750.0, "300 furnace ticks generate exactly 750 EU");
    });
  }

  private static int nativeBurnTime(GameTestHelper helper, ItemStack stack) {
    LootContext context = new LootContext.Builder(new LootParams.Builder(helper.getLevel()).create(LootContextParamSets.EMPTY)).create(Optional.empty());
    return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
  }

  private static List<Item> rubberWoods() {
    return List.of(Ic2Items.RUBBER_LOG, Ic2Items.STRIPPED_RUBBER_LOG, Ic2Items.RUBBER_WOOD, Ic2Items.STRIPPED_RUBBER_WOOD, Ic2Items.RUBBER_PLANKS);
  }

  private static Slot findFuelSlot(AbstractContainerMenu menu, InvSlotConsumableFuel fuel) {
    for (Slot slot : menu.slots) {
      if (slot instanceof SlotInvSlot ic2Slot && ic2Slot.invSlot == fuel) return slot;
    }
    throw new IllegalStateException("actual machine container has no fuel slot");
  }

  private static <T extends BlockEntity> T getTe(GameTestHelper helper, BlockPos pos, Class<T> type) {
    return type.cast(helper.getBlockEntity(pos, BlockEntity.class));
  }
}
