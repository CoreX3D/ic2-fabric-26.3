package ic2.fabric;

import ic2.core.IC2;
import ic2.core.proxy.EnvProxy;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Adds IC2's placed features to biomes.
 *
 * <p>NeoForge loaded the four files under {@code data/ic2/neoforge/biome_modifier/}. Those use the
 * {@code neoforge:add_features} modifier type, which does not exist on Fabric, so the same four
 * injections are registered here through {@code fabric-biome-api-v1}:
 *
 * <ul>
 *   <li>the seven ore features into {@code #minecraft:is_overworld} at {@code underground_ores},
 *   <li>{@code trees_rubber_jungle} into {@code #minecraft:is_jungle},
 *   <li>{@code trees_rubber_forest} into {@code #minecraft:is_forest},
 *   <li>{@code trees_rubber_swamp} into {@code #c:is_swamp},
 * </ul>
 *
 * all at {@code vegetal_decoration}.
 */
public final class FabricBiomeModifications {
  private record Addition(Identifier feature, TagKey<Biome> biomeTag, Decoration step) {}
  private static final Set<Addition> REGISTERED = new HashSet<>();
  private static final TagKey<Biome> IS_SWAMP =
      TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_swamp"));

  private FabricBiomeModifications() {}

  public static void register() {
    for (String ore :
        new String[] {
          "ore_lead",
          "ore_lead_lower",
          "ore_tin_upper",
          "ore_tin_small",
          "ore_uranium",
          "ore_uranium_buried",
          "ore_uranium_large"
        }) {
      add(BiomeTags.IS_OVERWORLD, Decoration.UNDERGROUND_ORES, ore);
    }

    add(BiomeTags.IS_JUNGLE, Decoration.VEGETAL_DECORATION, "trees_rubber_jungle");
    add(BiomeTags.IS_FOREST, Decoration.VEGETAL_DECORATION, "trees_rubber_forest");
    add(IS_SWAMP, Decoration.VEGETAL_DECORATION, "trees_rubber_swamp");
  }

  private static void add(TagKey<Biome> biomeTag, Decoration step, String placedFeaturePath) {
    add(biomeTag, step, IC2.getIdentifier(placedFeaturePath));
  }

  public static void add(Identifier feature, EnvProxy.BiomeSelector selector, Decoration step) {
    TagKey<Biome> biomeTag = switch (selector) {
      case OVERWORLD -> BiomeTags.IS_OVERWORLD;
      case SWAMP -> IS_SWAMP;
      case JUNGLE -> BiomeTags.IS_JUNGLE;
      case FOREST -> BiomeTags.IS_FOREST;
    };
    add(biomeTag, step, feature);
  }

  private static void add(TagKey<Biome> biomeTag, Decoration step, Identifier feature) {
    if (!REGISTERED.add(new Addition(feature, biomeTag, step))) return;
    BiomeModifications.addFeature(
        BiomeSelectors.tag(biomeTag),
        step,
        ResourceKey.create(Registries.PLACED_FEATURE, feature));
  }
}
