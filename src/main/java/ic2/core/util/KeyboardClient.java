package ic2.core.util;

import ic2.core.IC2;
import ic2.core.proxy.SideProxyClient;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class KeyboardClient extends Keyboard {
  private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(IC2.getIdentifier("controls"));
  public static final KeyMapping altKey = new KeyMapping("ic2.keyboard.alt_key", com.mojang.blaze3d.platform.InputConstants.KEY_LALT, CATEGORY);
  public static final KeyMapping boostKey =
      new KeyMapping("ic2.keyboard.boost_key", com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL, CATEGORY);
  public static final KeyMapping modeSwitchKey =
      new KeyMapping("ic2.keyboard.mode_switch_key", com.mojang.blaze3d.platform.InputConstants.KEY_M, CATEGORY);
  public static final KeyMapping sideInventoryKey =
      new KeyMapping("ic2.keyboard.side_inventory_key", com.mojang.blaze3d.platform.InputConstants.KEY_C, CATEGORY);
  public static final KeyMapping expandInfo =
      new KeyMapping("ic2.keyboard.hub_expand_key", com.mojang.blaze3d.platform.InputConstants.KEY_X, CATEGORY);
  private static boolean registeredKeys = false;
  private final Minecraft mc = Minecraft.getInstance();
  private int lastKeyState = 0;

  public KeyboardClient() {
    if (!registeredKeys) {
      registeredKeys = true;
      SideProxyClient.envProxy.registerKeyBinding(this.altKey);
      SideProxyClient.envProxy.registerKeyBinding(this.boostKey);
      SideProxyClient.envProxy.registerKeyBinding(this.modeSwitchKey);
      SideProxyClient.envProxy.registerKeyBinding(this.sideInventoryKey);
      SideProxyClient.envProxy.registerKeyBinding(this.expandInfo);
    }
  }

  @Override
  public void sendKeyUpdate() {
    Set<Keyboard.Key> keys = EnumSet.noneOf(Keyboard.Key.class);
    Screen currentScreen = SideProxyClient.mc.gui.screen();
    if (currentScreen == null) {
      if (this.altKey.isDown()) {
        keys.add(Keyboard.Key.alt);
      }

      if (this.boostKey.isDown()) {
        keys.add(Keyboard.Key.boost);
      }

      if (this.mc.options.keyUp.isDown()) {
        keys.add(Keyboard.Key.forward);
      }

      if (this.modeSwitchKey.isDown()) {
        keys.add(Keyboard.Key.modeSwitch);
      }

      if (this.mc.options.keyJump.isDown()) {
        keys.add(Keyboard.Key.jump);
      }

      if (this.sideInventoryKey.isDown()) {
        keys.add(Keyboard.Key.sideInventory);
      }

      if (this.expandInfo.isDown()) {
        keys.add(Keyboard.Key.hubMode);
      }

      for (Keyboard.IKeyWatcher watcher : this.watchers) {
        watcher.checkForKey(keys);
      }
    }

    int currentKeyState = Keyboard.Key.toInt(keys);
    if (currentKeyState != this.lastKeyState) {
      IC2.network.get(false).initiateKeyUpdate(currentKeyState);
      super.processKeyUpdate(IC2.sideProxy.getPlayerInstance(), currentKeyState);
      this.lastKeyState = currentKeyState;
    }
  }
}
