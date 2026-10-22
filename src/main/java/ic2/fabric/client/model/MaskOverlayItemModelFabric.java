package ic2.fabric.client.model;

import com.mojang.blaze3d.platform.NativeImage;
import ic2.core.item.tool.ItemObscurator;
import ic2.core.util.StackUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.BitSet;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import ic2.core.model.MeshQuad;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import ic2.core.model.BlockMesh;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MaskOverlayItemModelFabric implements BlockMesh {
  private final Identifier baseModelLocation;
  private final Identifier maskTextureLocation;
  private final boolean scaleOverlay;
  private final float offset;

  private BlockMesh baseModel;
  private List<MeshQuad> baseQuads = List.of();
  private List<OverlayQuad> overlayTemplates = List.of();
  private float uS;
  private float vS;
  private float uE;
  private float vE;

  /**
   * Resolves the model for one stack, adding the overlay quads for whatever block the obscurator is
   * holding.
   *
   * <p>NeoForge supplied this through a custom {@code ItemOverrides} subclass. Vanilla 1.21.1 builds
   * {@code ItemOverrides} only from a baked model's JSON {@code overrides} list, and the overlay
   * depends on arbitrary per-stack NBT, so Fabric renders the obscurator through a
   * {@code BuiltinItemRendererRegistry} item renderer that calls this method instead.
   */
  public BlockMesh resolveForStack(ItemStack stack) {
    List<MeshQuad> overlayQuads = List.of();
    CompoundTag nbt = StackUtil.getTag(stack);
    if (nbt != null) {
      BlockState state = ItemObscurator.getState(nbt);
      Direction side = ItemObscurator.getSide(nbt);
      int[] colorMultipliers = ItemObscurator.getColorMultipliers(nbt);
      if (state != null && side != null && colorMultipliers != null) {
        ItemObscurator.ObscuredRenderInfo renderInfo = ItemObscurator.getRenderInfo(state, side);
        if (renderInfo != null
            && colorMultipliers.length * 4 == renderInfo.uvs.length
            && !this.overlayTemplates.isEmpty()) {
          overlayQuads = this.buildOverlayQuads(renderInfo, colorMultipliers);
        }
      }
    }

    return new OverlaidModel(this.baseModel, this.getDefaultQuads(), overlayQuads);
  }

  public MaskOverlayItemModelFabric(
      Identifier baseModelLocation,
      Identifier maskTextureLocation,
      boolean scaleOverlay,
      float offset) {
    this.baseModelLocation = baseModelLocation;
    this.maskTextureLocation = maskTextureLocation;
    this.scaleOverlay = scaleOverlay;
    this.offset = offset;
  }

  private static Identifier maskToResource(Identifier maskLocation) {
    return Identifier.fromNamespaceAndPath(
        maskLocation.getNamespace(), "textures/" + maskLocation.getPath() + ".png");
  }

  private List<MeshQuad> getDefaultQuads() {
    if (!this.baseQuads.isEmpty()) {
      return this.baseQuads;
    }

    if (this.baseModel == null) {
      return List.of();
    }

    RandomSource rand = RandomSource.create(42L);
    return this.baseModel.getQuads(null, null, rand);
  }

  private MeshQuad remapQuad(
      MeshQuad template,
      TextureAtlasSprite sprite,
      float uS,
      float vS,
      float uE,
      float vE,
      int colorMul) {
    int[] oldData = template.getVertices();
    int[] newData = oldData.clone();
    int stride = oldData.length >>> 2;

    for (int vertex = 0; vertex < 4; vertex++) {
      int index = vertex * stride;
      float x = Float.intBitsToFloat(oldData[index]);
      float y = Float.intBitsToFloat(oldData[index + 1]);
      float u = (x - this.uS) / (this.uE - this.uS);
      float v = (this.vE - y) / (this.vE - this.vS);
      newData[index + 4] = Float.floatToRawIntBits(uS + u * (uE - uS));
      newData[index + 5] = Float.floatToRawIntBits(vS + v * (vE - vS));
    }

    if (colorMul != -1) {
      float r = ((colorMul >>> 16) & 0xFF) / 255f;
      float g = ((colorMul >>> 8) & 0xFF) / 255f;
      float b = (colorMul & 0xFF) / 255f;

      for (int v = 0; v < 4; v++) {
        int off = v * stride + 3;
        int packedColor = oldData[off];
        int a = (packedColor >>> 24) & 0xFF;
        int red = (int) ((packedColor & 0xFF) * r);
        int green = (int) (((packedColor >>> 8) & 0xFF) * g);
        int blue = (int) (((packedColor >>> 16) & 0xFF) * b);
        newData[off] = (a << 24) | (blue << 16) | (green << 8) | red;
      }
    }

    return new MeshQuad(
        newData, template.getTintIndex(), template.getDirection(), sprite, template.isShade());
  }

  private static MeshQuad createQuad(
      float xS, float yS, float xE, float yE, float z, Direction face, TextureAtlasSprite sprite) {
    int[] data = new int[32];
    int normal = computeNormal(face);
    int color = 0xFFFFFFFF;

    data[0] = Float.floatToRawIntBits(xS);
    data[1] = Float.floatToRawIntBits(yS);
    data[2] = Float.floatToRawIntBits(z);
    data[3] = color;
    data[4] = Float.floatToRawIntBits(0f);
    data[5] = Float.floatToRawIntBits(0f);
    data[6] = 0x00F000F0;
    data[7] = normal;

    data[8] = Float.floatToRawIntBits(xE);
    data[9] = Float.floatToRawIntBits(yS);
    data[10] = Float.floatToRawIntBits(z);
    data[11] = color;
    data[12] = Float.floatToRawIntBits(1f);
    data[13] = Float.floatToRawIntBits(0f);
    data[14] = 0x00F000F0;
    data[15] = normal;

    data[16] = Float.floatToRawIntBits(xE);
    data[17] = Float.floatToRawIntBits(yE);
    data[18] = Float.floatToRawIntBits(z);
    data[19] = color;
    data[20] = Float.floatToRawIntBits(1f);
    data[21] = Float.floatToRawIntBits(1f);
    data[22] = 0x00F000F0;
    data[23] = normal;

    data[24] = Float.floatToRawIntBits(xS);
    data[25] = Float.floatToRawIntBits(yE);
    data[26] = Float.floatToRawIntBits(z);
    data[27] = color;
    data[28] = Float.floatToRawIntBits(0f);
    data[29] = Float.floatToRawIntBits(1f);
    data[30] = 0x00F000F0;
    data[31] = normal;

    if (face == Direction.SOUTH) {
      // The front and rear must have opposite winding; both sides remain visible when held.
      int[] reverse = new int[data.length];
      for (int vertex = 0; vertex < 4; vertex++) {
        System.arraycopy(data, (3 - vertex) * 8, reverse, vertex * 8, 8);
      }
      data = reverse;
    }
    return new MeshQuad(data, -1, face, sprite, false);
  }

  private static int computeNormal(Direction face) {
    return face.getStepX() & 0xFF
        | ((face.getStepY() & 0xFF) << 8)
        | ((face.getStepZ() & 0xFF) << 16);
  }

  private static BitSet readMask(NativeImage img) {
    int w = img.getWidth();
    int h = img.getHeight();
    BitSet ret = new BitSet(w * h);

    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        if ((img.getPixel(x, y) >>> 24) > 128) {
          ret.set(y * w + x);
        }
      }
    }

    return ret;
  }

  private static List<Area> searchAreas(BitSet pixels, int width) {
    List<Area> ret = new ArrayList<>();
    int idx = 0;

    while ((idx = pixels.nextSetBit(idx)) != -1) {
      int y = idx / width;
      int x = idx - y * width;
      int areaWidth = Math.min(width - x, pixels.nextClearBit(idx + 1) - idx);
      int areaHeight = 1;

      for (int nextLineIdx = idx + width;
          pixels.get(nextLineIdx)
              && pixels.nextClearBit(nextLineIdx + 1) >= nextLineIdx + areaWidth;
          nextLineIdx += width) {
        pixels.clear(nextLineIdx, nextLineIdx + areaWidth);
        areaHeight++;
      }

      ret.add(new Area(x, y, areaWidth, areaHeight));
      idx += areaWidth;
    }

    return ret;
  }

  public Collection<Identifier> getDependencies() {
    return List.of(this.baseModelLocation);
  }

  public void resolveParents(Function<Identifier, UnbakedModel> resolver) {}

  public BlockMesh bake(
      ModelBaker bakery,
      Function<Material, TextureAtlasSprite> spriteGetter,
      ModelState modelTransform) {
    this.baseModel = BlockMesh.bake(bakery, this.baseModelLocation, modelTransform);
    if (this.baseModel == null) {
      throw new IllegalStateException("missing base model " + this.baseModelLocation);
    }

    RandomSource rand = RandomSource.create(42L);
    List<MeshQuad> quads = this.baseModel.getQuads(null, null, rand);
    this.baseQuads = quads.isEmpty() ? List.of() : List.copyOf(quads);

    TextureAtlasSprite maskSprite =
        spriteGetter.apply(new Material(this.maskTextureLocation));
    Identifier maskResLoc = maskToResource(this.maskTextureLocation);
    try (var input = Minecraft.getInstance().getResourceManager().getResource(maskResLoc)
            .orElseThrow().open();
        NativeImage maskImage = NativeImage.read(input)) {

      int width = maskImage.getWidth();
      int height = maskImage.getHeight();
      List<Area> areas = searchAreas(readMask(maskImage), width);
      if (areas.isEmpty()) throw new IllegalStateException("Empty obscurator mask " + maskResLoc);

      float texWidth = 1f;
      float texHeight = 1f;
      float zF = (7.5f - this.offset) / 16f;
      float zB = (8.5f + this.offset) / 16f;
      List<OverlayQuad> templates = new ArrayList<>(areas.size());

      for (Area area : areas) {
        float xS = area.x / (float) width * texWidth;
        float yS = 1f - area.y / (float) height * texHeight;
        float xE = (area.x + area.width) / (float) width * texWidth;
        float yE = 1f - (area.y + area.height) / (float) height * texHeight;
        MeshQuad frontQuad = createQuad(xS, yS, xE, yE, zF, Direction.NORTH, maskSprite);
        MeshQuad backQuad = createQuad(xS, yS, xE, yE, zB, Direction.SOUTH, maskSprite);
        templates.add(new OverlayQuad(frontQuad, backQuad));
      }

      this.overlayTemplates = templates;
      if (this.scaleOverlay) {
        calcOverlayBounds(areas, width, height, texWidth, texHeight);
      } else {
        this.uS = this.vS = 0f;
        this.uE = this.vE = 1f;
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot load obscurator mask " + this.maskTextureLocation, e);
    }

    return this;
  }

  @Override
  public @NotNull List<MeshQuad> getQuads(
      @Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource random) {
    return side == null ? this.getDefaultQuads() : List.of();
  }

  @Override
  public boolean useAmbientOcclusion() {
    return this.baseModel.useAmbientOcclusion();
  }

  @Override
  public boolean isGui3d() {
    return this.baseModel.isGui3d();
  }

  @Override
  public boolean usesBlockLight() {
    return this.baseModel.usesBlockLight();
  }

  @Override
  public boolean isCustomRenderer() {
    return false;
  }

  @Override
  public @NotNull TextureAtlasSprite getParticleIcon() {
    return this.baseModel.getParticleIcon();
  }

  @Override
  public ItemTransforms getTransforms() {
    return this.baseModel != null ? this.baseModel.getTransforms() : ItemTransforms.NO_TRANSFORMS;
  }


  private List<MeshQuad> buildOverlayQuads(
      ItemObscurator.ObscuredRenderInfo renderInfo, int[] colorMultipliers) {
    int textureCount = renderInfo.uvs.length / 4;
    List<MeshQuad> result = new ArrayList<>(this.overlayTemplates.size() * 2);

    for (int ti = 0; ti < textureCount; ti++) {
      TextureAtlasSprite sprite = renderInfo.sprites[ti];
      int colorMul = colorMultipliers[ti];

      float quS = renderInfo.uvs[ti * 4];
      float qvS = renderInfo.uvs[ti * 4 + 1];
      float quE = renderInfo.uvs[ti * 4 + 2];
      float qvE = renderInfo.uvs[ti * 4 + 3];

      for (OverlayQuad template : this.overlayTemplates) {
        result.add(remapQuad(template.frontQuad(), sprite, quS, qvS, quE, qvE, colorMul));
        result.add(remapQuad(template.backQuad(), sprite, quS, qvS, quE, qvE, colorMul));
      }
    }
    return result;
  }

  private void calcOverlayBounds(
      List<Area> areas, int width, int height, float texWidth, float texHeight) {
    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxY = Integer.MIN_VALUE;

    for (Area area : areas) {
      if (area.x < minX) minX = area.x;
      if (area.y < minY) minY = area.y;
      if (area.x + area.width > maxX) maxX = area.x + area.width;
      if (area.y + area.height > maxY) maxY = area.y + area.height;
    }

    this.uS = minX / (float) width * texWidth;
    this.vS = 1f - maxY / (float) height * texHeight;
    this.uE = maxX / (float) width * texWidth;
    this.vE = 1f - minY / (float) height * texHeight;
  }

  private record Area(int x, int y, int width, int height) {}

  private record OverlayQuad(MeshQuad frontQuad, MeshQuad backQuad) {}

  private record OverlaidModel(
      BlockMesh baseModel, List<MeshQuad> baseQuads, List<MeshQuad> overlayQuads)
      implements BlockMesh {
    @Override
    public @NotNull List<MeshQuad> getQuads(
        @Nullable BlockState state,
        @Nullable Direction side,
        @NotNull RandomSource random) {
      if (side != null) {
        return this.baseModel.getQuads(state, side, random);
      }

      List<MeshQuad> resolvedBaseQuads =
          this.baseQuads.isEmpty()
              ? this.baseModel.getQuads(state, null, random)
              : this.baseQuads;
      List<MeshQuad> combined =
          new ArrayList<>(resolvedBaseQuads.size() + this.overlayQuads.size());
      combined.addAll(resolvedBaseQuads);
      combined.addAll(this.overlayQuads);
      return combined;
    }

    @Override
    public boolean useAmbientOcclusion() {
      return this.baseModel.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
      return this.baseModel.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
      return this.baseModel.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
      return false;
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon() {
      return this.baseModel.getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
      return this.baseModel.getTransforms();
    }

    }
}

