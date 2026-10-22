package ic2.core.slot;

import com.mojang.datafixers.util.Pair;
import ic2.core.util.StackUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class SlotArmor extends Slot {
  private static final Identifier[] EMPTY_ARMOR_SLOT_TEXTURES =
      new Identifier[] {
        InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS,
        InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
        InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
        InventoryMenu.EMPTY_ARMOR_SLOT_HELMET
      };
  private final EquipmentSlot armorType;

  public SlotArmor(Inventory inventory, EquipmentSlot armorType, int x, int y) {
    super(inventory, 36 + armorType.getIndex(), x, y);
    this.armorType = armorType;
  }

  public boolean mayPlace(ItemStack stack) {
    Item item = stack.getItem();
    return item == null ? false : StackUtil.getEquipmentSlotForItem(stack) == this.armorType;
  }

  public Identifier getNoItemIcon() {
    return EMPTY_ARMOR_SLOT_TEXTURES[this.armorType.getIndex()];
  }
}
