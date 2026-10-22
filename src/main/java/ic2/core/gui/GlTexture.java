package ic2.core.gui;

import ic2.core.IC2;
import ic2.core.Ic2Gui;
import ic2.core.util.LogCategory;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;

/** Texture dimensions for XML GUI images; GPU resources belong to TextureManager. */
public class GlTexture implements Closeable {
  private static final Map<Identifier, GlTexture> textures = new HashMap<>();
  private final Identifier loc;
  protected int width;
  protected int height;
  public GlTexture(Identifier loc) { this.loc = loc; }
  public static void init() {
    ResourceManager manager = Minecraft.getInstance().getResourceManager();
    if (manager instanceof ReloadableResourceManager reloadable) {
      reloadable.registerReloadListener((state, preparationExecutor, barrier, applyExecutor) ->
          barrier.wait(null).thenRunAsync(textures::clear, applyExecutor));
    }
  }
  public static GlTexture get(Identifier identifier) {
    if (textures.containsKey(identifier)) return textures.get(identifier);
    return add(identifier, new GlTexture(identifier));
  }
  public static GlTexture add(Identifier identifier, GlTexture texture) {
    try {
      texture.load(Minecraft.getInstance().getResourceManager());
    } catch (IOException e) {
      IC2.log.warn(LogCategory.General, "Can't load texture %s", identifier);
      texture = null;
    }
    textures.put(identifier, texture);
    return texture;
  }
  protected void load(ResourceManager manager) throws IOException {
    try (InputStream stream = manager.getResourceOrThrow(this.loc).open()) {
      BufferedImage image = ImageIO.read(stream);
      if (image == null) throw new IOException("Unsupported image: " + this.loc);
      this.load(image);
    }
  }
  protected void load(BufferedImage image) {
    this.width = image.getWidth();
    this.height = image.getHeight();
  }
  @Override
  public void close() { textures.remove(this.loc, this); }
  public void bind() { Ic2Gui.bindTexture(this.loc); }
  public int getWidth() { return this.width; }
  public int getHeight() { return this.height; }
  public int getCanvasWidth() { return this.width; }
  public int getCanvasHeight() { return this.height; }
}
