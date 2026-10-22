package ic2.core.item.tool;

import ic2.core.Ic2Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class GuiCropAnalyzer extends Ic2Gui<ContainerAnalyzer> {
  private static final Identifier BACKGROUND =
      Identifier.fromNamespaceAndPath("ic2", "textures/gui/guicrop_analyzer.png");

  public GuiCropAnalyzer(ContainerAnalyzer container, Inventory playerInventory, Component title) {
    super(container, playerInventory, title, 223);
  }

  @Override
  protected void drawForegroundLayer(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
    super.drawForegroundLayer(guiGraphics, mouseX, mouseY);
    HandHeldCropAnalyzer analyzer = this.getContainer().base;
    int scannedLevel = analyzer.getScannedLevel();
    if (scannedLevel == 0) {
      this.drawString(guiGraphics, 8, 37, Component.translatable("ic2.crop.unknown").getString(), 0xFFFFFF);
    }

    if (scannedLevel >= 1) {
      this.drawString(
          guiGraphics, 8, 37, Component.translatable(analyzer.getSeedName()).getString(), 0xFFFFFF);
    }

    if (scannedLevel >= 2) {
      this.drawString(guiGraphics, 8, 50,
          Component.translatable("ic2.crop_analyzer.tier", analyzer.getSeedTier()).getString(), 0xFFFFFF);
      this.drawString(guiGraphics, 8, 73,
          Component.translatable("ic2.crop_analyzer.discovered_by").getString(), 0xFFFFFF);
      String discoveredBy = analyzer.getSeedDiscoveredBy();
      this.drawString(guiGraphics, 8, 86, discoveredBy.equals("unknown")
          ? Component.translatable("ic2.crop_analyzer.unknown_discoverer").getString()
          : discoveredBy, 0xFFFFFF);
    }

    if (scannedLevel >= 3) {
      this.drawString(guiGraphics, 8, 109, localizeSeedDescription(analyzer.getSeedDesc(0)), 0xFFFFFF);
      this.drawString(guiGraphics, 8, 122, localizeSeedDescription(analyzer.getSeedDesc(1)), 0xFFFFFF);
    }

    if (scannedLevel >= 4) {
      this.drawString(guiGraphics, 118, 37,
          Component.translatable("ic2.crop_analyzer.growth").getString(), 0xAE26E6);
      this.drawString(guiGraphics, 118, 50, Integer.toString(analyzer.getSeedGrowth()), 0xAE26E6);
      this.drawString(guiGraphics, 118, 73,
          Component.translatable("ic2.crop_analyzer.gain").getString(), 0xEEC900);
      this.drawString(guiGraphics, 118, 86, Integer.toString(analyzer.getSeedGain()), 0xEEC900);
      this.drawString(guiGraphics, 118, 109,
          Component.translatable("ic2.crop_analyzer.resistance").getString(), 0x00CED1);
      this.drawString(
          guiGraphics, 118, 122, Integer.toString(analyzer.getSeedResistance()), 0x00CED1);
    }
  }

  private static String localizeSeedDescription(String description) {
    // Crop attributes are also breeding identifiers. Translate only their displayed labels.
    String[] attributes = description.split(",", -1);
    for (int i = 0; i < attributes.length; i++) {
      String attribute = attributes[i].strip();
      if (!attribute.isEmpty()) {
        attributes[i] = attributes[i].replace(attribute,
            Component.translatableWithFallback("ic2.crop.attribute." + attribute, attribute).getString());
      }
    }
    return String.join(",", attributes);
  }

  @Override
  protected Identifier getTextureLocation() {
    return BACKGROUND;
  }
}
