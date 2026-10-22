package ic2.core.ref;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ToolMaterial;

public final class Ic2ToolMaterials {
  public static final ToolMaterial BRONZE = new ToolMaterial(
      BlockTags.INCORRECT_FOR_IRON_TOOL, 350, 6.0F, 2.0F, 14,
      TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("ic2", "repairs_bronze_tools")));
  public static final ToolMaterial CHAINSAW = new ToolMaterial(
      BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 250, 12.0F, 9.0F, 14,
      TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("ic2", "repairs_electric_tools")));

  private Ic2ToolMaterials() {}
}
