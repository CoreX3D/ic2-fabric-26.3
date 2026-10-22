package ic2.fabric;

import ic2.core.fluid.Ic2FluidStack;
import ic2.core.proxy.ClientEnvProxy;
import java.io.File;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Client registrations backed by Fabric API instead of NeoForge lifecycle events. */
@Environment(EnvType.CLIENT)
public final class FabricClientEnvProxy implements ClientEnvProxy {
  private static final Map<Item, ItemColor> ITEM_COLORS = new IdentityHashMap<>();
  private static final Map<Item, Map<Identifier, ModelPredicate>> ITEM_PREDICATES = new IdentityHashMap<>();
  private static final Map<Identifier, ModelPredicate> GENERIC_PREDICATES = new HashMap<>();
  private static final Map<Block, ChunkSectionLayer> BLOCK_LAYERS = new IdentityHashMap<>();

  public static ItemColor getItemColor(Item item) { return ITEM_COLORS.get(item); }
  public static ModelPredicate getModelPredicate(Item item, Identifier id) {
    var predicates = ITEM_PREDICATES.get(item);
    var result = predicates == null ? null : predicates.get(id);
    return result == null ? GENERIC_PREDICATES.get(id) : result;
  }
  public static ChunkSectionLayer getBlockLayer(Block block) { return BLOCK_LAYERS.get(block); }
  @Override public <H extends AbstractContainerMenu> void registerScreen(MenuType<H> type, ScreenFactory<H> factory) {
    MenuScreens.register(type, factory::create);
  }
  @Override public File getMinecraftDir() { return FabricLoader.getInstance().getGameDir().toFile(); }
  @Override public void registerColorProvider(BlockColor provider, Block... blocks) {
    BlockColorRegistry.register((state, level, pos, tints) ->
        tints.add(0xFF000000 | provider.getColor(state, level, pos, 0)), blocks);
  }
  @Override public void registerColorProvider(ItemColor provider, ItemLike... items) {
    for (ItemLike item : items) ITEM_COLORS.put(item.asItem(), provider);
  }
  @Override public void registerKeyBinding(KeyMapping binding) { KeyMappingHelper.registerKeyMapping(binding); }
  @Override public <E extends BlockEntity, S extends BlockEntityRenderState> void registerBer(BlockEntityType<E> type, BlockEntityRendererProvider<? super E, ? super S> factory) {
    BlockEntityRendererRegistry.register(type, factory);
  }
  @Override public void registerModelPredicateProvider(Identifier id, ModelPredicate provider) {
    GENERIC_PREDICATES.put(id, provider);
  }
  @Override public void registerModelPredicateProvider(Item item, Identifier id, ModelPredicate provider) {
    ITEM_PREDICATES.computeIfAbsent(item, ignored -> new HashMap<>()).put(id, provider);
  }
  @Override public void registerBlockLayer(ChunkSectionLayer layer, Block... blocks) {
    for (Block block : blocks) BLOCK_LAYERS.put(block, layer);
  }
  @Override public <E extends Entity> void registerEntityRenderer(EntityType<? extends E> type, EntityRendererProvider<E> factory) {
    EntityRendererRegistry.register(type, factory);
  }
  @Override public <E extends BlockEntity, S extends BlockEntityRenderState> void registerBlockEntityRenderer(BlockEntityType<? extends E> type, BlockEntityRendererProvider<E, S> factory) {
    BlockEntityRendererRegistry.register(type, factory);
  }
  @Override public TextureAtlasSprite getFluidStillSprite(Ic2FluidStack stack) {
    return Minecraft.getInstance().getModelManager().getFluidStateModelSet()
        .get(stack.getFluid().defaultFluidState()).stillMaterial().sprite();
  }
  @Override public int getFluidColor(Ic2FluidStack stack) { return FluidVariantRendering.getColor(FluidVariant.of(stack.getFluid())); }
  @Override public String getFluidName(Ic2FluidStack stack) { return FluidVariantAttributes.getName(FluidVariant.of(stack.getFluid())).getString(); }
}
