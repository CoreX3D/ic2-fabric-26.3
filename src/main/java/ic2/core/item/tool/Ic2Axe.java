package ic2.core.item.tool;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.ToolMaterial;

public class Ic2Axe extends Item {
  public Ic2Axe(ToolMaterial material, float attackDamage, float attackSpeed, Properties settings) {
    super(settings.axe(material, attackDamage, attackSpeed));
  }
}
