package ic2.fabric.client.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ic2.core.block.tileentity.Ic2TileEntityBlock;
import ic2.core.block.wiring.CableFoam;
import ic2.core.block.wiring.CableType;
import ic2.core.model.MeshQuad;
import ic2.core.ref.Ic2Blocks;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import org.apache.logging.log4j.LogManager;

/** Bridges the legacy IC2 geometry definitions to the 26.3 model baking pipeline. */
@Environment(EnvType.CLIENT)
public final class Ic2ModelLoadingPlugin implements PreparableModelLoadingPlugin<Map<Identifier, JsonObject>> {
  public static void register() {
    PreparableModelLoadingPlugin.register(Ic2ModelLoadingPlugin::load, new Ic2ModelLoadingPlugin());
  }
  private static CompletableFuture<Map<Identifier, JsonObject>> load(
      PreparableReloadListener.SharedState shared, Executor executor) {
    return CompletableFuture.supplyAsync(() -> scan(shared.resourceManager()), executor);
  }
  private static Map<Identifier, JsonObject> scan(ResourceManager manager) {
    Map<Identifier, JsonObject> found = new HashMap<>();
    manager.listResources("models", path -> path.getPath().endsWith(".json")).forEach((id, resource) -> {
      if (!id.getNamespace().equals("ic2")) return;
      try (var reader = resource.openAsReader()) {
        String path = id.getPath();
        found.put(id.withPath(path.substring(7, path.length() - 5)), JsonParser.parseReader(reader).getAsJsonObject());
      } catch (Exception exception) {
        throw new IllegalStateException("Cannot read IC2 model " + id, exception);
      }
    });
    return found;
  }
  @Override public void initialize(Map<Identifier, JsonObject> data, ModelLoadingPlugin.Context context) {
    LogManager.getLogger("ic2").info("IC2 model loading: {} definitions", data.size());
    java.util.Set<Identifier> roots = new java.util.HashSet<>(data.keySet());
    // These parameterized item parents have no layer0 until a child supplies it.
    // Keep their JSON available for inheritance, but never request a standalone bake.
    roots.remove(Identifier.fromNamespaceAndPath("ic2", "item/default"));
    roots.remove(Identifier.fromNamespaceAndPath("ic2", "item/tool/default"));
    // Legacy override models are not dependencies of modern item JSON. Mark them before baking.
    context.addModel(net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey.create(() -> "ic2:legacy_model_dependencies"),
        new net.fabricmc.fabric.api.client.model.loading.v1.UnbakedExtraModel<java.util.Set<Identifier>>() {
          @Override public void resolveDependencies(net.minecraft.client.resources.model.ResolvableModel.Resolver resolver) {
            roots.forEach(resolver::markDependency);
          }
          @Override public java.util.Set<Identifier> bake(net.minecraft.client.resources.model.ModelBaker baker) {
            return java.util.Set.copyOf(roots);
          }
        });
    context.modifyModelOnLoad().register((original, modelContext) -> {
      JsonObject json = data.get(modelContext.id());
      if (json == null || !json.has("loader")) return original;
      String loader = json.get("loader").getAsString();
      Identifier parent = switch (loader) {
        case "ic2:be" -> Identifier.parse(json.get("id").getAsString().replace("block/be/", "block/"));
        case "ic2:wall" -> Identifier.fromNamespaceAndPath("ic2", "block/cf/wall_light_gray");
        case "ic2:mask_overlay" -> Identifier.parse(json.get("base").getAsString());
        default -> null;
      };
      if (!loader.equals("ic2:cable")) {
        if (parent == null) throw new IllegalArgumentException("Unknown model loader " + loader);
        return new UnbakedModel() { @Override public Identifier parent() { return parent; } };
      }
      return new UnbakedModel() {
        @Override public net.minecraft.client.resources.model.sprite.TextureSlots.Data textureSlots() {
          CableType type = CableType.valueOf(json.get("type").getAsString());
          int insulation = json.get("insulation").getAsInt();
          CableFoam foam = CableFoam.get(json.get("foam").getAsString());
          String path;
          if (foam != null) {
            path = foam.isSoft() ? "block/cf/foam" : "block/cf/wall_" + foam.getColor().getSerializedName();
          } else {
            path = "block/wiring/cable/" + type.name() + "_cable_" + insulation;
            if (insulation >= type.minColoredInsulation) path += "_black";
            if (json.get("active").getAsBoolean() && (type == CableType.detector || type == CableType.splitter)) path += "_active";
          }
          return new net.minecraft.client.resources.model.sprite.TextureSlots.Data.Builder()
              .addTexture("particle", new net.minecraft.client.resources.model.sprite.Material(
                  Identifier.fromNamespaceAndPath("ic2", path))).build();
        }
        @Override public UnbakedGeometry geometry() {
          return (slots, baker, transform, debugName) -> {
            DynamicCableModelFabric cable = new DynamicCableModelFabric(
                CableType.valueOf(json.get("type").getAsString()), json.get("insulation").getAsInt(),
                CableFoam.get(json.get("foam").getAsString()), json.get("active").getAsBoolean());
            cable.bake(baker, material -> baker.materials().get(material, debugName).sprite(), transform);
            QuadCollection.Builder quads = new QuadCollection.Builder();
            for (int face = 0; face < 7; face++) {
              Direction direction = face == 6 ? null : Direction.values()[face];
              for (MeshQuad quad : cable.getQuads(null, direction, RandomSource.create(42))) {
                if (direction == null) quads.addUnculledFace(quad.toNative());
                else quads.addCulledFace(direction, quad.toNative());
              }
            }
            return quads.build();
          };
        }
      };
    });
    context.modifyBlockModelAfterBake().register((model, modelContext) -> {
      var state = modelContext.state();
      var layer = ic2.fabric.FabricClientEnvProxy.getBlockLayer(state.getBlock());
      if (layer != null) model = new LayeredBlockModel(model, layer);
      if (state.is(Ic2Blocks.OBSCURED_WALL)) return new WallModelFabric(modelContext.baker());
      if (state.getBlock() instanceof Ic2TileEntityBlock) return new DynamicBeModelFabric(state, model);
      return model;
    });
    context.modifyItemModelBeforeBake().register((original, itemContext) -> {
      Identifier modelId = itemContext.itemId().withPrefix("item/");
      JsonObject json = data.get(modelId);
      if (json == null) return original;
      var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(itemContext.itemId());
      boolean legacy = json.has("overrides") || json.has("loader")
          || ic2.fabric.FabricClientEnvProxy.getItemColor(item) != null;
      return legacy ? new LegacyItemModel(original, modelId, json) : original;
    });
  }
}
