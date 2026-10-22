package ic2.core;

import ic2.api.upgrade.IUpgradableBlock;
import ic2.api.upgrade.IUpgradeItem;
import ic2.api.upgrade.UpgradableProperty;
import ic2.api.upgrade.UpgradeRegistry;
import ic2.core.gui.GuiElement;
import ic2.core.gui.IClickHandler;
import ic2.core.gui.IKeyboardDependent;
import ic2.core.gui.MouseButton;
import ic2.core.gui.ScrollDirection;
import ic2.core.util.StackUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix3x2fStack;

public abstract class Ic2Gui<T extends ContainerBase<? extends Container>>
    extends AbstractContainerScreen<T> {
  public static final int textHeight = 8;
  protected static Runnable closeHandler;
  protected final List<GuiElement<?>> elements = new ArrayList<>();
  private final Set<GuiElement.ImplementedMethod> elementMethods =
      EnumSet.noneOf(GuiElement.ImplementedMethod.class);
  private final Queue<Ic2Gui.Tooltip> queuedTooltips = new ArrayDeque<>();
  private boolean fixKeyEvents = false;
  private GuiGraphicsExtractor guiGraphics;
  private boolean doubleClick;
  private KeyEvent activeKeyEvent;

  public Ic2Gui(T container, Inventory playerInventory, Component title) {
    this(container, playerInventory, title, 176, 166);
  }

  public Ic2Gui(T container, Inventory playerInventory, Component title, int ySize) {
    this(container, playerInventory, title, 176, ySize);
  }

  public Ic2Gui(T container, Inventory playerInventory, Component title, int xSize, int ySize) {
    super(container, playerInventory, title, xSize, ySize);
  }

  private static List<ItemStack> getCompatibleUpgrades(IUpgradableBlock block) {
    List<ItemStack> ret = new ArrayList<>();
    Set<UpgradableProperty> properties = block.getUpgradableProperties();

    for (ItemStack stack : UpgradeRegistry.getUpgrades()) {
      IUpgradeItem item = (IUpgradeItem) stack.getItem();
      if (item.isSuitableFor(stack, properties)) {
        ret.add(stack);
      }
    }

    return ret;
  }

  private static Identifier boundTexture;

  public static void bindTexture(Identifier id) {
    boundTexture = id;
  }

  public GuiGraphicsExtractor graphics() { return this.guiGraphics; }

  public final T getContainer() {
    return this.menu;
  }

  public final int getX() {
    return this.leftPos;
  }

  public final int getY() {
    return this.topPos;
  }

  public final Slot getFocusedSlot() {
    return this.hoveredSlot;
  }

  public void init() {
    super.init();

    for (GuiElement<?> element : this.elements) {
      if (element instanceof IKeyboardDependent) {
        // TODO: In 1.20.1, keyboardHandler.setSendRepeatsToGui was removed.
        // this.minecraft.keyboardHandler.setSendRepeatsToGui(true);
        this.fixKeyEvents = true;
        break;
      }
    }
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
    this.guiGraphics = guiGraphics;
    this.renderBg(guiGraphics, partialTicks, mouseX, mouseY);
    super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
    this.flushTooltips(guiGraphics);
  }

  public void containerTick() {
    super.containerTick();
    if (this.elementMethods.contains(GuiElement.ImplementedMethod.tick)) {
      for (GuiElement<?> element : this.elements) {
        if (element.isEnabled()) {
          element.tick();
        }
      }
    }
  }

  protected void renderBg(GuiGraphicsExtractor guiGraphics, float delta, int mouseX, int mouseY) {
    mouseX -= this.leftPos;
    mouseY -= this.topPos;
    this.drawBackgroundAndTitle(guiGraphics, delta, mouseX, mouseY);
    if (this.menu.base instanceof IUpgradableBlock) {
      bindTexture(Identifier.fromNamespaceAndPath("ic2", "textures/gui/infobutton.png"));
      this.drawTexturedRect(guiGraphics.pose(), 3.0, 3.0, 10.0, 10.0, 0.0, 0.0);
    }

    if (this.elementMethods.contains(GuiElement.ImplementedMethod.drawBackground)) {
      for (GuiElement<?> element : this.elements) {
        if (element.isEnabled()) {
          element.drawBackground(guiGraphics, mouseX, mouseY);
        }
      }
    }
  }

  protected void drawBackgroundAndTitle(
      GuiGraphicsExtractor guiGraphics, float partialTicks, int mouseX, int mouseY) {
    this.bindTexture();
    guiGraphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, 
        this.getTextureLocation(),
        this.leftPos,
        this.topPos,
        0.0F,
        0.0F,
        this.imageWidth,
        this.imageHeight,
        256,
        256);
    this.drawXCenteredString(guiGraphics, this.imageWidth / 2, 6, this.title, 4210752, false);
  }

  protected final void extractLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
    this.drawForegroundLayer(guiGraphics, mouseX - this.leftPos, mouseY - this.topPos);
  }

  protected void drawForegroundLayer(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
    if (this.menu.base instanceof IUpgradableBlock) {
      this.handleUpgradeTooltip(mouseX, mouseY);
    }

    for (GuiElement<?> element : this.elements) {
      if (element.isEnabled()) {
        element.drawForeground(guiGraphics, mouseX, mouseY);
      }
    }
  }

  private void handleUpgradeTooltip(int mouseX, int mouseY) {
    int areaSize = 12;
    if (mouseX >= 0 && mouseX <= 12 && mouseY >= 0 && mouseY <= 12) {
      List<Component> text = new ArrayList<>();
      text.add(Component.translatable("ic2.generic.text.upgrade"));

      for (ItemStack stack : getCompatibleUpgrades((IUpgradableBlock) this.menu.base)) {
        text.add(stack.getHoverName().copy().withStyle(ChatFormatting.GRAY));
      }

      this.drawTooltip(mouseX, mouseY, text);
    }
  }

  public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    double scrollDelta = scrollY != 0.0 ? scrollY : scrollX;
    if (this.elementMethods.contains(GuiElement.ImplementedMethod.onMouseScroll)) {
      ScrollDirection direction;
      if (scrollDelta != 0.0) {
        direction = scrollDelta < 0.0 ? ScrollDirection.down : ScrollDirection.up;
      } else {
        direction = ScrollDirection.stopped;
      }

      for (GuiElement<?> element : this.elements) {
        int localX = (int) mouseX - this.leftPos;
        int localY = (int) mouseY - this.topPos;
        if (element.isEnabled() && element.contains(localX, localY)
            && element.getImplementedMethods().contains(GuiElement.ImplementedMethod.onMouseScroll)) {
          element.onMouseScroll(localX, localY, direction);
          return true;
        }
      }
    }

    return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    this.doubleClick = doubleClick;
    try {
      return mouseClicked(event.x(), event.y(), ic2.core.gui.GuiInput.legacyMouseButton(event.button()));
    } finally {
      this.doubleClick = false;
    }
  }

  public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
    if (this.elementMethods.contains(GuiElement.ImplementedMethod.onMouseClick)) {
      MouseButton button = MouseButton.get(mouseButton);
      if (button != null) {
        boolean handled = false;
        mouseX -= this.leftPos;
        mouseY -= this.topPos;

        for (GuiElement<?> element : this.elements) {
          if (element.isEnabled()) {
            handled |=
                element.onMouseClick(
                    (int) mouseX,
                    (int) mouseY,
                    button,
                    element.contains((int) mouseX, (int) mouseY));
          }
        }

        if (handled) {
          return true;
        }

        mouseX += this.leftPos;
        mouseY += this.topPos;
      }
    }

    return super.mouseClicked(ic2.core.gui.GuiInput.mouseEvent(mouseX, mouseY, mouseButton), this.doubleClick);
  }

  @Override
  public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
    return mouseDragged(event.x(), event.y(), ic2.core.gui.GuiInput.legacyMouseButton(event.button()), deltaX, deltaY);
  }

  public boolean mouseDragged(
      double mouseX, double mouseY, int clickedMouseButton, double deltaX, double deltaY) {
    if (this.elementMethods.contains(GuiElement.ImplementedMethod.onMouseDrag)) {
      MouseButton button = MouseButton.get(clickedMouseButton);
      if (button != null) {
        boolean handled = false;
        mouseX -= this.leftPos;
        mouseY -= this.topPos;

        for (GuiElement<?> element : this.elements) {
          if (element.isEnabled()) {
            handled |=
                element.onMouseDrag(
                    (int) mouseX,
                    (int) mouseY,
                    button,
                    element.contains((int) mouseX, (int) mouseY));
          }
        }

        if (handled) {
          return true;
        }

        mouseX += this.leftPos;
        mouseY += this.topPos;
      }
    }

    return super.mouseDragged(ic2.core.gui.GuiInput.mouseEvent(mouseX, mouseY, clickedMouseButton), deltaX, deltaY);
  }

  @Override
  public boolean mouseReleased(MouseButtonEvent event) {
    return mouseReleased(event.x(), event.y(), ic2.core.gui.GuiInput.legacyMouseButton(event.button()));
  }

  public boolean mouseReleased(double mouseX, double mouseY, int state) {
    if (this.elementMethods.contains(GuiElement.ImplementedMethod.onMouseRelease)) {
      MouseButton button = MouseButton.get(state);
      if (button != null) {
        boolean handled = false;
        mouseX -= this.leftPos;
        mouseY -= this.topPos;

        for (GuiElement<?> element : this.elements) {
          if (element.isEnabled()) {
            handled |=
                element.onMouseRelease(
                    (int) mouseX,
                    (int) mouseY,
                    button,
                    element.contains((int) mouseX, (int) mouseY));
          }
        }

        if (handled) {
          return true;
        }

        mouseX += this.leftPos;
        mouseY += this.topPos;
      }
    }

    return super.mouseReleased(ic2.core.gui.GuiInput.mouseEvent(mouseX, mouseY, state));
  }

  @Override
  public boolean charTyped(CharacterEvent event) {
    boolean handled = false;
    for (char ch : event.codepointAsString().toCharArray()) {
      handled |= charTyped(ch, 0);
    }
    return handled || super.charTyped(event);
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    this.activeKeyEvent = event;
    try {
      return charTyped((char) 0, event.key()) || super.keyPressed(event);
    } finally {
      this.activeKeyEvent = null;
    }
  }

  public KeyEvent keyEvent(int key) {
    return this.activeKeyEvent != null && this.activeKeyEvent.key() == key
        ? this.activeKeyEvent : ic2.core.gui.GuiInput.keyEvent(key);
  }

  public boolean charTyped(char typedChar, int keyCode) {
    if (this.elementMethods.contains(GuiElement.ImplementedMethod.onKeyTyped)) {
      boolean handled = false;

      for (GuiElement<?> element : this.elements) {
        if (element.isEnabled()) {
          handled |= element.onKeyTyped(typedChar, keyCode);
        }
      }

      if (handled) {
        return true;
      }
    }

    return false;
  }

  public void removed() {
    super.removed();
    // TODO: In 1.20.1, keyboardHandler.setSendRepeatsToGui was removed.
    // this.minecraft.keyboardHandler.setSendRepeatsToGui(false);

    if (closeHandler != null) {
      closeHandler.run();
    }
  }

  public void drawTexturedRect(
      Matrix3x2fStack matrices,
      double x,
      double y,
      double width,
      double height,
      double texX,
      double texY) {
    this.drawTexturedRect(matrices, x, y, width, height, texX, texY, false);
  }

  public void drawTexturedRect(
      Matrix3x2fStack matrices,
      double x,
      double y,
      double width,
      double height,
      double texX,
      double texY,
      boolean mirrorX) {
    this.drawTexturedRect(
        matrices,
        x,
        y,
        width,
        height,
        texX / 256.0,
        texY / 256.0,
        (texX + width) / 256.0,
        (texY + height) / 256.0,
        mirrorX);
  }

  public void drawTexturedRect(
      Matrix3x2fStack matrices,
      double x,
      double y,
      double width,
      double height,
      double uS,
      double vS,
      double uE,
      double vE,
      boolean mirrorX) {
    x += this.leftPos;
    y += this.topPos;
    if (mirrorX) {
      double tmp = uS;
      uS = uE;
      uE = tmp;
    }

    // Native blit bounds are integers. Preserve the original floating-point vertices in the
    // pose instead: truncating the bounds while retaining fractional UVs rescales the texture
    // on every progress update and makes all smooth gauges visibly wobble.
    Matrix3x2fStack pose = this.guiGraphics.pose();
    pose.pushMatrix();
    try {
      pose.translate((float) x, (float) y);
      pose.scale((float) width, (float) height);
      this.guiGraphics.blit(boundTexture, 0, 0, 1, 1,
          (float) uS, (float) uE, (float) vS, (float) vE);
    } finally {
      pose.popMatrix();
    }
  }

  public void drawSprite(
      Matrix3x2fStack matrices,
      double x,
      double y,
      double width,
      double height,
      TextureAtlasSprite sprite,
      int color,
      double scale,
      boolean fixRight,
      boolean fixBottom) {
    if (sprite == null) {
      sprite =
          ((TextureAtlas) this.minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS))
              .getSprite(MissingTextureAtlasSprite.getLocation());
    }

    x += this.leftPos;
    y += this.topPos;
    scale *= 16.0;
    double spriteUS = sprite.getU0();
    double spriteVS = sprite.getV0();
    double spriteWidth = sprite.getU1() - spriteUS;
    double spriteHeight = sprite.getV1() - spriteVS;
    int a = color >>> 24 & 0xFF;
    int r = color >>> 16 & 0xFF;
    int g = color >>> 8 & 0xFF;
    int b = color & 0xFF;
    int atlasWidth = (int) Math.round(sprite.contents().width() / spriteWidth);
    int atlasHeight = (int) Math.round(sprite.contents().height() / spriteHeight);
    double xS = x;

    while (xS < x + width) {
      double maxWidth;
      double uS;
      if (xS == x && fixRight && (maxWidth = width % scale) > 0.0) {
        uS = spriteUS + spriteWidth * (1.0 - maxWidth / scale);
      } else {
        maxWidth = scale;
        uS = spriteUS;
      }

      double xE = Math.min(xS + maxWidth, x + width);
      double uE = uS + (xE - xS) / scale * spriteWidth;
      double yS = y;

      while (yS < y + height) {
        double maxHeight;
        double vS;
        if (yS == y && fixBottom && (maxHeight = height % scale) > 0.0) {
          vS = spriteVS + spriteHeight * (1.0 - maxHeight / scale);
        } else {
          maxHeight = scale;
          vS = spriteVS;
        }

        double yE = Math.min(yS + maxHeight, y + height);
        double vE = vS + (yE - yS) / scale * spriteHeight;
        this.guiGraphics.blit(RenderPipelines.GUI_TEXTURED, sprite.atlasLocation(),
            (int) xS, (int) yS, (float) (uS * atlasWidth), (float) (vS * atlasHeight),
            (int) (xE - xS), (int) (yE - yS),
            (int) Math.round((uE - uS) * atlasWidth), (int) Math.round((vE - vS) * atlasHeight),
            atlasWidth, atlasHeight, color);
        yS += maxHeight;
      }

      xS += maxWidth;
    }

  }

  public void drawItem(int x, int y, ItemStack stack) {
    if (this.guiGraphics != null) {
      this.guiGraphics.item(stack, this.leftPos + x, this.topPos + y);
    }
  }

  public void drawItemStack(int x, int y, ItemStack stack) {
    this.drawItem(x, y, stack);
    if (this.guiGraphics != null) {
      this.guiGraphics.itemDecorations(
          this.font, stack, this.leftPos + x, this.topPos + y, null);
    }
  }

  public void drawColoredRect(Matrix3x2fStack matrices, int x, int y, int width, int height, int color) {
    x += this.leftPos;
    y += this.topPos;
    if ((color & 0xFF000000) == 0) color |= 0xFF000000;
    this.guiGraphics.fill(x, y, x + width, y + height, color);
  }

  public int drawString(GuiGraphicsExtractor guiGraphics, int x, int y, String text, int color) {
    guiGraphics.text(this.font, text, x, y, opaqueTextColor(color), false);
    return x + this.font.width(text);
  }

  public int drawString(
      GuiGraphicsExtractor guiGraphics, int x, int y, String text, int color, boolean ignored) {
    guiGraphics.text(this.font, text, this.leftPos + x, this.topPos + y, opaqueTextColor(color), false);
    return x + this.font.width(text);
  }

  public void drawTrimmedString(
      GuiGraphicsExtractor guiGraphics, int x, int y, String text, int maxWidth, int color) {
    guiGraphics.textWithWordWrap(this.font, Component.literal(text), x, y, maxWidth, opaqueTextColor(color), false);
  }

  public void drawXCenteredString(
      GuiGraphicsExtractor guiGraphics, int x, int y, String text, int color, boolean shadow) {
    this.drawCenteredString(guiGraphics, x, y, text, color, shadow, true, false);
  }

  public void drawXCenteredString(
      GuiGraphicsExtractor guiGraphics, int x, int y, Component text, int color, boolean shadow) {
    this.drawCenteredString(guiGraphics, x, y, text, color, shadow, true, false);
  }

  public void drawXYCenteredString(
      GuiGraphicsExtractor guiGraphics, int x, int y, String text, int color, boolean shadow) {
    this.drawCenteredString(guiGraphics, x, y, text, color, shadow, true, true);
  }

  public void drawXYCenteredString(
      GuiGraphicsExtractor guiGraphics, int x, int y, Component text, int color, boolean shadow) {
    this.drawCenteredString(guiGraphics, x, y, text, color, shadow, true, true);
  }

  public void drawCenteredString(
      GuiGraphicsExtractor guiGraphics,
      int x,
      int y,
      String text,
      int color,
      boolean shadow,
      boolean centerX,
      boolean centerY) {
    if (centerX) {
      x -= this.getStringWidth(text) / 2;
    }

    if (centerY) {
      y -= 4;
    }

    guiGraphics.text(this.font, text, this.leftPos + x, this.topPos + y, opaqueTextColor(color), shadow);
  }

  public void drawCenteredString(
      GuiGraphicsExtractor guiGraphics,
      int x,
      int y,
      Component text,
      int color,
      boolean shadow,
      boolean centerX,
      boolean centerY) {
    if (centerX) {
      x -= this.getStringWidth(text) / 2;
    }

    if (centerY) {
      y -= 4;
    }

    guiGraphics.text(this.font, text, this.leftPos + x, this.topPos + y, opaqueTextColor(color), shadow);
  }

  private static int opaqueTextColor(int color) {
    return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
  }

  public int getStringWidth(String text) {
    return this.font.width(text);
  }

  public int getStringWidth(Component text) {
    return this.font.width(text);
  }

  public String trimStringToWidth(String text, int width) {
    return this.font.plainSubstrByWidth(text, width, false);
  }

  public String trimStringToWidthReverse(String text, int width) {
    return this.font.plainSubstrByWidth(text, width, true);
  }

  public void drawTooltip(int x, int y, List<Component> text) {
    // 清空之前的 tooltip，确保只有最后处理的（视觉最上层的）元素 tooltip 被渲染
    // 避免 TankGauge + SlotGrid 等重叠元素的 tooltip 叠加显示
    this.queuedTooltips.clear();
    this.queuedTooltips.add(new Ic2Gui.Tooltip(text, x, y));
  }

  public void drawTooltip(GuiGraphicsExtractor guiGraphics, int x, int y, ItemStack stack) {
    assert !StackUtil.isEmpty(stack);
    guiGraphics.setTooltipForNextFrame(this.font, stack, x + this.leftPos, y + this.topPos);
  }

  protected void flushTooltips(GuiGraphicsExtractor guiGraphics) {
    // 如果 vanilla 接下来会渲染 slot 物品 tooltip，跳过 IC2 的 tooltip，避免与物品 tooltip 重叠
    if (this.hoveredSlot != null
        && this.hoveredSlot.hasItem()
        && this.menu.getCarried().isEmpty()) {
      this.queuedTooltips.clear();
      return;
    }

    for (Ic2Gui.Tooltip tooltip : this.queuedTooltips) {
      guiGraphics.setTooltipForNextFrame(
          this.font, tooltip.text, java.util.Optional.empty(),
          tooltip.x + this.leftPos, tooltip.y + this.topPos);
    }

    this.queuedTooltips.clear();
  }

  protected void addElement(GuiElement<?> element) {
    this.elements.add(element);
    this.elementMethods.addAll(element.getImplementedMethods());
  }

  protected final void bindTexture() {
    bindTexture(this.getTextureLocation());
  }

  protected IClickHandler createEventSender(int event) {
    if (this.menu.base instanceof BlockEntity) {
      return button ->
          IC2.network
              .get(false)
              .initiateClientTileEntityEvent((BlockEntity) Ic2Gui.this.menu.base, event);
    } else {
      throw new IllegalArgumentException(
          "not applicable for " + ((ContainerBase<?>) this.menu).base);
    }
  }

  protected abstract Identifier getTextureLocation();

  private record Tooltip(List<Component> text, int x, int y) {}
}
