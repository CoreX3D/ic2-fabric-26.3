package ic2.core;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class Ic2DamageSource {
  public static final ResourceKey<DamageType> ELECTRICITY =
      ResourceKey.create(
          Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("ic2", "electricity"));
  public static final ResourceKey<DamageType> NUKE =
      ResourceKey.create(
          Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("ic2", "nuke"));
  public static final ResourceKey<DamageType> RADIATION =
      ResourceKey.create(
          Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("ic2", "radiation"));
  public static final ResourceKey<DamageType> REACTOR_EXPLOSION =
      ResourceKey.create(
          Registries.DAMAGE_TYPE,
          Identifier.fromNamespaceAndPath("ic2", "reactor_explosion"));
  public static final ResourceKey<DamageType> HYDROGEN_EXPLOSION =
      ResourceKey.create(
          Registries.DAMAGE_TYPE,
          Identifier.fromNamespaceAndPath("ic2", "hydrogen_explosion"));

  public static DamageSource electricity;
  public static DamageSource nuke;
  public static DamageSource radiation;
  public static DamageSource reactorExplosion;
  public static DamageSource hydrogenExplosion;

  public static void init(RegistryAccess registryAccess) {
    net.minecraft.core.HolderLookup.RegistryLookup<DamageType> registry = registryAccess.lookupOrThrow(Registries.DAMAGE_TYPE);
    electricity = new DamageSource(registry.getOrThrow(ELECTRICITY));
    nuke = new DamageSource(registry.getOrThrow(NUKE));
    radiation = new DamageSource(registry.getOrThrow(RADIATION));
    reactorExplosion = new DamageSource(registry.getOrThrow(REACTOR_EXPLOSION));
    hydrogenExplosion = new DamageSource(registry.getOrThrow(HYDROGEN_EXPLOSION));
  }

  public static DamageSource radiation(Level level) {
    return level.damageSources().source(RADIATION);
  }

  public static DamageSource hydrogenExplosion(Level level) {
    return new DamageSource(
        level
            .registryAccess()
            .lookupOrThrow(Registries.DAMAGE_TYPE)
            .getOrThrow(HYDROGEN_EXPLOSION));
  }

  public static DamageSource getNukeSource(LivingEntity igniter, Level level) {
    if (igniter != null) {
      return new DamageSource(
          level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(NUKE),
          igniter);
    }
    if (nuke == null) {
      init(level.registryAccess());
    }
    return nuke;
  }

  public static DamageSource create(Level level, String name) {
    ResourceKey<DamageType> key =
        ResourceKey.create(
            Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("ic2", name));
    return new DamageSource(
        level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key));
  }
}
