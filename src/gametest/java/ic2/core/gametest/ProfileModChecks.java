package ic2.core.gametest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fails an opt-in compatibility run if Loader did not load the staged profile mods. */
public final class ProfileModChecks implements ModInitializer {
  private static final Logger LOGGER = LoggerFactory.getLogger("ic2-gametest-profile");

  @Override
  public void onInitialize() {
    for (String modId : System.getProperty("ic2.gametest.requiredMods", "").split(",")) {
      if (modId.isBlank()) continue;
      var loaded = FabricLoader.getInstance().getModContainer(modId);
      if (loaded.isEmpty()) {
        throw new IllegalStateException("Compatibility test did not load requested profile mod " + modId);
      }
      LOGGER.info("Profile mod loaded: {} {}", modId,
          loaded.orElseThrow().getMetadata().getVersion().getFriendlyString());
    }
  }
}
