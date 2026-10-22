package ic2.core.model;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;
public final class ModelUtil {
  private ModelUtil() {}
  public static BlockMesh getMissingModel() {
    return BlockMesh.wrap(Minecraft.getInstance().getModelManager().getBlockStateModelSet().missingModel());
  }
  public static BlockMesh getBlockModel(BlockState state) {
    return BlockMesh.wrap(Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state));
  }
}
