package ic2.core.item.tool;

import ic2.api.item.ElectricItem;
import ic2.api.item.IElectricItem;
import ic2.api.item.IItemHudInfo;
import ic2.api.network.INetworkItemEventListener;
import ic2.core.IC2;
import ic2.core.item.ElectricItemTooltipHandler;
import ic2.core.ref.Ic2BlockTags;
import ic2.core.ref.Ic2SoundEvents;
import ic2.core.sound.Sound;
import ic2.core.util.StackUtil;
import ic2.core.util.Util;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public abstract class ItemElectricTool extends Item
    implements IElectricItem, INetworkItemEventListener, IItemHudInfo, ic2.api.item.IPlayerDropHandler , ic2.core.item.Ic2InventoryTickItem {
  private final Collection<TagKey<Block>> effectiveBlocks;
  private final ToolMaterial toolMaterial;

  public ToolMaterial getTier() {
    return this.toolMaterial;
  }
  public double operationEnergyCost;
  public int maxCharge;
  public int transferLimit;
  public int tier;
  protected Sound idleSound;
  protected Sound startSound;
  protected Sound stopSound;
  protected boolean wasEquipped;

  protected ItemElectricTool(Properties settings, int operationEnergyCost) {
    this(settings, operationEnergyCost, ToolMaterial.IRON, Collections.emptyList());
  }

  protected ItemElectricTool(
      Properties settings,
      int operationEnergyCost,
      ToolMaterial material,
      Collection<TagKey<Block>> effectiveBlocks) {
    this(settings, 2.0F, operationEnergyCost, material, effectiveBlocks);
  }

  private ItemElectricTool(
      Properties settings,
      float attackDamage,
      int operationEnergyCost,
      ToolMaterial material,
      Collection<TagKey<Block>> effectiveBlocks) {
    super(electricToolProperties(settings, material, attackDamage, effectiveBlocks));
    this.toolMaterial = material;
    this.operationEnergyCost = operationEnergyCost;
    this.effectiveBlocks = effectiveBlocks;
  }

  private static Properties electricToolProperties(Properties settings, ToolMaterial material,
      float attackDamage, Collection<TagKey<Block>> effectiveBlocks) {
    // EU is the only wear mechanism for powered tools; vanilla weapon/tool wear must remain zero.
    settings.stacksTo(1).attributes(net.minecraft.world.item.component.ItemAttributeModifiers.builder()
        .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
            new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_DAMAGE_ID,
                attackDamage + material.attackDamageBonus(),
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
            net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
        .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
            new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_SPEED_ID, -3.0F,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
            net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
        .build());
    settings.component(net.minecraft.core.component.DataComponents.WEAPON,
        new net.minecraft.world.item.component.Weapon(0));
    return settings.delayedComponent(net.minecraft.core.component.DataComponents.TOOL, registries -> {
      var blocks = registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
      List<net.minecraft.world.item.component.Tool.Rule> rules = new java.util.ArrayList<>();
      rules.add(net.minecraft.world.item.component.Tool.Rule.deniesDrops(
          blocks.getOrThrow(material.incorrectBlocksForDrops())));
      for (TagKey<Block> tag : effectiveBlocks) {
        rules.add(net.minecraft.world.item.component.Tool.Rule.minesAndDrops(
            blocks.getOrThrow(tag), material.speed()));
      }
      return new net.minecraft.world.item.component.Tool(rules, 1.0F, 0, true);
    });
  }

  public static boolean consumeEnergy(
      ItemStack stack, double amount, int tier, LivingEntity entity) {
    if (!(stack.getItem() instanceof IElectricItem)) {
      return false;
    }

    if (!ElectricItem.manager.canUse(stack, amount)) {
      return false;
    }

    boolean isConsumed;
    if (entity == null) {
      isConsumed =
          Util.isSimilar(
              ElectricItem.manager.discharge(stack, amount, tier, true, false, false), amount);
    } else {
      isConsumed = ElectricItem.manager.use(stack, amount, entity);
    }

    if (ElectricItem.manager.getCharge(stack) <= 0.0 && entity instanceof Player player) {
      IC2.network.get(true).initiateItemEvent(player, stack, 0, true);
    }

    return isConsumed;
  }

  @Override
  public void onNetworkEvent(ItemStack stack, Player player, int event) {
    player.playSound(this.getShutdownSound(), 1.0F, 1.0F);
  }

  public boolean consumeEnergy(ItemStack stack, double amount, LivingEntity entity) {
    return consumeEnergy(stack, amount, this.tier, entity);
  }

  @Override
  public List<String> getHudInfo(ItemStack stack, boolean advanced) {
    List<String> info = new LinkedList<>();
    info.add(ElectricItem.manager.getToolTip(stack));
    info.add(Component.translatable("ic2.item.tooltip.power_tier", this.tier).getString());
    return info;
  }

  public InteractionResult useOn(UseOnContext context) {
    ElectricItem.manager.use(context.getItemInHand(), 0.0, context.getPlayer());
    return super.useOn(context);
  }

  public InteractionResult use(Level world, Player player, InteractionHand hand) {
    ElectricItem.manager.use(StackUtil.get(player, hand), 0.0, player);
    return super.use(world, player, hand);
  }

  public float getDestroySpeed(ItemStack stack, BlockState state) {
    return this.isEffective(state) && ElectricItem.manager.canUse(stack, this.operationEnergyCost)
        ? this.getTier().speed()
        : 1.0F;
  }

  public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
    // 1.21: ToolMaterial#getLevel() is gone; harvestability is expressed via the tier's "incorrect blocks"
    // tag.
    return !state.is(this.getTier().incorrectBlocksForDrops()) && this.isEffective(state);
  }

  private boolean isEffective(BlockState state) {
    for (TagKey<Block> tag : this.effectiveBlocks) {
      if (state.is(tag)) {
        return true;
      }
    }

    return false;
  }

  public void hurtEnemy(
      ItemStack itemstack, LivingEntity entityliving, LivingEntity entityliving1) {
    super.hurtEnemy(itemstack, entityliving, entityliving1);
  }

  public int getEnchantmentValue() {
    return 0;
  }

  @Override
  public boolean canProvideEnergy(ItemStack stack) {
    return false;
  }

  @Override
  public double getMaxCharge(ItemStack stack) {
    return this.maxCharge;
  }

  @Override
  public int getTier(ItemStack stack) {
    return this.tier;
  }

  @Override
  public double getTransferLimit(ItemStack stack) {
    return this.transferLimit;
  }

  public boolean mineBlock(
      ItemStack stack, Level world, BlockState state, BlockPos pos, LivingEntity user) {
    if (state.getDestroySpeed(world, pos) != 0.0F) {
      this.consumeEnergy(stack, this.operationEnergyCost, user);
    }

    return true;
  }

  public boolean isEnchantable(ItemStack stack) {
    return false;
  }

  public void appendHoverText(
      ItemStack stack, Item.TooltipContext world, net.minecraft.world.item.component.TooltipDisplay tooltipDisplay,
      java.util.function.Consumer<Component> tooltipOutput, TooltipFlag context) {
    List<Component> tooltip = new java.util.ArrayList<>();
    try {
    ElectricItemTooltipHandler.addTooltip(stack, tooltip);
  
    } finally {
      tooltip.forEach(tooltipOutput);
    }
  }

  protected ItemStack getItemStack(double charge) {
    ItemStack ret = new ItemStack(this);
    ElectricItem.manager.charge(ret, charge, Integer.MAX_VALUE, true, false);
    return ret;
  }

  public void tickInventory(ItemStack itemstack, Level world, Entity entity, int i, boolean flag) {
    boolean isEquipped = flag && entity instanceof LivingEntity;
    if (IC2.sideProxy.isRendering()) {
      if (isEquipped) {
        boolean hasPower = ElectricItem.manager.canUse(itemstack, 1.0);

        if (!this.wasEquipped) {
          if (hasPower) {
            this.initSound((LivingEntity) entity, itemstack);
            if (this.idleSound != null) {
              this.idleSound.play();
            }

            if (this.startSound != null) {
              this.startSound.playOnce();
            }
          }
        } else if (this.idleSound != null) {
          if (hasPower && !this.idleSound.isPlaying()) {
            this.idleSound.play();
          } else if (!hasPower) {
            if (this.stopSound != null) {
              this.stopSound.playOnce();
            }

            this.clearSound((LivingEntity) entity);
          }
        }
      } else if (this.idleSound != null && entity instanceof LivingEntity theEntity) {
        ItemStack stack = theEntity.getItemBySlot(EquipmentSlot.MAINHAND);
        if (stack.getItem() != this || stack == itemstack) {
          if (this.stopSound != null) {
            this.stopSound.playOnce();
          }

          this.clearSound(theEntity);
        }
      }

      this.wasEquipped = isEquipped;
    }
  }

  protected void initSound(LivingEntity entity, ItemStack stack) {
    SoundEvent idleEvent;
    if (this.idleSound == null && (idleEvent = this.getIdleSound(entity, stack)) != null) {
      this.idleSound =
          IC2.soundManager.createSound(entity, idleEvent, SoundSource.PLAYERS, entity, 1.0F, 1.0F);
      this.idleSound.setSourceItem(this);
    }

    SoundEvent startEvent;
    if (this.startSound == null && (startEvent = this.getStartSound(entity, stack)) != null) {
      this.startSound =
          IC2.soundManager.createSound(entity, startEvent, SoundSource.PLAYERS, entity, 1.0F, 1.0F);
    }

    SoundEvent stopEvent;
    if (this.stopSound == null && (stopEvent = this.getStopSound(entity, stack)) != null) {
      this.stopSound =
          IC2.soundManager.createSound(entity, stopEvent, SoundSource.PLAYERS, entity, 1.0F, 1.0F);
    }
  }

  protected void clearSound(LivingEntity entity) {
    if (this.idleSound != null) {
      IC2.soundManager.removeSound(entity, this.idleSound);
      this.idleSound = null;
    }

    if (this.startSound != null) {
      IC2.soundManager.removeSound(entity, this.startSound);
      this.startSound = null;
    }

    if (this.stopSound != null) {
      IC2.soundManager.removeSound(entity, this.stopSound);
      this.stopSound = null;
    }
  }

  public boolean onDroppedByPlayer(ItemStack stack, Player player) {
    this.clearSound(player);
    return true;
  }

  protected SoundEvent getIdleSound(LivingEntity player, ItemStack stack) {
    return null;
  }

  protected SoundEvent getStopSound(LivingEntity player, ItemStack stack) {
    return null;
  }

  protected SoundEvent getStartSound(LivingEntity player, ItemStack stack) {
    return null;
  }

  public SoundEvent getShutdownSound() {
    return Ic2SoundEvents.ITEM_ELECTRIC_SHUTDOWN;
  }

  public boolean isBarVisible(ItemStack stack) {
    return true;
  }

  public int getBarWidth(ItemStack stack) {
    return (int) Math.round(ElectricItem.manager.getChargeLevel(stack) * 13.0);
  }

  public int getBarColor(ItemStack stack) {
    return Mth.hsvToRgb((float) (ElectricItem.manager.getChargeLevel(stack) / 3.0), 1.0F, 1.0F);
  }

  public boolean canUse(ItemStack stack) {
    return ElectricItem.manager.canUse(stack, this.operationEnergyCost);
  }
  @Override
  public void inventoryTick(net.minecraft.world.item.ItemStack stack,
      net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity entity,
      net.minecraft.world.entity.EquipmentSlot equipmentSlot) {
    int inventorySlot = ic2.core.item.Ic2InventoryTickItem.findInventorySlot(entity, stack);
    this.tickInventory(stack, level, entity, inventorySlot,
        equipmentSlot == net.minecraft.world.entity.EquipmentSlot.MAINHAND);
  }


}
