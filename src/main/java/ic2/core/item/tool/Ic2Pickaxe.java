package ic2.core.item.tool;

import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

public class Ic2Pickaxe extends Item {
  public Ic2Pickaxe(ToolMaterial material, int attackDamage, float attackSpeed, Properties settings) {
    super(settings.pickaxe(material, attackDamage, attackSpeed));
  }
}
