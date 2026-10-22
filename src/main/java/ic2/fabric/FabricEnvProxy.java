package ic2.fabric;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import ic2.api.crops.CropCard;
import ic2.api.crops.Crops;
import ic2.api.energy.ProfileEvent;
import ic2.api.event.ExplosionEvent;
import ic2.api.event.Ic2EventBus;
import ic2.api.event.RetextureEvent;
import ic2.api.item.IElectricItem;
import ic2.core.IC2;
import ic2.core.Ic2ItemGroupType;
import ic2.core.fluid.EnvFluidHandler;
import ic2.core.item.BlockItemEnergyStorage;
import ic2.core.item.ElectricItemManager;
import ic2.core.item.EnvItemHandler;
import ic2.core.item.ItemCropSeed;
import ic2.core.item.armor.ItemArmorFluidTank;
import ic2.core.network.GrowingBuffer;
import ic2.core.proxy.EnvProxy;
import ic2.core.ref.Ic2Items;
import ic2.core.util.StackUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.level.storage.loot.providers.number.floats.ResolvableFloat;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.phys.Vec3;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric implementation of {@link EnvProxy}.
 *
 * <p>This replaces {@code ic2.forge.EnvProxyForge}. NeoForge deferred every registration to the
 * {@code RegisterEvent} of the matching registry and shipped a {@code FluidType} registry,
 * extended-menu support and a fake-player factory of its own. Fabric has none of those, so
 * registration targets the vanilla registries directly during {@code onInitialize} (which runs
 * before the registries freeze), menus use Fabric's extended screen handlers, and the remaining
 * NeoForge services are mapped onto the Fabric API equivalents noted at each member.
 */
public final class FabricEnvProxy implements EnvProxy {
  private static final boolean IS_CLIENT =
      FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;

  /** Work needing item stacks, deferred until the registries and item components are bound. */
  private static final List<Runnable> afterRegistryInit = new ArrayList<>();

  /** Placed features IC2 asked for; see {@link #registerPlacedFeature}. */
  private static final java.util.Map<Identifier, PlacedFeature> REQUESTED_PLACED_FEATURES =
      new java.util.concurrent.ConcurrentHashMap<>();

  private static boolean registryInitComplete;

  /** Runs once after vanilla binds item components, including on a remote client's first join. */
  public static synchronized void completeRegistryInit() {
    if (registryInitComplete) return;
    registryInitComplete = true;
    List<Runnable> pending = new ArrayList<>(afterRegistryInit);
    afterRegistryInit.clear();
    for (Runnable runnable : pending) {
      runnable.run();
    }
  }

  @Override
  public boolean isClientEnv() {
    return IS_CLIENT;
  }

  @Override
  public boolean isFabricEnv() {
    return true;
  }

  @Override
  public boolean isForgeEnv() {
    return false;
  }

  @Override
  public MinecraftServer getServer() {
    return FabricLifecycle.getServer();
  }

  @Override
  public void registerBlock(Identifier id, Block block) {
    Registry.register(BuiltInRegistries.BLOCK, id, block);
  }

