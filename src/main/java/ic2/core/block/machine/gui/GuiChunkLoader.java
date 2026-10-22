package ic2.core.block.machine.gui;

import com.mojang.blaze3d.platform.NativeImage;
import ic2.core.IC2;
import ic2.core.Ic2Gui;
import ic2.core.block.machine.container.ContainerChunkLoader;
import ic2.core.gui.EnergyGauge;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.MapColor;

public class GuiChunkLoader extends Ic2Gui<ContainerChunkLoader> {
  private static final Identifier background =
      Identifier.fromNamespaceAndPath("ic2", "textures/gui/guichunkloader.png");
  private final Map<Long, Identifier> chunkTextureIds = new HashMap<>();

  public GuiChunkLoader(
      ContainerChunkLoader container, Inventory playerInventory, Component title) {
    super(container, playerInventory, title, 250);
    this.addElement(EnergyGauge.asBolt(this, 12, 125, container.base));
  }

  @Override
  protected Identifier getTextureLocation() {
    return background;
  }

  @Override
  protected void drawForegroundLayer(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
    ChunkPos mainChunk = ChunkPos.containing(this.menu.base.getBlockPos());
    LongSet loadedChunks = ((ContainerChunkLoader) this.menu).base.getLoadedChunks();
    Level world = ((ContainerChunkLoader) this.menu).base.getLevel();
    int amountLoadedChunks = 0;

    for (int i = -4; i <= 4; i++) {
      for (int j = -4; j <= 4; j++) {
        ChunkPos currentChunk = new ChunkPos(mainChunk.x() + i, mainChunk.z() + j);
        int xpos = 89 + 16 * i;
        int ypos = 80 + 16 * j;

        Identifier texLoc = this.getChunkTexture(world, currentChunk);
        guiGraphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texLoc, xpos, ypos, 0, 0, 16, 16, 16, 16);

        if (loadedChunks.contains(currentChunk.pack())) {
          this.drawColoredRect(
              guiGraphics.pose(), xpos - this.leftPos, ypos - this.topPos, 16, 16, 805371648);
          amountLoadedChunks++;
        } else {
          this.drawColoredRect(
              guiGraphics.pose(), xpos - this.leftPos, ypos - this.topPos, 16, 16, 822018048);
        }
      }
    }

    this.drawTrimmedString(
        guiGraphics,
        8,
        58,
        amountLoadedChunks + " / " + ((ContainerChunkLoader) this.menu).base.getMaxChunks(),
        15,
        4210752);
    super.drawForegroundLayer(guiGraphics, mouseX, mouseY);
  }

  private Identifier getChunkTexture(Level world, ChunkPos chunkPos) {
    long key = chunkPos.pack();
    return this.chunkTextureIds.computeIfAbsent(
        key,
        k -> {
          NativeImage img = this.createChunkImage(world, chunkPos);
          DynamicTexture tex = new DynamicTexture(() -> "IC2 chunk loader map", img);
          Identifier loc =
              Identifier.fromNamespaceAndPath(
                  "ic2", "chunkloader_" + chunkPos.x() + "_" + chunkPos.z());
          Minecraft.getInstance().getTextureManager().register(loc, tex);
          return loc;
        });
  }

  private NativeImage createChunkImage(Level world, ChunkPos chunkPos) {
    NativeImage img = new NativeImage(16, 16, false);
    if (!world.hasChunk(chunkPos.x(), chunkPos.z())) {
      for (int cx = 0; cx < 16; cx++) {
        for (int cz = 0; cz < 16; cz++) {
          img.setPixel(cx, cz, 0xFF000000);
        }
      }
      return img;
    }

    LevelChunk chunk = world.getChunk(chunkPos.x(), chunkPos.z());
    MutableBlockPos worldPos = new MutableBlockPos();

    for (int cx = 0; cx < 16; cx++) {
      for (int cz = 0; cz < 16; cz++) {
        worldPos.set(
            chunkPos.x() << 4 | cx,
            chunk.getHeight(Types.WORLD_SURFACE, cx, cz),
            chunkPos.z() << 4 | cz);
        BlockState state = chunk.getBlockState(worldPos);
        if (state.isAir()) {
          worldPos.move(Direction.DOWN);
          state = chunk.getBlockState(worldPos);
        }

        int argb = this.getColor(state, world, worldPos);
        img.setPixel(cx, cz, argb);
      }
    }
    return img;
  }

  private int getColor(BlockState state, Level world, MutableBlockPos pos) {
    MapColor color = state.getMapColor(world, pos);
    if (color == null) {
      IC2.log.warn(
          ic2.core.util.LogCategory.General,
          "BlockState "
              + state
              + " does not have a MapColor set. Please report to the mod author of that mod.");
      return 0;
    } else {
      return color.col | 0xFF000000;
    }
  }

  @Override
  public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
    if (mouseButton == 0) {
      ChunkPos mainChunk = ChunkPos.containing(this.menu.base.getBlockPos());

      for (int dx = -4; dx <= 4; dx++) {
        for (int dy = -4; dy <= 4; dy++) {
          if (mouseX - this.leftPos > 89 + 16 * dx
              && mouseX - this.leftPos <= 89 + 16 * dx + 16
              && mouseY - this.topPos > 80 + 16 * dy
              && mouseY - this.topPos <= 80 + 16 * dy + 16) {
            this.changeChunk(new ChunkPos(mainChunk.x() + dx, mainChunk.z() + dy));
            return true;
          }
        }
      }
    }

    return super.mouseClicked(mouseX, mouseY, mouseButton);
  }

  private void changeChunk(ChunkPos chunk) {
    ChunkPos mainChunk = ChunkPos.containing(this.menu.base.getBlockPos());
    IC2.network
        .get(false)
        .initiateClientTileEntityEvent(
            ((ContainerChunkLoader) this.menu).base,
            chunk.x() - mainChunk.x() + 8 & 15 | (chunk.z() - mainChunk.z() + 8 & 15) << 4);
  }

  @Override
  public void removed() {
    super.removed();
    for (Map.Entry<Long, Identifier> entry : this.chunkTextureIds.entrySet()) {
      Minecraft.getInstance().getTextureManager().release(entry.getValue());
    }
    this.chunkTextureIds.clear();
  }
}
