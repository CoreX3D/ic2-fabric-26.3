package ic2.core.item.armor;

import ic2.api.item.ElectricItem;
import ic2.api.item.HudMode;
import ic2.api.item.IHazmatLike;
import ic2.api.item.IItemHudProvider;
import ic2.core.IC2;
import ic2.core.Ic2Potion;
import ic2.core.init.IC2ClientConfig;
import ic2.core.item.ItemTinCan;
import ic2.core.item.armor.jetpack.IJetpack;
import ic2.core.ref.Ic2Items;
import ic2.core.util.Ic2Tooltip;
import ic2.core.util.KeyboardClient;
import ic2.core.util.StackUtil;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class ItemArmorQuantumSuit extends ItemArmorElectric
    implements IJetpack, IHazmatLike, IItemHudProvider , ic2.core.item.Ic2InventoryTickItem {
  public static final int[] CHARGED_PROTECTION = new int[] {3, 6, 8, 3};
  protected static final Map<Holder<MobEffect>, Integer> potionRemovalCost =
      new IdentityHashMap<>();
  private static boolean potionRemovalCostInit = false;

  // Populated lazily: the radiation Holder is only resolvable once the mob-effect
  // registry has been populated, which is guaranteed by the time this runs at tick time.
  private static Map<Holder<MobEffect>, Integer> potionRemovalCost() {
    if (!potionRemovalCostInit) {
      potionRemovalCostInit = true;
      potionRemovalCost.put(MobEffects.POISON, 10000);
      potionRemovalCost.put(Ic2Potion.radiationHolder(), 10000);
      potionRemovalCost.put(MobEffects.WITHER, 25000);
    }

    return potionRemovalCost;
  }

  private static int getPotionRemovalCost(
      Holder<MobEffect> potion, MobEffectInstance effect, int baseCost) {
    if (potion.value() == Ic2Potion.radiation) {
      // IC2 radiation uses high amplifier values for damage scaling, not vanilla potion levels.
      return baseCost + effect.getAmplifier() * 100;
    }

    return baseCost * (effect.getAmplifier() + 1);
  }

  private static final Map<Player, Float> jumpCharges = new WeakHashMap<>();

  public ItemArmorQuantumSuit(
      Holder<ArmorMaterial> material, EquipmentSlot armorType, Properties settings) {
    super(material, armorType, settings.rarity(Rarity.RARE), 1.0E7, 12000.0, 4);
  }

  @Override
  public int getEnergyPerDamage() {
    return 20000;
  }

  @Override
  public double getDamageAbsorptionRatio() {
    return this.getEquipmentSlot() == EquipmentSlot.CHEST ? 1.2 : 1.0;
  }

  public boolean hasCustomColor(@NotNull ItemStack stack) {
    return stack.has(DataComponents.DYED_COLOR) || this.getLegacyColor(stack) != -1;
  }

  public void clearColor(@NotNull ItemStack stack) {
    stack.remove(DataComponents.DYED_COLOR);
    removeLegacyColor(stack);
  }

  public int getColor(@NotNull ItemStack stack) {
    DyedItemColor color = stack.get(DataComponents.DYED_COLOR);
    return color == null ? this.getLegacyColor(stack) : color.rgb();
  }

  public void setColor(@NotNull ItemStack stack, int color) {
    stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
    removeLegacyColor(stack);
  }

  private static void removeLegacyColor(ItemStack stack) {
    CompoundTag existing = StackUtil.getTag(stack);
    if (existing == null || !existing.getCompoundOrEmpty("display").contains("color")) {
      return;
    }
    StackUtil.updateNbtData(stack, root -> {
      CompoundTag display = root.getCompoundOrEmpty("display");
      display.remove("color");
      if (display.isEmpty()) root.remove("display");
      else root.put("display", display);
    });
  }

  private int getLegacyColor(ItemStack stack) {
    CompoundTag nbt = StackUtil.getTag(stack);
    return nbt == null ? -1 : nbt.getCompoundOrEmpty("display").getIntOr("color", -1);
  }

  @Override
  public boolean addsProtection(LivingEntity entity, EquipmentSlot slot, ItemStack stack) {
    return ElectricItem.manager.getCharge(stack) > 0.0;
  }

  public boolean absorbFall(ItemStack stack, float distance) {
    int fallDamage = Math.max((int) distance - 10, 0);
    double energyCost = this.getEnergyPerDamage() * fallDamage;
    if (energyCost > ElectricItem.manager.getCharge(stack)) {
      return false;
    }

    ElectricItem.manager.discharge(stack, energyCost, Integer.MAX_VALUE, true, false, false);
    return true;
  }

  public int getEnchantmentValue() {
    return 0;
  }

  @Override
  @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
  public void appendHoverText(
      ItemStack stack, Item.TooltipContext world, net.minecraft.world.item.component.TooltipDisplay tooltipDisplay,
      java.util.function.Consumer<Component> tooltipOutput, TooltipFlag context) {
    List<Component> tooltip = new java.util.ArrayList<>();
    try {
    super.appendHoverText(stack, world, tooltipDisplay, tooltip::add, context);
    if (this.getEquipmentSlot() == EquipmentSlot.HEAD) {
      Ic2Tooltip.add(
          tooltip,
          Component.translatable(
              "item.ic2.tooltip.night_vision.toggle",
              KeyboardClient.altKey.getTranslatedKeyMessage(),
              KeyboardClient.modeSwitchKey.getTranslatedKeyMessage()));
    }
    if (this.getEquipmentSlot() == EquipmentSlot.CHEST) {
      Ic2Tooltip.add(
          tooltip,
          Component.translatable(
              "item.ic2.tooltip.jetpack.toggle",
              Minecraft.getInstance().options.keyJump.getTranslatedKeyMessage(),
              KeyboardClient.modeSwitchKey.getTranslatedKeyMessage()));
    }
    if (this.getEquipmentSlot() == EquipmentSlot.LEGS) {
      Ic2Tooltip.add(
          tooltip,
          Component.translatable(
              "item.ic2.tooltip.speed.toggle",
              KeyboardClient.altKey.getTranslatedKeyMessage(),
              Minecraft.getInstance().options.keyShift.getTranslatedKeyMessage()));
    }
    if (this.getEquipmentSlot() == EquipmentSlot.FEET) {
      Ic2Tooltip.add(
          tooltip,
          Component.translatable(
              "item.ic2.tooltip.jump.toggle",
              KeyboardClient.altKey.getTranslatedKeyMessage(),
              Minecraft.getInstance().options.keyJump.getTranslatedKeyMessage()));
    }
  
    } finally {
      tooltip.forEach(tooltipOutput);
    }
  }

  @Override
  public void tickInventory(
      @NotNull ItemStack stack,
      @NotNull Level world,
      @NotNull Entity entity,
      int slot,
      boolean selected) {
    // Upgrade legacy custom NBT once; modern dye crafting and equipment rendering share DYED_COLOR.
    if (!world.isClientSide() && this.getLegacyColor(stack) != -1) {
      if (!stack.has(DataComponents.DYED_COLOR)) {
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(this.getLegacyColor(stack)));
      }
      removeLegacyColor(stack);
    }
    if (!(entity instanceof Player player)) {
      return;
    }

    if (player.getItemBySlot(this.getEquipmentSlot()) != stack) {
      return;
    }

    CompoundTag nbtData = StackUtil.getOrCreateNbtData(stack);
    byte toggleTimer = nbtData.getByteOr("toggle_timer", (byte) 0);
    boolean ret = false;
    EquipmentSlot equipSlot = this.getEquipmentSlot();

    if (equipSlot == EquipmentSlot.HEAD) {
      int air = player.getAirSupply();
      if (ElectricItem.manager.canUse(stack, 1000.0) && air < player.getMaxAirSupply()) {
        player.setAirSupply(air + 200);
        ElectricItem.manager.use(stack, 1000.0, player);
        ret = true;
      } else if (air <= 0) {
        IC2.grantAdvancement(
            player,
            "ic2/build_generator/build_compressor/build_nano_suit/build_quantum_suits/starve_with_q_helmet");
      }

      if (ElectricItem.manager.canUse(stack, 1000.0) && player.getFoodData().needsFood()) {
        int foodSlot = -1;

        for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
          ItemStack invStack = player.getInventory().getNonEquipmentItems().get(i);
          if (!StackUtil.isEmpty(invStack) && invStack.getItem() == Ic2Items.FILLED_TIN_CAN) {
            foodSlot = i;
            break;
          }
        }

        if (foodSlot > -1) {
          ItemStack foodStack = player.getInventory().getNonEquipmentItems().get(foodSlot);
          ItemTinCan can = (ItemTinCan) foodStack.getItem();
          InteractionResult result = can.onEaten(player, foodStack);
          if (result instanceof InteractionResult.Success success) {
            player.getInventory().getNonEquipmentItems().set(foodSlot, success.heldItemTransformedTo());
            ElectricItem.manager.use(stack, 1000.0, player);
          }

          ret = true;
        }
      } else if (player.getFoodData().getFoodLevel() <= 0) {
        IC2.grantAdvancement(
            player,
            "ic2/build_generator/build_compressor/build_nano_suit/build_quantum_suits/starve_with_q_helmet");
      }

      if (IC2.sideProxy.isSimulating()) {
        for (MobEffectInstance effect : new LinkedList<>(player.getActiveEffects())) {
          Holder<MobEffect> potion = effect.getEffect();
          Integer baseCost = potionRemovalCost().get(potion);
          if (baseCost != null) {
            int cost = getPotionRemovalCost(potion, effect, baseCost);
            if (ElectricItem.manager.canUse(stack, cost)) {
              ElectricItem.manager.use(stack, cost, player);
              player.removeEffect(potion);
            }
          }
        }
      }

      boolean Nightvision = nbtData.getBooleanOr("night_vision", false);
      short hudmode = nbtData.getShortOr("hud_mode", (short) 0);
      if (IC2.keyboard.isAltKeyDown(player)
          && IC2.keyboard.isModeSwitchKeyDown(player)
          && toggleTimer == 0) {
        toggleTimer = 10;
        Nightvision = !Nightvision;
        if (IC2.sideProxy.isSimulating()) {
          nbtData.putBoolean("night_vision", Nightvision);
          ic2.core.item.ItemNbt.commitFields(stack, nbtData, "night_vision");
          if (Nightvision) {
            IC2.sideProxy.messagePlayer(player, "ic2.night_vision.mode.enabled");
          } else {
            IC2.sideProxy.messagePlayer(player, "ic2.night_vision.mode.disabled");
          }
        }
      }

      if (IC2.keyboard.isAltKeyDown(player)
          && IC2.keyboard.isHudModeKeyDown(player)
          && toggleTimer == 0) {
        toggleTimer = 10;
        if (hudmode == HudMode.getMaxMode()) {
          hudmode = 0;
        } else {
          hudmode++;
        }

        if (IC2.sideProxy.isSimulating()) {
          nbtData.putShort("hud_mode", hudmode);
          ic2.core.item.ItemNbt.commitFields(stack, nbtData, "hud_mode");
          IC2.sideProxy.messagePlayer(
              player,
              HudMode.getFromID(hudmode).getTranslationKey());
        }
      }

      if (IC2.sideProxy.isSimulating() && toggleTimer > 0) {
        nbtData.putByte("toggle_timer", --toggleTimer);
        ic2.core.item.ItemNbt.commitFields(stack, nbtData, "toggle_timer");
      }

      if (Nightvision
          && IC2.sideProxy.isSimulating()
          && ElectricItem.manager.use(stack, 1.0, player)) {
        BlockPos pos = BlockPos.containing(player.position());
        int skylight = player.level().getMaxLocalRawBrightness(pos);
        ItemArmorNanoSuit.affectPlayer(player, skylight);

        ret = true;
      }

    } else if (equipSlot == EquipmentSlot.CHEST) {
      player.clearFire();
    } else if (equipSlot == EquipmentSlot.LEGS) {
      boolean speedEnabled =
          !nbtData.contains("speed_enabled") || nbtData.getBooleanOr("speed_enabled", false);
      boolean speedTogglePressed =
          IC2.keyboard.isAltKeyDown(player) && IC2.keyboard.isSneakKeyDown(player);
      boolean speedToggleWasPressed = nbtData.getBooleanOr("speed_toggle_pressed", false);
      if (speedTogglePressed && !speedToggleWasPressed) {
        speedEnabled = !speedEnabled;
        if (IC2.sideProxy.isSimulating()) {
          nbtData.putBoolean("speed_enabled", speedEnabled);
          ic2.core.item.ItemNbt.commitFields(stack, nbtData, "speed_enabled");
          if (speedEnabled) {
            IC2.sideProxy.messagePlayer(player, "ic2.speed.mode.enabled");
          } else {
            IC2.sideProxy.messagePlayer(player, "ic2.speed.mode.disabled");
          }
        }
      }

      nbtData.putBoolean("speed_toggle_pressed", speedTogglePressed);
      ic2.core.item.ItemNbt.commitFields(stack, nbtData, "speed_toggle_pressed");

      if (speedEnabled) {
        boolean enableQuantumSpeedOnSprint;
        if (IC2.sideProxy.isRendering()) {
          enableQuantumSpeedOnSprint = IC2ClientConfig.misc.quantumSpeedOnSprint.get();
        } else {
          enableQuantumSpeedOnSprint = true;
        }

        if (ElectricItem.manager.canUse(stack, 1000.0)
            && (player.onGround() || player.isInWater())
            && IC2.keyboard.isForwardKeyDown(player)
            && (enableQuantumSpeedOnSprint && player.isSprinting()
                || !enableQuantumSpeedOnSprint && IC2.keyboard.isBoostKeyDown(player))) {
          byte speedTicker = nbtData.getByteOr("speed_ticker", (byte) 0);
          if (++speedTicker >= 10) {
            speedTicker = 0;
            ElectricItem.manager.use(stack, 1000.0, player);
            ret = true;
          }

          nbtData.putByte("speed_ticker", speedTicker);
          ic2.core.item.ItemNbt.commitFields(stack, nbtData, "speed_ticker");
          float speed = 0.22F;
          if (player.isInWater()) {
            speed = 0.1F;
            if (IC2.keyboard.isJumpKeyDown(player)) {
              Vec3 motion = player.getDeltaMovement();
              player.setDeltaMovement(motion.x, motion.y + 0.1F, motion.z);
            }
          }

          float yawRad = player.getYRot() * (float) Math.PI / 180.0F;
          Vec3 motion = player.getDeltaMovement();
          player.setDeltaMovement(
              motion.x + (-Math.sin(yawRad) * speed),
              motion.y,
              motion.z + (Math.cos(yawRad) * speed));
        }
      }

    } else if (equipSlot == EquipmentSlot.FEET) {
      boolean jumpEnabled = !nbtData.contains("jump_enabled") || nbtData.getBooleanOr("jump_enabled", false);
      if (IC2.keyboard.isAltKeyDown(player)
          && IC2.keyboard.isJumpKeyDown(player)
          && toggleTimer == 0) {
        toggleTimer = 10;
        jumpEnabled = !jumpEnabled;
        if (IC2.sideProxy.isSimulating()) {
          nbtData.putBoolean("jump_enabled", jumpEnabled);
          ic2.core.item.ItemNbt.commitFields(stack, nbtData, "jump_enabled");
          if (jumpEnabled) {
            IC2.sideProxy.messagePlayer(player, "ic2.jump.mode.enabled");
          } else {
            IC2.sideProxy.messagePlayer(player, "ic2.jump.mode.disabled");
          }
        }
      }

      if (IC2.sideProxy.isSimulating() && toggleTimer > 0) {
        nbtData.putByte("toggle_timer", --toggleTimer);
        ic2.core.item.ItemNbt.commitFields(stack, nbtData, "toggle_timer");
      }

      if (jumpEnabled) {
        if (IC2.sideProxy.isSimulating()) {
          boolean wasOnGround = !nbtData.contains("on_ground") || nbtData.getBooleanOr("on_ground", false);
          if (wasOnGround
              && !player.onGround()
              && IC2.keyboard.isJumpKeyDown(player)
              && IC2.keyboard.isBoostKeyDown(player)) {
            ElectricItem.manager.use(stack, 4000.0, player);
            ret = true;
          }

          if (player.onGround() != wasOnGround) {
            nbtData.putBoolean("on_ground", player.onGround());
            ic2.core.item.ItemNbt.commitFields(stack, nbtData, "on_ground");
          }
        }

        if (ElectricItem.manager.canUse(stack, 4000.0) && player.onGround()) {
          jumpCharges.put(player, 1.0F);
        }

        float jumpCharge = jumpCharges.getOrDefault(player, 0.0F);
        if (player.getDeltaMovement().y >= 0.0 && jumpCharge > 0.0F && !player.isInWater()) {
          if (IC2.keyboard.isJumpKeyDown(player) && IC2.keyboard.isBoostKeyDown(player)) {
            if (jumpCharge == 1.0F) {
              Vec3 motion = player.getDeltaMovement();
              player.setDeltaMovement(motion.x * 3.5, motion.y, motion.z * 3.5);
            }

            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, motion.y + jumpCharge * 0.3F, motion.z);
            jumpCharges.put(player, jumpCharge * 0.75F);
          } else if (jumpCharge < 1.0F) {
            jumpCharges.put(player, 0.0F);
          }
        }
      }
    }

    if (ret) {
      player.inventoryMenu.broadcastChanges();
    }
  }

  @Override
  public void drainEnergy(ItemStack pack, int amount) {
    ElectricItem.manager.discharge(pack, amount + 6, Integer.MAX_VALUE, true, false, false);
  }

  @Override
  public float getPower(ItemStack stack) {
    return 1.0F;
  }

  @Override
  public float getDropPercentage(ItemStack stack) {
    return 0.05F;
  }

  @Override
  public double getChargeLevel(ItemStack stack) {
    return ElectricItem.manager.getCharge(stack) / this.getMaxCharge(stack);
  }

  @Override
  public boolean isJetpackActive(ItemStack stack) {
    return true;
  }

  @Override
  public float getHoverMultiplier(ItemStack stack, boolean upwards) {
    return 0.1F;
  }

  @Override
  public float getWorldHeightDivisor(ItemStack stack) {
    return 0.9F;
  }

  @Override
  public boolean doesProvideHUD(ItemStack stack) {
    return this.getEquipmentSlot() == EquipmentSlot.HEAD
        && ElectricItem.manager.getCharge(stack) > 0.0;
  }

  @Override
  public HudMode getHudMode(ItemStack stack) {
    return HudMode.getFromID(StackUtil.getOrCreateNbtData(stack).getShortOr("hud_mode", (short) 0));
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
