package ic2.core.item.upgrade;

import ic2.core.IC2;
import ic2.core.Ic2Gui;
import ic2.core.gui.Button;
import ic2.core.gui.VanillaButton;
import ic2.core.item.tool.HandHeldInventory;
import ic2.core.util.StackUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public abstract class HandHeldUpgradeOption extends HandHeldInventory {
  protected final String name;

  protected HandHeldUpgradeOption(HandHeldAdvancedUpgrade upgradeGUI, String name) {
    super(upgradeGUI.getPlayer(), upgradeGUI.getHand(), upgradeGUI.getContainerStack(), 9);
    this.name = name;
  }

  protected CompoundTag getNBT() {
    return HandHeldAdvancedUpgrade.getTag(
        StackUtil.getOrCreateNbtData(this.containerStack), this.name);
  }

  protected void setNBT(CompoundTag settings) {
    CompoundTag root = StackUtil.getOrCreateNbtData(this.containerStack);
    root.put(this.name + "Settings", settings);
    StackUtil.setTag(this.containerStack, root);
  }

  @Environment(EnvType.CLIENT)
  Button<?> getBackButton(Ic2Gui<?> gui, int y) {
    return new VanillaButton(
            gui,
            10,
            y,
            50,
            15,
            button -> IC2.network.get(false).requestGUI(HandHeldUpgradeOption.this))
        .withText(Component.translatable("ic2.upgrade.advancedGUI.back").getString());
  }
}
