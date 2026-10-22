package ic2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import ic2.core.event.EventHandler;
import ic2.core.item.ElectricItemAttributes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Restores the IC2 damage/fall events at the native, accepted server damage boundaries. */
@Mixin(LivingEntity.class)
public abstract class ElectricArmorDamageMixin {
  @ModifyVariable(
      method = "getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0)
  private float ic2$absorbAcceptedDamage(
      float amount, DamageSource source, float incomingAmount) {
    // Players override actuallyHurt, but share this method with LivingEntity. It is reached only
    // after shields, invulnerability, difficulty and the repeated-hit cooldown have been applied.
    LivingEntity entity = (LivingEntity) (Object) this;
    return entity.level().isClientSide() ? amount : EventHandler.onEntityAttacked(entity, source, amount);
  }

  @WrapOperation(
      method = "getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
      at = @At(
          value = "INVOKE",
          target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"))
  private float ic2$applyOrdinaryArmorOnly(
      LivingEntity entity,
      float amount,
      DamageSource source,
      float armor,
      float toughness,
      Operation<Float> original) {
    float ordinaryArmor = entity instanceof Player
        ? ElectricItemAttributes.getArmorWithoutElectricProtection(entity, armor) : armor;
    return original.call(entity, amount, source, ordinaryArmor, toughness);
  }

  @WrapOperation(
      method = "causeFallDamage(DFLnet/minecraft/world/damagesource/DamageSource;)Z",
      at = @At(
          value = "INVOKE",
          target = "Lnet/minecraft/world/entity/LivingEntity;calculateFallDamage(DF)I"))
  private int ic2$absorbNativeFall(
      LivingEntity entity,
      double effectiveDistance,
      float multiplier,
      Operation<Integer> original,
      double distance,
      float damageMultiplier,
      DamageSource source) {
    int damage = original.call(entity, effectiveDistance, multiplier);
    // The argument is the native impulse-adjusted distance. Calling the native calculation first
    // preserves safe-fall distance, fall multipliers and entity immunity before spending energy.
    if (damage > 0
        && entity.level() instanceof ServerLevel level
        && !entity.isInvulnerableTo(level, source)
        && EventHandler.onLivingFall(entity, (float) effectiveDistance)) {
      return 0;
    }
    return damage;
  }
}
