package ic2.client.gametest;

import ic2.core.Ic2Gui;
import ic2.core.block.machine.container.ContainerMetalFormer;
import ic2.core.block.machine.gui.GuiMetalFormer;
import ic2.core.block.machine.tileentity.TileEntityMetalFormer;
import ic2.core.gui.GuiElement;
import ic2.core.ref.Ic2Blocks;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

/** Verifies the actual native blit snapshots, including their delayed-render pose copies. */
@Environment(EnvType.CLIENT)
@SuppressWarnings("UnstableApiUsage")
public final class GuiTexturePrecisionChecks {
  private static final int LEFT = 37;
  private static final int TOP = 19;
  private static final double EPSILON = 0.0001;

  private GuiTexturePrecisionChecks() {}

  public static List<String> validate(ClientGameTestContext context) {
    return context.computeOnClient(client -> {
      Path directory = client.gameDirectory.toPath().toAbsolutePath().normalize();
      require(directory.endsWith(Path.of("build", "client-render-smoke")),
          "GUI texture fixtures are permitted only in build/client-render-smoke");
      require(client.player != null && client.level != null,
          "GUI texture fixtures require the connected client player");
      TileEntityMetalFormer machine = new TileEntityMetalFormer(BlockPos.ZERO,
          Ic2Blocks.METAL_FORMER.defaultBlockState());
      GuiMetalFormer screen = new GuiMetalFormer(
          new ContainerMetalFormer(0, client.player.getInventory(), machine),
          client.player.getInventory(), Component.literal("IC2 texture precision fixture"));
      setField(AbstractContainerScreen.class, screen, "leftPos", LEFT);
      setField(AbstractContainerScreen.class, screen, "topPos", TOP);

      Identifier previousTexture = (Identifier) field(Ic2Gui.class, null, "boundTexture");
      Object previousGraphics = field(Ic2Gui.class, screen, "guiGraphics");
      try {
        Ic2Gui.bindTexture(GuiElement.commonTexture);
        List<Rectangle> cases = new ArrayList<>();
        for (double width : new double[] {23.4, 23.9, 24.0}) {
          cases.add(Rectangle.texels("horizontal " + width, 12.25, 8.5,
              width, 9.0, 200.0, 19.0, false));
        }
        for (double height : new double[] {13.4, 13.9, 14.0}) {
          cases.add(Rectangle.texels("vertical " + height, 6.125, 5.375,
              3.0, height, 252.0, 33.0, false));
        }
        cases.add(Rectangle.texels("reverse horizontal", 52.0 + 46.0 - 23.4,
            39.0, 23.4, 9.0, 200.0 + 46.0 - 23.4, 19.0, false));
        cases.add(Rectangle.texels("reverse vertical", 20.0, 37.0 + 14.0 - 13.4,
            3.0, 13.4, 252.0, 33.0 + 14.0 - 13.4, false));
        cases.add(Rectangle.texels("mirrored fractional", 12.25, 8.5,
            23.9, 9.0, 200.0, 19.0, true));
        cases.add(new Rectangle("normalized UV scaling", 7.125, 11.375, 31.75, 15.5,
            0.125, 0.25, 0.25, 0.375, false, false));
        for (Rectangle rectangle : cases) {
          verifySingle(client, screen, rectangle, new Matrix3x2f());
        }

        Matrix3x2f affine = new Matrix3x2f().translate(3.25F, 5.75F)
            .rotate(0.17F).scale(1.25F, 0.75F);
        verifySingle(client, screen, cases.get(1), affine);
        verifySingle(client, screen, cases.get(8), affine);
        verifyMultiple(client, screen, affine, cases.subList(0, 3));
        return List.of("GUI native texture precision: 12 individual blit snapshots preserve all four "
            + "floating-point corners and UVs, including widths 23.4/23.9/24, vertical/reverse fills, "
            + "subpixel positions, mirroring and an existing affine pose; no source texel compression.",
            "GUI native delayed rendering: three queued progress quads retain independent pose "
                + "snapshots after later pose mutation; drawTexturedRect restores the caller pose.");
      } finally {
        setField(Ic2Gui.class, screen, "guiGraphics", previousGraphics);
        Ic2Gui.bindTexture(previousTexture);
      }
    });
  }

  private static void verifySingle(Minecraft client, Ic2Gui<?> screen,
      Rectangle rectangle, Matrix3x2fc initialPose) {
    GuiRenderState state = new GuiRenderState();
    GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(client, state, 0, 0);
    graphics.pose().set(initialPose);
    setField(Ic2Gui.class, screen, "guiGraphics", graphics);
    submit(screen, graphics, rectangle);
    requirePose(initialPose, graphics.pose(), rectangle.name + " caller pose");
    List<BlitRenderState> blits = blits(state);
    require(blits.size() == 1, rectangle.name + " did not submit exactly one native blit: " + blits.size());
    verifyBlit(blits.getFirst(), rectangle, initialPose);
  }

