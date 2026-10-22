package ic2.core.recipe.v2;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Builds the immutable Minecraft 26.3 serializer while retaining IC's codec implementations. */
public abstract class Ic2RecipeSerializer<T extends Recipe<?>> {
  private RecipeSerializer<T> serializer;

  public final RecipeSerializer<T> serializer() {
    if (this.serializer == null) {
      this.serializer = new RecipeSerializer<>(this.codec(), this.streamCodec());
    }
    return this.serializer;
  }

  public abstract MapCodec<T> codec();

  public abstract StreamCodec<RegistryFriendlyByteBuf, T> streamCodec();
}
