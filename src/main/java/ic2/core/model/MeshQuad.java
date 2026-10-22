package ic2.core.model;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

/** Editable IC2 geometry, converted to immutable 26.3 quads at the renderer boundary. */
public final class MeshQuad {
  private final int[] vertices;
  private final int tint;
  private final Direction face;
  private final TextureAtlasSprite sprite;
  private final boolean shade;
  private final BakedQuad.MaterialInfo material;

  public MeshQuad(int[] vertices, int tint, Direction face, TextureAtlasSprite sprite, boolean shade) {
    this(vertices, tint, face, sprite, shade, null);
  }
  private MeshQuad(int[] vertices, int tint, Direction face, TextureAtlasSprite sprite,
      boolean shade, BakedQuad.MaterialInfo material) {
    if (vertices.length != 32) throw new IllegalArgumentException("A quad needs four eight-word vertices");
    this.vertices = vertices;
    this.tint = tint;
    this.face = face;
    this.sprite = sprite;
    this.shade = shade;
    this.material = material;
  }
  public static MeshQuad fromNative(BakedQuad quad) {
    int[] data = new int[32];
    for (int i = 0; i < 4; i++) {
      var pos = quad.position(i);
      int off = i * 8;
      data[off] = Float.floatToRawIntBits(pos.x());
      data[off + 1] = Float.floatToRawIntBits(pos.y());
      data[off + 2] = Float.floatToRawIntBits(pos.z());
      data[off + 3] = -1;
      data[off + 4] = Float.floatToRawIntBits(UVPair.unpackU(quad.packedUV(i)));
      data[off + 5] = Float.floatToRawIntBits(UVPair.unpackV(quad.packedUV(i)));
    }
    return new MeshQuad(data, quad.materialInfo().tintIndex(), quad.direction(),
        quad.materialInfo().sprite(), quad.materialInfo().shadeDirectionOverride() == null, quad.materialInfo());
  }
  public BakedQuad toNative() { return toNative(tint); }
  public BakedQuad toNative(int tintIndex) {
    BakedQuad.MaterialInfo info = material;
    if (info == null) {
      info = BakedQuad.MaterialInfo.of(new Material.Baked(sprite, false), sprite.transparency(),
          tintIndex, shade ? null : Direction.UP, 0);
    } else if (info.tintIndex() != tintIndex) {
      info = new BakedQuad.MaterialInfo(info.sprite(), info.layer(), info.itemRenderType(),
          info.itemGlintRenderType(), info.itemGlintSpecialRenderType(), tintIndex,
          info.shadeDirectionOverride(), info.lightEmission());
    }
    return new BakedQuad(position(0), position(1), position(2), position(3), uv(0), uv(1), uv(2), uv(3), face, info);
  }
  private Vector3f position(int vertex) {
    int off = vertex * 8;
    return new Vector3f(Float.intBitsToFloat(vertices[off]), Float.intBitsToFloat(vertices[off + 1]),
        Float.intBitsToFloat(vertices[off + 2]));
  }
  private long uv(int vertex) {
    int off = vertex * 8;
    return UVPair.pack(Float.intBitsToFloat(vertices[off + 4]), Float.intBitsToFloat(vertices[off + 5]));
  }
  public int[] getVertices() { return vertices; }
  public int getTintIndex() { return tint; }
  public Direction getDirection() { return face; }
  public TextureAtlasSprite getSprite() { return sprite; }
  public boolean isShade() { return shade; }

  public MeshQuad withVertices(int[] data, int tintIndex, Direction direction) {
    return new MeshQuad(data, tintIndex, direction, sprite, shade, material);
  }

  public MeshQuad withLayer(net.minecraft.client.renderer.chunk.ChunkSectionLayer layer) {
    BakedQuad.MaterialInfo info = toNative().materialInfo();
    return new MeshQuad(vertices, tint, face, sprite, shade,
        new BakedQuad.MaterialInfo(sprite, layer, info.itemRenderType(), info.itemGlintRenderType(),
            info.itemGlintSpecialRenderType(), tint, info.shadeDirectionOverride(), info.lightEmission()));
  }
}
