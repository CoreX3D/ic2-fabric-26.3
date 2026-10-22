import ic2.core.block.wiring.AbstractCableBlock;
import ic2.core.block.wiring.CableType;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Checks all cable connections against native 26.3 pipe geometry at IC's intended width. */
public final class CableGeometry {
  public static void main(String[] args) throws Exception {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    var makeShapes = AbstractCableBlock.class.getDeclaredMethod("makeCableShapes", float.class);
    makeShapes.setAccessible(true);
    int checked = 0;
    for (CableType type : CableType.values()) {
      for (int insulation = 0; insulation <= type.maxInsulation; insulation++) {
        String name = type.name() + "_" + insulation;
        VoxelShape[] actualShapes = (VoxelShape[]) makeShapes.invoke(null, type.getThickness(insulation));
        double width = type.getThickness(insulation) * 16.0;
        VoxelShape center = Block.cube(width);
        var nativeArms = Shapes.rotateAll(Block.boxZ(width, 0.0, 8.0));
        for (int mask = 0; mask < 64; mask++) {
          VoxelShape expected = center;
          for (Direction direction : Direction.values()) {
            if ((mask & (1 << direction.ordinal())) != 0) {
              expected = Shapes.or(expected, nativeArms.get(direction));
            }
          }
          var actual = actualShapes[mask];
          check(!Shapes.joinIsNotEmpty(actual, expected, BooleanOp.NOT_SAME),
              "Incorrect cable geometry: " + name + " mask " + mask);
          if (mask == 0) {
            check(actual.bounds().getXsize() == type.getThickness(insulation), "Incorrect cable width: " + name);
          }
          checked++;
        }
      }
    }
    System.out.println("PASS: " + checked + " cable shapes match native 26.3 geometry at the original IC width");
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

}
