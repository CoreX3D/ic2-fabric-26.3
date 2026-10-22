package ic2.fabric.client.model;
import ic2.core.model.MeshQuad;
import java.util.List;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.core.Direction;
final class QuadEmit {
  private QuadEmit() {}
  static void emit(QuadEmitter emitter, MeshQuad quad, Direction cullFace) {
    emitter.fromBakedQuad(quad.toNative()).cullFace(cullFace);
    int[] vertices = quad.getVertices();
    for (int i = 0; i < 4; i++) {
      int abgr = vertices[i * 8 + 3];
      int argb = abgr & 0xff00ff00 | (abgr & 255) << 16 | (abgr >>> 16 & 255);
      emitter.color(i, argb);
    }
    emitter.emit();
  }
  static void emitAll(QuadEmitter emitter, List<MeshQuad> quads, Direction cullFace) {
    for (MeshQuad quad : quads) emit(emitter, quad, cullFace);
  }
}
