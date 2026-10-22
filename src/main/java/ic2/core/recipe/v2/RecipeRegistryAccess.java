package ic2.core.recipe.v2;

import com.mojang.serialization.Lifecycle;
import java.util.Collection;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/** Builds a complete client recipe snapshot without mutating the world's synchronized registries. */
public final class RecipeRegistryAccess {
  private RecipeRegistryAccess() {}

  public static RecipeManager create(
      Collection<RecipeHolder<?>> recipes, HolderLookup.Provider registries) {
    MappedRegistry<Recipe<?>> recipeRegistry =
        new MappedRegistry<>(Registries.RECIPE, Lifecycle.stable());
    for (var recipe : recipes) {
      recipeRegistry.register(recipe.id(), recipe.value(), RegistrationInfo.BUILT_IN);
    }
    recipeRegistry.freeze();
    HolderLookup.Provider provider = HolderLookup.Provider.create(Stream.concat(
        registries.listRegistries().filter(registry -> !registry.key().equals(Registries.RECIPE)),
        Stream.of(recipeRegistry)));
    return new RecipeManager(provider);
  }
}
