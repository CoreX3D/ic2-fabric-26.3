package ic2.core.item.tool;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.ToolMaterial;

public class Ic2Hoe extends Item {
  public Ic2Hoe(ToolMaterial material, int attackDamage, float attackSpeed, Properties settings) {
    super(settings.hoe(material, attackDamage, attackSpeed));
  }
}
