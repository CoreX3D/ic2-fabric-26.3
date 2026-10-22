package ic2.core;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public class Ic2Potion extends MobEffect {
  public static Ic2Potion radiation;

  public Ic2Potion(MobEffectCategory type, int liquidColor) {
    super(type, liquidColor);
  }

  /**
   * Returns the registry Holder for the radiation effect. In 1.21 the mob-effect system is
   * Holder-based, so effect look-ups/additions require the Holder rather than the raw {@link
   * MobEffect} instance.
   */
  public static Holder<MobEffect> radiationHolder() {
    return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(radiation);
  }

  @Override
  public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
    if (this == radiation) {
      // The original IC2 dose uses integer division: amplifier 90 is 0.5 HP, 100 is 1.5 HP.
      // Resolve the source from this level instead of retaining a registry holder from another world.
      entity.hurtServer(level, Ic2DamageSource.radiation(level), amplifier / 100 + 0.5F);
    }

    return true;
  }

  @Override
  public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
    if (this == radiation) {
      int rate = 25 >> amplifier;
      return rate > 0 ? duration % rate == 0 : true;
    } else {
      return false;
    }
  }

  public void applyTo(LivingEntity entity, int duration, int amplifier) {
    MobEffectInstance effect = new MobEffectInstance(radiationHolder(), duration, amplifier);
    entity.addEffect(effect);
  }
}
