package ic2.core.ref;

import ic2.core.IC2;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

public final class Ic2FluidTags {
  public static final TagKey<Fluid> STEAM = create();

  public static void init() {}

  private static TagKey<Fluid> create() {
    Identifier id =
        Identifier.parse(
            IC2.envProxy.isFabricEnv() ? "c:steam" : Ic2ItemTags.toCommon("forge:steam"));
    return TagKey.create(Registries.FLUID, id);
  }
}