  private static void verifyMultiple(Minecraft client, Ic2Gui<?> screen,
      Matrix3x2fc initialPose, List<Rectangle> rectangles) {
    GuiRenderState state = new GuiRenderState();
    GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(client, state, 0, 0);
    graphics.pose().set(initialPose);
    setField(Ic2Gui.class, screen, "guiGraphics", graphics);
    for (Rectangle rectangle : rectangles) {
      submit(screen, graphics, rectangle);
      requirePose(initialPose, graphics.pose(), "queued " + rectangle.name + " caller pose");
    }
    // The renderer consumes the recorded blits later; changing this stack must not alter them.
    graphics.pose().translate(101.5F, -37.25F).scale(2.0F, 3.0F);
    List<BlitRenderState> blits = blits(state);
    require(blits.size() == rectangles.size(), "Multiple native blits were lost: " + blits.size());
    for (Rectangle rectangle : rectangles) {
      List<BlitRenderState> matching = blits.stream()
          .filter(blit -> near(blit.u1(), rectangle.uEnd, 0.0000001)).toList();
      require(matching.size() == 1, "Queued native blit cannot be identified: " + rectangle.name);
      verifyBlit(matching.getFirst(), rectangle, initialPose);
    }
  }

  private static void submit(Ic2Gui<?> screen, GuiGraphicsExtractor graphics, Rectangle rectangle) {
    if (rectangle.texelCoordinates) {
      screen.drawTexturedRect(graphics.pose(), rectangle.x, rectangle.y,
          rectangle.width, rectangle.height, rectangle.uStart * 256.0,
          rectangle.vStart * 256.0, rectangle.mirror);
    } else {
      screen.drawTexturedRect(graphics.pose(), rectangle.x, rectangle.y,
          rectangle.width, rectangle.height, rectangle.uStart, rectangle.vStart,
          rectangle.uEnd, rectangle.vEnd, rectangle.mirror);
    }
  }

  private static void verifyBlit(BlitRenderState blit, Rectangle rectangle, Matrix3x2fc initialPose) {
    float[] sourceX = {blit.x0(), blit.x1(), blit.x1(), blit.x0()};
    float[] sourceY = {blit.y0(), blit.y0(), blit.y1(), blit.y1()};
    double[] expectedX = {rectangle.x, rectangle.x + rectangle.width,
        rectangle.x + rectangle.width, rectangle.x};
    double[] expectedY = {rectangle.y, rectangle.y,
        rectangle.y + rectangle.height, rectangle.y + rectangle.height};
    for (int corner = 0; corner < 4; corner++) {
      Vector2f actual = blit.pose().transformPosition(sourceX[corner], sourceY[corner], new Vector2f());
      Vector2f expected = initialPose.transformPosition((float) (LEFT + expectedX[corner]),
          (float) (TOP + expectedY[corner]), new Vector2f());
      require(near(actual.x, expected.x, EPSILON) && near(actual.y, expected.y, EPSILON),
          rectangle.name + " corner " + corner + " was quantized or moved: " + actual + " != " + expected);
    }
    require(near(blit.u0(), rectangle.mirror ? rectangle.uEnd : rectangle.uStart, 0.0000001)
            && near(blit.u1(), rectangle.mirror ? rectangle.uStart : rectangle.uEnd, 0.0000001)
            && near(blit.v0(), rectangle.vStart, 0.0000001)
            && near(blit.v1(), rectangle.vEnd, 0.0000001),
        rectangle.name + " source UVs changed");
    if (rectangle.texelCoordinates) {
      require(near(Math.abs(blit.u1() - blit.u0()) * 256.0, rectangle.width, 0.00002)
              && near((blit.v1() - blit.v0()) * 256.0, rectangle.height, 0.00002),
          rectangle.name + " does not retain its original source texel span");
    }
  }

  private static List<BlitRenderState> blits(GuiRenderState state) {
    List<BlitRenderState> result = new ArrayList<>();
    state.forEachElement(element -> {
      if (element instanceof BlitRenderState blit) result.add(blit);
    }, GuiRenderState.TraverseRange.ALL);
    return result;
  }

  private static void requirePose(Matrix3x2fc expected, Matrix3x2fc actual, String description) {
    require(expected.equals(actual), description + " changed after textured draw");
  }

  private static Object field(Class<?> owner, Object instance, String name) {
    try {
      Field field = owner.getDeclaredField(name);
      field.setAccessible(true);
      return field.get(instance);
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError("Cannot read GUI fixture field " + name, exception);
    }
  }

  private static void setField(Class<?> owner, Object instance, String name, Object value) {
    try {
      Field field = owner.getDeclaredField(name);
      field.setAccessible(true);
      field.set(instance, value);
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError("Cannot set GUI fixture field " + name, exception);
    }
  }

  private static boolean near(double actual, double expected, double epsilon) {
    return Double.isFinite(actual) && Math.abs(actual - expected) <= epsilon;
  }

  private static void require(boolean condition, String description) {
    if (!condition) throw new AssertionError(description);
  }

  private record Rectangle(String name, double x, double y, double width, double height,
      double uStart, double vStart, double uEnd, double vEnd,
      boolean mirror, boolean texelCoordinates) {
    private static Rectangle texels(String name, double x, double y, double width, double height,
        double texX, double texY, boolean mirror) {
      return new Rectangle(name, x, y, width, height, texX / 256.0, texY / 256.0,
          (texX + width) / 256.0, (texY + height) / 256.0, mirror, true);
    }
  }
}
