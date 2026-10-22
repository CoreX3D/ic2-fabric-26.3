import java.io.InputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.selectors.ElementNode;
import org.spongepowered.asm.mixin.injection.selectors.ITargetSelector;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;

/** Check overload selection against the actual target bytecode, without booting Minecraft. */
public final class FluidRendererInjectionTargets {
  private static final String HEIGHT = "getHeight(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;"
      + "Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/core/BlockPos;"
      + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)F";

  public static void main(String[] args) throws Exception {
    ClassNode type = new ClassNode();
    try (InputStream source = FluidRendererInjectionTargets.class.getClassLoader()
        .getResourceAsStream("net/minecraft/client/renderer/block/FluidRenderer.class")) {
      new ClassReader(source).accept(type, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
    MethodNode ambiguous = select(type, "getHeight");
    MethodNode precise = select(type, HEIGHT);
    int ambiguousReads = aboveCalls(ambiguous), preciseReads = aboveCalls(precise);
    System.out.println("Bare selector: " + ambiguous.name + ambiguous.desc + "; above() calls=" + ambiguousReads);
    System.out.println("Exact selector: " + precise.name + precise.desc + "; above() calls=" + preciseReads);
    int allReads = preciseReads + aboveCalls(select(type, "tesselate")) + aboveCalls(select(type, "getLightCoords"));
    if (ambiguousReads != 0 || preciseReads != 1 || allReads != 3) {
      throw new AssertionError("FluidRenderer injection targets changed: " + allReads + " above() calls");
    }
    System.out.println("PASS: exact sampler selected, three guarded surface-neighbor injection points.");
  }

  private static MethodNode select(ClassNode type, String text) {
    ITargetSelector selector = MemberInfo.parse(text, null).configure(ITargetSelector.Configure.SELECT_MEMBER);
    if (selector.getMaxMatchCount() != 1) throw new AssertionError("Unexpected selector cardinality");
    return type.methods.stream().filter(method -> selector.match(ElementNode.of(type, method)).isExactMatch())
        .findFirst().orElseThrow();
  }

  private static int aboveCalls(MethodNode method) {
    int count = 0;
    for (var instruction : method.instructions) {
      if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/core/BlockPos")
          && call.name.equals("above") && call.desc.equals("()Lnet/minecraft/core/BlockPos;")) count++;
    }
    return count;
  }
}
