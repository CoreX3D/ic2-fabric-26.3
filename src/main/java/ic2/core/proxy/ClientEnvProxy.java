package ic2.core.proxy;

import ic2.core.fluid.Ic2FluidStack;
import ic2.core.model.MeshQuad;
import java.io.File;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public interface ClientEnvProxy {
  <H extends AbstractContainerMenu> void registerScreen(
      MenuType<H> var1, ClientEnvProxy.ScreenFactory<H> var2);

  File getMinecraftDir();

  void registerColorProvider(BlockColor var1, Block... var2);

  void registerColorProvider(ItemColor var1, ItemLike... var2);

  void registerKeyBinding(KeyMapping var1);

  <E extends BlockEntity, S extends BlockEntityRenderState> void registerBer(
      BlockEntityType<E> var1, BlockEntityRendererProvider<? super E, ? super S> var2);

  void registerModelPredicateProvider(Identifier var1, ModelPredicate var2);

  void registerModelPredicateProvider(
      Item var1, Identifier var2, ModelPredicate var3);

  void registerBlockLayer(ChunkSectionLayer var1, Block... var2);

  <E extends Entity> void registerEntityRenderer(
      EntityType<? extends E> var1, EntityRendererProvider<E> var2);

  <E extends BlockEntity, S extends BlockEntityRenderState> void registerBlockEntityRenderer(
      BlockEntityType<? extends E> var1, BlockEntityRendererProvider<E, S> var2);

  TextureAtlasSprite getFluidStillSprite(Ic2FluidStack var1);

  int getFluidColor(Ic2FluidStack var1);

  String getFluidName(Ic2FluidStack var1);

  default ClientEnvProxy.QuadData getQuadData(MeshQuad quad) {
    int[] data = quad.getVertices();
    int stride = data.length / 4;
    int posOffset = 0;
    int uvOffset = 4;
    final float[] positions = new float[12];
    final float[] uvs = new float[8];

    for (int i = 0; i < 4; i++) {
      int offset = i * stride;

      for (int j = 0; j < 3; j++) {
        positions[i * 3 + j] = Float.intBitsToFloat(data[offset + posOffset + j]);
      }

      for (int j = 0; j < 2; j++) {
        uvs[i * 2 + j] = Float.intBitsToFloat(data[offset + uvOffset + j]);
      }
    }

    return new ClientEnvProxy.QuadData() {
      @Override
      public float[] positions() {
        return positions;
      }

      @Override
      public float[] uvs() {
        return uvs;
      }

      @Override
      public int tint() {
        return quad.getTintIndex();
      }

      @Override
      public TextureAtlasSprite sprite() {
        return quad.getSprite();
      }
    };
  }

  interface QuadData {
    float[] positions();

    float[] uvs();

    int tint();

    TextureAtlasSprite sprite();
  }

  @FunctionalInterface
  interface BlockColor {
    int getColor(BlockState state, BlockAndTintGetter level, BlockPos pos, int tint);
  }

  @FunctionalInterface
  interface ItemColor {
    int getColor(ItemStack stack, int tint);
  }

  @FunctionalInterface
  interface ModelPredicate {
    float unclampedCall(ItemStack stack, ClientLevel level, LivingEntity entity, int seed);
  }

  interface ScreenFactory<H extends AbstractContainerMenu> {
    AbstractContainerScreen<H> create(H var1, Inventory var2, Component var3);
  }
}
