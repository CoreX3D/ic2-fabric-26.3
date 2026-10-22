package ic2.fabric;

import ic2.core.event.EventHandlerClient;
import ic2.core.init.IC2ClientConfig;
import ic2.core.item.armor.jetpack.JetpackHandler;
import ic2.core.item.armor.jetpack.LayerJetpackOverride;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Client entrypoint, replacing the client half of {@code FmlMod} plus {@code
 * ClientModEventHandlerForge}.
 *
 * <p>NeoForge collected client registrations into lists and applied them from
 * {@code FMLClientSetupEvent}, {@code RegisterMenuScreensEvent}, {@code RegisterColorHandlersEvent},
 * {@code EntityRenderersEvent} and {@code ModelEvent}. Fabric's {@code FabricClientEnvProxy}
 * performs those registrations eagerly, so they run here, inside client mod initialisation, where
 * Fabric's registries still accept entries.
 */
@Environment(EnvType.CLIENT)
public final class Ic2FabricClient implements ClientModInitializer {
  private static final Logger LOGGER = LogManager.getLogger("ic2");

  @Override
  public void onInitializeClient() {
    loadClientConfig();
    FabricClientFluidHandler.registerRendering();

    // NeoForge: FMLClientSetupEvent ran EventHandlerClient.onClientSetup(), which performs the
    // screen, key, colour, layer and renderer registrations through FabricClientEnvProxy.
    EventHandlerClient.onClientSetup();

    // NeoForge: SoundEngineLoadEvent.
    EventHandlerClient.onSoundSetup();

    FabricClientNetwork.register();
    FabricClientEventHandler.register();
    LayerJetpackOverride.register();

    // NeoForge: ModelEvent.RegisterGeometryLoaders registered ic2:be, ic2:cable, ic2:mask_overlay
    // and ic2:wall as geometry loaders. Fabric removed custom JSON model loaders, so the same 388
    // model files are served through a model loading plugin instead.
    ic2.fabric.client.model.Ic2ModelLoadingPlugin.register();

    ItemTooltipCallback.EVENT.register(
        (stack, context, flag, lines) -> {
          JetpackHandler handler = JetpackHandler.instance;
          if (handler != null) {
            handler.appendTooltip(stack, lines);
          }
        });
  }

  private static void loadClientConfig() {
    Path configDir = FabricLoader.getInstance().getConfigDir();

    try {
      IC2ClientConfig.SPEC.load(configDir.resolve("ic2-client.cfg"));
      IC2ClientConfig.SPEC.save();
    } catch (IOException e) {
      LOGGER.error("failed to load the IC2 client config", e);
    }
  }
}
