package ic2.fabric.client.model;

import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import ic2.core.model.BlockMesh;
import ic2.core.model.MeshQuad;
import ic2.fabric.FabricClientEnvProxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;

/** Retains IC2's stack predicates, tint providers and sampled obscurator texture. */
final class LegacyItemModel implements ItemModel.Unbaked {
  private final ItemModel.Unbaked original;
  private final Identifier id;
  private final JsonObject json;
  LegacyItemModel(ItemModel.Unbaked original, Identifier id, JsonObject json) {
    this.original = original;
    this.id = id;
    this.json = json;
  }
  @Override public MapCodec<? extends ItemModel.Unbaked> type() { return original.type(); }
  @Override public void resolveDependencies(ResolvableModel.Resolver resolver) {
    original.resolveDependencies(resolver);
    resolver.markDependency(id);
    if (json.has("base")) resolver.markDependency(Identifier.parse(json.get("base").getAsString()));
    if (json.has("overrides")) for (var entry : json.getAsJsonArray("overrides")) {
      resolver.markDependency(Identifier.parse(entry.getAsJsonObject().get("model").getAsString()));
    }
  }
  @Override public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transform) {
    var baker = context.blockModelBaker();
    BlockMesh base = BlockMesh.bake(baker, id, BlockModelRotation.IDENTITY);
    MaskOverlayItemModelFabric mask = null;
    if (json.has("loader") && "ic2:mask_overlay".equals(json.get("loader").getAsString())) {
      mask = new MaskOverlayItemModelFabric(Identifier.parse(json.get("base").getAsString()),
          Identifier.parse(json.get("mask").getAsString()), json.get("scale_overlay").getAsBoolean(),
          json.get("offset").getAsFloat());
      mask.bake(baker, material -> baker.materials().get(material, id::toString).sprite(), BlockModelRotation.IDENTITY);
      base = mask;
    }
    List<ModelOverride> overrides = new ArrayList<>();
    if (json.has("overrides")) for (var entry : json.getAsJsonArray("overrides")) {
      var override = entry.getAsJsonObject();
      Map<Identifier, Float> predicates = new LinkedHashMap<>();
      override.getAsJsonObject("predicate").entrySet().forEach(value ->
          predicates.put(Identifier.parse(value.getKey()), value.getValue().getAsFloat()));
      overrides.add(new ModelOverride(predicates, BlockMesh.bake(baker,
          Identifier.parse(override.get("model").getAsString()), BlockModelRotation.IDENTITY)));
    }
    final BlockMesh defaultModel = base;
    final MaskOverlayItemModelFabric overlay = mask;
    return (renderState, stack, resolver, display, level, owner, seed) -> {
      BlockMesh selected = overlay == null ? defaultModel : overlay.resolveForStack(stack);
      for (int i = overrides.size() - 1; i >= 0; i--) {
        ModelOverride candidate = overrides.get(i);
        boolean matches = true;
        for (var predicate : candidate.predicates.entrySet()) {
          var provider = FabricClientEnvProxy.getModelPredicate(stack.getItem(), predicate.getKey());
          float value = provider == null ? vanillaPredicate(predicate.getKey(), stack)
              : provider.unclampedCall(stack, level, owner instanceof LivingEntity living ? living : null, seed);
          if (value < predicate.getValue()) { matches = false; break; }
        }
        if (matches) { selected = candidate.model; break; }
      }
      var layer = renderState.newLayer();
      var tintProvider = FabricClientEnvProxy.getItemColor(stack.getItem());
      List<BakedQuad> quads = new ArrayList<>();
      List<Vector3fc> extents = new ArrayList<>();
      var random = RandomSource.create(seed);
      for (int side = 0; side < 7; side++) {
        for (MeshQuad quad : selected.getQuads(null, side == 6 ? null : Direction.values()[side], random)) {
          int abgr = quad.getVertices()[3];
          int color = abgr & 0xff00ff00 | (abgr & 255) << 16 | (abgr >>> 16 & 255);
          if (quad.getTintIndex() >= 0 && tintProvider != null) {
            int tint = tintProvider.getColor(stack, quad.getTintIndex());
            if ((tint & 0xff000000) == 0) tint |= 0xff000000;
            color = multiply(color, tint);
          }
          int tintIndex = layer.tintLayers().size();
          layer.tintLayers().add(color);
          BakedQuad nativeQuad = quad.toNative(tintIndex);
          quads.add(nativeQuad);
          for (int vertex = 0; vertex < 4; vertex++) extents.add(nativeQuad.position(vertex));
        }
      }
      Vector3fc[] bounds = extents.toArray(Vector3fc[]::new);
      layer.setQuads(ItemQuads.split(quads));
      layer.setExtents(() -> bounds);
      layer.setParticleMaterial(selected.particleMaterial());
      layer.setUsesBlockLight(selected.usesBlockLight());
      layer.setItemTransform(selected.getTransforms().getTransform(display));
      layer.setLocalTransform(transform);
      layer.setFoilType(stack.hasFoil() ? ItemStackRenderState.FoilType.STANDARD : ItemStackRenderState.FoilType.NONE);
      renderState.appendModelIdentityElement(selected);
    };
  }
  private static int multiply(int color, int tint) {
    int r = ((color >> 16) & 255) * ((tint >> 16) & 255) / 255;
    int g = ((color >> 8) & 255) * ((tint >> 8) & 255) / 255;
    int b = (color & 255) * (tint & 255) / 255;
    int a = (color >>> 24) * (tint >>> 24) / 255;
    return a << 24 | r << 16 | g << 8 | b;
  }
  private static float vanillaPredicate(Identifier id, net.minecraft.world.item.ItemStack stack) {
    if (!id.getNamespace().equals("minecraft")) return Float.NEGATIVE_INFINITY;
    return switch (id.getPath()) {
      case "damaged" -> stack.isDamaged() ? 1f : 0f;
      case "damage" -> stack.isDamageableItem() ? (float) stack.getDamageValue() / stack.getMaxDamage() : 0f;
      default -> Float.NEGATIVE_INFINITY;
    };
  }
  private record ModelOverride(Map<Identifier, Float> predicates, BlockMesh model) {}
}