  @Override
  public <T extends BlockEntity> BlockEntityType<T> registerBlockEntity(
      Identifier id, BiFunction<BlockPos, BlockState, T> factory, Block... blocks) {
    BlockEntityType<T> type = FabricBlockEntityTypeBuilder.create(factory::apply, blocks).build();
    return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, type);
  }

  @Override
  public <T extends AbstractContainerMenu> MenuType<T> registerScreenHandler(
      Identifier id, BiFunction<Integer, Inventory, T> factory) {
    MenuType<T> type = new MenuType<>(factory::apply, FeatureFlags.DEFAULT_FLAGS);
    return Registry.register(BuiltInRegistries.MENU, id, type);
  }

  @Override
  public <T extends AbstractContainerMenu> MenuType<T> registerExtendedScreenHandler(
      Identifier id, EnvProxy.ExtendedClientScreenHandlerFactory<T> factory) {
    // Fabric carries the extra opening data as a typed payload rather than raw bytes.
    ExtendedMenuType<T, byte[]> type =
        new ExtendedMenuType<>(
            (syncId, inventory, data) ->
                factory.create(syncId, inventory, Unpooled.wrappedBuffer(data)),
            ByteBufCodecs.BYTE_ARRAY);
    return Registry.register(BuiltInRegistries.MENU, id, type);
  }

  @Override
  public void registerItem(Identifier id, Item item) {
    Registry.register(BuiltInRegistries.ITEM, id, item);
  }

  @Override
  public void registerEntity(Identifier id, EntityType<?> type) {
    Registry.register(BuiltInRegistries.ENTITY_TYPE, id, type);
  }

  @Override
  public WoodType registerSignType(String name) {
    return WoodType.register(new WoodType("ic2:" + name, BlockSetType.OAK));
  }

  @Override
  public void registerStatusEffect(Identifier id, MobEffect effect) {
    Registry.register(BuiltInRegistries.MOB_EFFECT, id, effect);
  }

  @Override
  public void registerFlammableBlock(Block block, int burn, int spread) {
    FlammableBlockRegistry.getDefaultInstance().add(block, burn, spread);
  }

  @Override
  public SoundEvent registerSoundEvent(String id) {
    Identifier identifier = IC2.getIdentifier(id);
    SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(identifier);
    return Registry.register(BuiltInRegistries.SOUND_EVENT, identifier, soundEvent);
  }

  @Override
  public GameEvent registerGameEvent(String id, int range) {
    Identifier identifier = IC2.getIdentifier(id);
    return Registry.register(BuiltInRegistries.GAME_EVENT, identifier, new GameEvent(range));
  }

  @Override
  public CompletableFuture<Holder<Feature>> registerConfiguredFeature(
      Identifier id, Feature feature) {
    // Registry-backed instances load from data/<namespace>/worldgen/feature. A direct
    // holder remains available to callers constructing a feature before datapack loading.
    return CompletableFuture.completedFuture(Holder.direct(feature));
  }

  @Override
  public void registerPlacedFeature(
      Identifier id,
      CompletableFuture<Holder<Feature>> feature,
      List<PlacementModifier> modifiers) {
    // As with registerConfiguredFeature, placed features live in a datapack registry, so the object
    // is built and retained here for inspection rather than inserted into the registry.
    afterRegistryInit.add(
        () -> REQUESTED_PLACED_FEATURES.put(id, buildPlacedFeature(feature.join(), modifiers)));
  }

  private static PlacedFeature buildPlacedFeature(
      Holder<Feature> feature, List<PlacementModifier> modifiers) {
    return new PlacedFeature(feature, List.copyOf(modifiers));
  }

  @Override
  public void attachPlacedFeatureToBiome(
      Identifier id, EnvProxy.BiomeSelector selector, Decoration step) {
    FabricBiomeModifications.add(id, selector, step);
  }

  @Override
  public void registerPlacementModifierType(
      Identifier id, MapCodec<? extends PlacementModifier> type) {
    Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, id, type);
  }

  @Override
  public <T extends FoliagePlacer> FoliagePlacerType<T> registerFoliagePlacer(
      Identifier id, MapCodec<T> codec) {
    return Registry.register(
        BuiltInRegistries.FOLIAGE_PLACER_TYPE, id, new FoliagePlacerType<>(codec));
  }

  @Override
  public <T extends Recipe<?>> RecipeType<T> registerRecipeType(Identifier id) {
    // Vanilla 1.21.1 only ships RecipeType.register(String), which also registers; NeoForge's
    // RecipeType.simple(id) built an unregistered instance, so the same is done here.
    RecipeType<T> type =
        new RecipeType<T>() {
          @Override
          public String toString() {
            return id.toString();
          }
        };
    return Registry.register(BuiltInRegistries.RECIPE_TYPE, id, type);
  }

  @Override
  public void registerRecipeSerializer(Identifier id, RecipeSerializer<?> serializer) {
    Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id, serializer);
  }

  @Override
  public void runAfterRegistryInit(Runnable runnable) {
    synchronized (FabricEnvProxy.class) {
      if (registryInitComplete) {
        runnable.run();
      } else {
        afterRegistryInit.add(runnable);
      }
    }
  }

  @Override
  public CreativeModeTab createItemGroup(
      Identifier id, Supplier<ItemStack> iconSupplier, Ic2ItemGroupType groupType) {
    CreativeModeTab tab =
        CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup." + id.getNamespace() + "." + id.getPath()))
            .icon(iconSupplier)
            .displayItems(
                (params, output) -> {
                  List<Supplier<Item>> items = Ic2Items.CREATIVE_TAB_ITEMS.get(groupType);
                  if (items != null) {
                    items.sort(
                        Comparator.comparing(
                            s -> BuiltInRegistries.ITEM.getKey(s.get()).toString()));
                    for (Supplier<Item> itemSupplier : items) {
                      Item item = itemSupplier.get();
                      output.accept(new ItemStack(item));
                      if (item instanceof IElectricItem) {
                        output.accept(
                            ElectricItemManager.getCharged(item, Double.POSITIVE_INFINITY));
                      }
                      if (item instanceof BlockItemEnergyStorage energyItem) {
                        ItemStack chargedStack = new ItemStack(item);
                        StackUtil.updateNbtData(chargedStack,
                            tag -> tag.putDouble("energy", energyItem.maxEnergy));
                        output.accept(chargedStack);
                      }
                      if (item instanceof ItemArmorFluidTank tankItem) {
                        ItemStack filledStack = new ItemStack(item);
                        tankItem.fillTank(filledStack);
                        output.accept(filledStack);
                      }
                    }
                  }

                  if (groupType == Ic2ItemGroupType.FARMING) {
                    for (CropCard crop : Crops.instance.getCrops()) {
                      output.accept(ItemCropSeed.generateItemStackFromValues(crop, 1, 1, 1, 4));
                    }
                  }
                })
            .build();
    return Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id, tab);
  }

  @Override
  public EnvFluidHandler createFluidStackHandler() {
    if (IS_CLIENT) return ClientServices.createFluidHandler();
    return new FabricFluidHandler();
  }

  /** Keeps client subclasses out of the common factory's verification and stack-map frames. */
  @Environment(EnvType.CLIENT)
  private static final class ClientServices {
    static EnvFluidHandler createFluidHandler() { return new FabricClientFluidHandler(); }
  }

  @Override
  public EnvItemHandler createItemHandler() {
    return new FabricItemHandler();
  }

  @Override
  public float getBlastResistance(
      BlockState state, BlockGetter world, BlockPos pos, Explosion explosion) {
    // NeoForge added Block#getExplosionResistance(state, level, pos, explosion); vanilla exposes
    // the block-level value without context.
    return state.getBlock().getExplosionResistance();
  }

  @Override
  public boolean isFlammable(BlockState state, Level world, BlockPos pos, Direction side) {
    // NeoForge patched BlockState#isFlammable(BlockGetter, BlockPos, Direction) onto vanilla.
    // Vanilla 1.21.1 keeps the fire table private inside FireBlock, and Fabric exposes the same
    // table as FlammableBlockRegistry, so a non-zero burn chance is the equivalent test.
    FlammableBlockRegistry.Entry entry =
        FlammableBlockRegistry.getDefaultInstance().get(state.getBlock());
    return entry.getBurnOdds() > 0;
  }

  @Override
  public BlockState rotate(BlockState state, LevelAccessor world, BlockPos pos, Rotation rotation) {
    // NeoForge added BlockState#rotate(LevelAccessor, BlockPos, Rotation) for context-aware
    // rotation. Vanilla 1.21.1 keeps only the state-only overload.
    return state.rotate(rotation);
  }

  @Override
  public boolean hasRecipeRemainder(ItemStack stack) {
    return stack.getItem().getCraftingRemainder() != null;
  }

  @Override
  public ItemStack getRecipeRemainder(ItemStack stack) {
    var remainder = stack.getItem().getCraftingRemainder();
    return remainder == null ? ItemStack.EMPTY : remainder.create();
  }

  @Override
  public void registerBurnTime(ItemLike stack, int value) {
    DefaultItemComponentEvents.MODIFY.register(context ->
        context.modify(stack.asItem(), components ->
            components.set(DataComponents.COOKING_FUEL,
                new CookingFuel(new ResolvableInt.Constant(value),
                    new ResolvableFloat.Constant(1.0F)))));
  }

  @Override
  public int getBurnTime(ItemStack stack) {
    CookingFuel fuel = stack.get(DataComponents.COOKING_FUEL);
    if (fuel == null) return 0;
    if (fuel.burnTime() instanceof ResolvableInt.Constant constant) return constant.value();
    MinecraftServer server = getServer();
    if (server == null) return 0;
    // This legacy ItemInfo query has no machine position. Use an empty context for
    // registry-backed item fuel values; actual providers still resolve through server data.
    LootContext context = new LootContext.Builder(
        new LootParams.Builder(server.overworld()).create(LootContextParamSets.EMPTY))
        .create(java.util.Optional.empty());
    return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL,
        CookingFuel::burnTime, context, 0);
  }

  @Override
  public boolean biomeHasType(Holder<Biome> biome, EnvProxy.BiomeType type) {
    TagKey<Biome> tag = switch (type) {
      case COLD -> ConventionalBiomeTags.IS_COLD;
      case HOT -> ConventionalBiomeTags.IS_HOT;
      case DRY -> ConventionalBiomeTags.IS_DRY;
      case WET -> ConventionalBiomeTags.IS_WET;
      case DENSE -> ConventionalBiomeTags.IS_VEGETATION_DENSE;
      case SPARSE -> ConventionalBiomeTags.IS_VEGETATION_SPARSE;
      case RARE -> ConventionalBiomeTags.IS_RARE;
      case PLATEAU -> ConventionalBiomeTags.IS_PLATEAU;
      case MODIFIED -> TagKey.create(Registries.BIOME,
          Identifier.fromNamespaceAndPath("c", "is_modified"));
      case END -> ConventionalBiomeTags.IS_END;
      case NETHER -> ConventionalBiomeTags.IS_NETHER;
      case OVERWORLD -> ConventionalBiomeTags.IS_OVERWORLD;
      case BEACH -> ConventionalBiomeTags.IS_BEACH;
      case CONIFEROUS -> ConventionalBiomeTags.IS_CONIFEROUS_TREE;
      case DEAD -> ConventionalBiomeTags.IS_DEAD;
      case FOREST -> ConventionalBiomeTags.IS_FOREST;
      case HILLS -> ConventionalBiomeTags.IS_HILL;
      case JUNGLE -> ConventionalBiomeTags.IS_JUNGLE;
      case LUSH -> ConventionalBiomeTags.IS_LUSH;
      case MAGICAL -> ConventionalBiomeTags.IS_MAGICAL;
      case MESA -> ConventionalBiomeTags.IS_BADLANDS;
      case MOUNTAIN -> ConventionalBiomeTags.IS_MOUNTAIN;
      case MUSHROOM -> ConventionalBiomeTags.IS_MUSHROOM;
      case OCEAN -> ConventionalBiomeTags.IS_OCEAN;
      case PLAINS -> ConventionalBiomeTags.IS_PLAINS;
      case RIVER -> ConventionalBiomeTags.IS_RIVER;
      case SANDY -> ConventionalBiomeTags.IS_SANDY;
      case SAVANNA -> ConventionalBiomeTags.IS_SAVANNA;
      case SNOWY -> ConventionalBiomeTags.IS_SNOWY;
      case SPOOKY -> ConventionalBiomeTags.IS_SPOOKY;
      case SWAMP -> ConventionalBiomeTags.IS_SWAMP;
      case VOID -> ConventionalBiomeTags.IS_VOID;
      case WASTELAND -> ConventionalBiomeTags.IS_WASTELAND;
      case WATER -> ConventionalBiomeTags.IS_AQUATIC;
    };
    return biome.is(tag);
  }

  @Override
  public Collection<EnvProxy.BiomeType> getBiomeTypes(Holder<Biome> biome) {
    java.util.EnumSet<EnvProxy.BiomeType> types = java.util.EnumSet.noneOf(EnvProxy.BiomeType.class);
    for (EnvProxy.BiomeType type : EnvProxy.BiomeType.values()) {
      if (biomeHasType(biome, type)) types.add(type);
    }
    return types;
  }

  @Override
  public boolean openHandledScreen(Player player, MenuProvider factory, GrowingBuffer data) {
    if (!(player instanceof ServerPlayer serverPlayer)) {
      return false;
    }

    byte[] payload = encodeScreenData(serverPlayer, data);
    serverPlayer.openMenu(
        new ExtendedMenuProvider<byte[]>() {
          @Override
          public Component getDisplayName() {
            return factory.getDisplayName();
          }

          @Override
          public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player owner) {
            return factory.createMenu(syncId, inventory, owner);
          }

          @Override
          public byte[] getScreenOpeningData(ServerPlayer opening) {
            return payload;
          }
        });
    return true;
  }

  private static byte[] encodeScreenData(ServerPlayer player, GrowingBuffer data) {
    RegistryFriendlyByteBuf buffer =
        new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
    try {
      data.writeTo(buffer);
      byte[] bytes = new byte[buffer.readableBytes()];
      buffer.readBytes(bytes);
      return bytes;
    } finally {
      buffer.release();
    }
  }

  @Override
  public boolean isFakePlayer(Player entity) {
    return entity instanceof FakePlayer;
  }

  @Override
  public Player createFakePlayer(ServerLevel world, GameProfile profile) {
    return FakePlayer.get(world, profile);
  }

  @Override
  public void announceProfileLoad(Set<String> loaded, String active) {
    Ic2EventBus.INSTANCE.post(new ProfileEvent.Load(loaded, active));
  }

  @Override
  public void announceProfileSwitch(String from, String to) {
    Ic2EventBus.INSTANCE.post(new ProfileEvent.Switch(from, to));
  }

  @Override
  public boolean announceRetexture(
      Level world,
      BlockPos pos,
      BlockState state,
      Direction side,
      Player player,
      BlockState refState,
      String refVariant,
      Direction refSide,
      int[] refColorMultipliers) {
    RetextureEvent event =
        Ic2EventBus.INSTANCE.post(
            new RetextureEvent(
                world, pos, state, side, player, refState, refVariant, refSide, refColorMultipliers));
    return event.applied;
  }

  @Override
  public boolean announceExplosion(
      Level world,
      Entity entity,
      Vec3 pos,
      double power,
      LivingEntity igniter,
      int radiationRange,
      double rangeLimit) {
    ExplosionEvent event =
        Ic2EventBus.INSTANCE.post(
            new ExplosionEvent(world, entity, pos, power, igniter, radiationRange, rangeLimit));
    return !event.isCanceled();
  }
}
