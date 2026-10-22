package ic2.core.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;

/** Keyboard modifiers for IC2 elements that are not vanilla widgets. */
public final class GuiInput {
  private GuiInput() {}
  public static KeyEvent keyEvent(int key) {
    int modifiers = 0;
    if (down(InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT)) modifiers |= InputConstants.MOD_SHIFT;
    if (down(InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL)) modifiers |= InputConstants.MOD_CONTROL;
    if (down(InputConstants.KEY_LALT, InputConstants.KEY_RALT)) modifiers |= InputConstants.MOD_ALT;
    if (down(InputConstants.KEY_LGUI, InputConstants.KEY_RGUI)) modifiers |= InputConstants.MOD_SUPER;
    return new KeyEvent(key, 0, modifiers);
  }
  public static int legacyMouseButton(int button) {
    return switch (button) {
      case InputConstants.MOUSE_BUTTON_LEFT -> 0;
      case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
      case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
      default -> button - 1;
    };
  }
  public static net.minecraft.client.input.MouseButtonEvent mouseEvent(double x, double y, int legacyButton) {
    int button = switch (legacyButton) {
      case 0 -> InputConstants.MOUSE_BUTTON_LEFT;
      case 1 -> InputConstants.MOUSE_BUTTON_RIGHT;
      case 2 -> InputConstants.MOUSE_BUTTON_MIDDLE;
      default -> legacyButton + 1;
    };
    return new net.minecraft.client.input.MouseButtonEvent(x, y,
        new net.minecraft.client.input.MouseButtonInfo(button, keyEvent(0).modifiers()));
  }
  private static boolean down(int left, int right) {
    return InputConstants.isKeyDown(left) || InputConstants.isKeyDown(right);
  }
  public static boolean hasShiftDown() { return keyEvent(0).hasShiftDown(); }
  public static boolean hasControlDown() { return keyEvent(0).hasControlDownWithQuirk(); }
  public static boolean hasAltDown() { return keyEvent(0).hasAltDown(); }
}
