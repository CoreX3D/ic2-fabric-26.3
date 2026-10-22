package ic2.core.recipe.v2;

import ic2.api.recipe.Recipes;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

public class RecipeManagerGetter<T> implements Recipes.IGetter<T> {
  private static volatile RecipeManager clientManager;
  private final Function<RecipeManager, T> factory;
  private final Map<RecipeManager, T> cache = new WeakHashMap<>();

  public RecipeManagerGetter(Function<RecipeManager, T> factory) {
    this.factory = factory;
  }

  @Override
  public T get(Level world) {
    RecipeManager manager = getRecipeManager(world);
    return this.cache.computeIfAbsent(manager, this.factory);
  }

  public static RecipeManager getRecipeManager(Level level) {
    return level.isClientSide() ? getClientManager() : level.getServer().getRecipeManager();
  }

  public static RecipeManager getClientManager() {
    RecipeManager manager = clientManager;
    if (manager == null) throw new IllegalStateException("IC2 recipes have not been synchronized yet");
    return manager;
  }

  /** Atomically replaces client data after join or a server datapack reload. */
  public static void setClientRecipes(
      Collection<net.minecraft.world.item.crafting.RecipeHolder<?>> recipes,
      HolderLookup.Provider registries) {
    clientManager = RecipeRegistryAccess.create(recipes, registries);
  }

  public static void clearClientRecipes() {
    clientManager = null;
  }

  /** RecipeManager no longer exposes getAllRecipesFor in 26.3. */
  @SuppressWarnings("unchecked")
  public static <I extends RecipeInput, R extends Recipe<I>>
      List<net.minecraft.world.item.crafting.RecipeHolder<R>> getRecipes(
          RecipeManager manager, RecipeType<R> type) {
    return manager.getRecipes().stream()
        .filter(holder -> holder.value().getType() == type)
        .map(holder -> (net.minecraft.world.item.crafting.RecipeHolder<R>) holder)
        .toList();
  }
}
