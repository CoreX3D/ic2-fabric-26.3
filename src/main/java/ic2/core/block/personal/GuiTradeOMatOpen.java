package ic2.core.block.personal;

import ic2.core.IC2;
import ic2.core.Ic2Gui;
import ic2.core.gui.VanillaButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class GuiTradeOMatOpen extends Ic2Gui<ContainerTradeOMatOpen> {
  private static final Identifier background =
      IC2.getIdentifier("textures/gui/guitradeomatopen.png");

  public GuiTradeOMatOpen(
      ContainerTradeOMatOpen container, Inventory playerInventory, Component title) {
    super(container, playerInventory, title);
    if (this.menu.canToggleInfinite) {
      this.addElement(
          new VanillaButton(this, 152, 4, 20, 20, this.createEventSender(0)).withText("∞"));
    }
  }

  @Override
  protected void drawForegroundLayer(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
    super.drawForegroundLayer(guiGraphics, mouseX, mouseY);
    this.drawString(
        guiGraphics,
        8,
        this.imageHeight - 96 + 2,
        Component.translatable("container.inventory").getString(),
        4210752);
    this.drawString(
        guiGraphics,
        12,
        23,
        Component.translatable("ic2.container.personalTrader.want").getString(),
        4210752);
    this.drawString(
        guiGraphics,
        12,
        57,
        Component.translatable("ic2.container.personalTrader.offer").getString(),
        4210752);
    this.drawString(
        guiGraphics,
        108,
        28,
        Component.translatable("ic2.container.personalTrader.totalTrades0").getString(),
        4210752);
    this.drawString(
        guiGraphics,
        108,
        36,
        Component.translatable("ic2.container.personalTrader.totalTrades1").getString(),
        4210752);
    this.drawString(guiGraphics, 112, 44, this.menu.base.totalTradeCount + "", 4210752);
    this.drawString(
        guiGraphics,
        108,
        60,
        Component.translatable("ic2.container.personalTrader.stock")
            + " "
            + (this.menu.base.stock < 0 ? "∞" : this.menu.base.stock + ""),
        4210752);
  }

  @Override
  protected Identifier getTextureLocation() {
    return background;
  }
}
