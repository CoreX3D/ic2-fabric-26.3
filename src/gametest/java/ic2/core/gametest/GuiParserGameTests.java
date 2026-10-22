package ic2.core.gametest;

import ic2.core.block.personal.TileEntityPersonalChest;
import ic2.core.gui.dynamic.GuiParser;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

public class GuiParserGameTests {
  private static final String TEMPLATE = "ic2:gametest/empty3x3x3";

  private static final Identifier PERSONAL_CHEST =
      Identifier.fromNamespaceAndPath("ic2", "personal_chest");

  // GUI definitions resolve through baseClass first so addon guidef XMLs (in the addon's own
  // module) are found; module resource lookups don't cross mod boundaries. The GuiParser.class
  // fallback keeps XMLs loadable when baseClass lives in another module.
  @GameTest(structure = TEMPLATE, maxTicks = 20)
  public void guiDefsResolveThroughBaseClassAndFallback(GameTestHelper helper) {
    // same-module path: baseClass is an ic2 class, first lookup must hit
    GuiParser.GuiNode viaOwnModule = GuiParser.parse(PERSONAL_CHEST, TileEntityPersonalChest.class);
    helper.assertTrue(
        viaOwnModule != null, "guidef must parse with a baseClass from the owning module");

    // cross-module miss: a minecraft-module class cannot see ic2 assets, so this only succeeds
    // if the GuiParser.class fallback still runs
    GuiParser.GuiNode viaFallback = GuiParser.parse(PERSONAL_CHEST, ChestBlockEntity.class);
    helper.assertTrue(
        viaFallback != null, "guidef must still parse via the GuiParser.class fallback");

    helper.succeed();
  }
}
