package ic2.core.item;

import ic2.core.profile.NotClassic;
import ic2.core.util.Ic2Tooltip;
import ic2.core.util.StackUtil;
import ic2.core.util.Util;
import ic2.core.uu.UuIndex;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

@NotClassic
public class ItemCrystalMemory extends Item {
  public ItemCrystalMemory(Properties settings) {
    super(settings);
  }

  public void appendHoverText(
      @NotNull ItemStack stack,
      Item.TooltipContext world,
      net.minecraft.world.item.component.TooltipDisplay tooltipDisplay,
      java.util.function.Consumer<Component> tooltipOutput,
      @NotNull TooltipFlag advanced) {
    List<Component> tooltip = new java.util.ArrayList<>();
    try {
    ItemStack recorded = this.readItemStack(stack, world.registries());
    if (!StackUtil.isEmpty(recorded)) {
      Ic2Tooltip.add(
          tooltip,
          Component.translatable(
              "item.ic2.crystal_memory.tooltip.item",
              recorded.getHoverName()));
      Ic2Tooltip.add(
          tooltip,
          Component.translatable(
              "item.ic2.crystal_memory.tooltip.uu_matter",
              Util.toSiString(UuIndex.instance.getInBuckets(recorded), 4)));
    } else {
      Ic2Tooltip.add(tooltip, Component.translatable("item.ic2.crystal_memory.tooltip.empty"));
    }
  
    } finally {
      tooltip.forEach(tooltipOutput);
    }
  }

  public ItemStack readItemStack(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
    CompoundTag nbt = StackUtil.getOrCreateNbtData(stack);
    CompoundTag contentTag = nbt.getCompoundOrEmpty("Pattern");
    return ic2.core.block.persistence.LegacyNbt.readStack(registries, contentTag);
  }

  public void writeContentsTag(ItemStack stack, ItemStack recorded, net.minecraft.core.HolderLookup.Provider registries) {
    CompoundTag nbt = StackUtil.getOrCreateNbtData(stack);
    nbt.put("Pattern", ic2.core.block.persistence.LegacyNbt.writeStack(registries, recorded));
    StackUtil.setTag(stack, nbt);
  }
}
