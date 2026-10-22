package ic2.fabric.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ic2.core.model.MeshQuad;
import java.lang.reflect.Method;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class MaskOverlayGeometryTest {
  @Test void frontAndBackFacesHaveOutwardWinding() throws Exception {
    for (Direction face : new Direction[] {Direction.NORTH, Direction.SOUTH}) {
      int[] data = template(face).getVertices();
      float ax = position(data, 1, 0) - position(data, 0, 0);
      float ay = position(data, 1, 1) - position(data, 0, 1);
      float bx = position(data, 2, 0) - position(data, 0, 0);
      float by = position(data, 2, 1) - position(data, 0, 1);
      assertTrue((ax * by - ay * bx) * face.getStepZ() > 0,
          "The mask must remain visible from both sides of the held item");
    }
  }

  @Test void sampledTextureUsesItsAtlasRegionAcrossMaskBounds() throws Exception {
    Identifier id = Identifier.fromNamespaceAndPath("ic2", "test_mask");
    var model = new MaskOverlayItemModelFabric(id, id, true, 0.001f);
    for (String name : new String[] {"uS", "vS", "uE", "vE"}) {
      var field = MaskOverlayItemModelFabric.class.getDeclaredField(name);
      field.setAccessible(true);
      field.setFloat(model, name.endsWith("S") ? 0.25f : 0.75f);
    }
    Method remap = MaskOverlayItemModelFabric.class.getDeclaredMethod("remapQuad", MeshQuad.class,
        TextureAtlasSprite.class, float.class, float.class, float.class, float.class, int.class);
    remap.setAccessible(true);
    MeshQuad result = (MeshQuad) remap.invoke(model, template(Direction.NORTH), null,
        0.125f, 0.25f, 0.375f, 0.5f, -1);
    for (int vertex = 0; vertex < 4; vertex++) {
      int[] data = result.getVertices();
      assertEquals(position(data, vertex, 0) == 0.25f ? 0.125f : 0.375f,
          Float.intBitsToFloat(data[vertex * 8 + 4]), 0.00001f);
      assertEquals(position(data, vertex, 1) == 0.75f ? 0.25f : 0.5f,
          Float.intBitsToFloat(data[vertex * 8 + 5]), 0.00001f);
    }
  }

  private static MeshQuad template(Direction face) throws Exception {
    Method create = MaskOverlayItemModelFabric.class.getDeclaredMethod("createQuad", float.class,
        float.class, float.class, float.class, float.class, Direction.class, TextureAtlasSprite.class);
    create.setAccessible(true);
    return (MeshQuad) create.invoke(null, 0.25f, 0.75f, 0.75f, 0.25f, 0.5f, face, null);
  }
  private static float position(int[] data, int vertex, int component) {
    return Float.intBitsToFloat(data[vertex * 8 + component]);
  }
}
