package ic2.core.block.wiring;

import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.IColoredEnergyTile;
import ic2.api.energy.tile.IEnergyAcceptor;
import ic2.api.energy.tile.IEnergyConductor;
import ic2.api.energy.tile.IEnergyEmitter;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergyTile;
import ic2.api.info.ILocatable;
import ic2.core.block.ChunkLoadAwareBlock;
import ic2.core.block.comp.Energy;
import ic2.core.block.misc.FoamBlock;
import ic2.core.block.reactor.tileentity.TileEntityNuclearReactorElectric;
import ic2.core.block.tileentity.Ic2TileEntity;
import ic2.core.item.tool.ItemToolCutter;
import ic2.core.ref.Ic2Fluids;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractCableBlock extends Block
    implements ChunkLoadAwareBlock, SimpleWaterloggedBlock, ic2.api.block.IPlayerBreakInterceptor {
  public static final DyeColor DEFAULT_COLOR = DyeColor.BLACK;
  public static final EnumProperty<DyeColor> colorProperty =
      EnumProperty.create("color", DyeColor.class);
  public static final EnumProperty<CableFoam> foamProperty =
      EnumProperty.create("foam", CableFoam.class);
  public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
  public static final BooleanProperty UP = BlockStateProperties.UP;
  public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
  public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
  public static final BooleanProperty EAST = BlockStateProperties.EAST;
  public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
  public static final BooleanProperty WEST = BlockStateProperties.WEST;
  public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION =
      Map.of(
          Direction.DOWN, DOWN,
          Direction.UP, UP,
          Direction.NORTH, NORTH,
          Direction.SOUTH, SOUTH,
          Direction.WEST, WEST,
          Direction.EAST, EAST);
  private static final Map<CableType, Int2ReferenceMap<AbstractCableBlock>> types =
      new EnumMap<>(CableType.class);
  private static final Map<AbstractCableBlock, AbstractCableBlock> foamToCable =
      new IdentityHashMap<>();
  private static boolean pendingHasColor;
  public final CableType type;
  final int insulation;
  private final boolean hasColor;
  private final VoxelShape[] cableShapes;


  protected AbstractCableBlock(Properties settings, CableType type, int insulation) {
    super(settings);
    if (insulation > type.maxInsulation) {
      throw new IllegalArgumentException("invalid insulation " + insulation + " for type " + type);
    }

    this.type = type;
    this.insulation = insulation;
    this.hasColor = insulation >= type.minColoredInsulation;
    // Foam has no connection properties. Vanilla PipeBlock eagerly evaluates all of them
    // in its constructor, so cable geometry is cached independently of the foam states.
    this.cableShapes = this.isFoam() ? null : makeCableShapes(type.getThickness(insulation));
    BlockState defaultState = this.stateDefinition.any();
    if (!this.isFoam()) {
      defaultState = defaultState.setValue(WATERLOGGED, false);
    }

    this.initializeState(defaultState);
    if (type.maxInsulation > 0) {
      types
          .computeIfAbsent(type, t -> new Int2ReferenceOpenHashMap<>(t.maxInsulation + 1))
          .put(insulation, this);
    }
  }

  protected static void prepareCreate(CableType type, int insulation) {
    pendingHasColor = insulation >= type.minColoredInsulation;
  }

  private static VoxelShape[] makeCableShapes(float thickness) {
    double min = (1.0 - thickness) / 2.0;
    double max = (1.0 + thickness) / 2.0;
    VoxelShape center = Shapes.box(min, min, min, max, max, max);
    Map<Direction, VoxelShape> arms =
        Map.of(
            Direction.DOWN, Shapes.box(min, 0.0, min, max, 0.5, max),
            Direction.UP, Shapes.box(min, 0.5, min, max, 1.0, max),
            Direction.NORTH, Shapes.box(min, min, 0.0, max, max, 0.5),
            Direction.SOUTH, Shapes.box(min, min, 0.5, max, max, 1.0),
            Direction.WEST, Shapes.box(0.0, min, min, 0.5, max, max),
            Direction.EAST, Shapes.box(0.5, min, min, 1.0, max, max));
    VoxelShape[] shapes = new VoxelShape[64];
    for (int connections = 0; connections < shapes.length; connections++) {
      VoxelShape shape = center;
      for (Direction direction : Direction.values()) {
        if ((connections & (1 << direction.ordinal())) != 0) {
          shape = Shapes.or(shape, arms.get(direction));
        }
      }
      shapes[connections] = shape;
    }
    return shapes;
  }

  protected static void registerFoamCounterpart(
      AbstractCableBlock foamBlock, AbstractCableBlock cableBlock) {
    foamToCable.put(foamBlock, cableBlock);
  }

  public AbstractCableBlock getCableCounterpart() {
    return foamToCable.get(this);
  }

  public BlockState toFoamState(BlockState cableState, AbstractCableBlock foamBlock) {
    return this.copyState(cableState, foamBlock).setValue(foamProperty, CableFoam.SOFT);
  }

  public static DyeColor getColor(BlockState state, CableType type, int insulation) {
    return insulation >= type.minColoredInsulation ? state.getValue(colorProperty) : DEFAULT_COLOR;
  }

  public abstract boolean isFoam();

  public abstract boolean isHardFoam(BlockState var1);

  public void initializeState(BlockState defaultState) {
    if (this.isFoam()) {
      defaultState = defaultState.setValue(foamProperty, FoamCableBlock.DEFAULT_FOAM);
    } else {
      defaultState =
          defaultState
              .setValue(UP, false)
              .setValue(DOWN, false)
              .setValue(NORTH, false)
              .setValue(EAST, false)
              .setValue(SOUTH, false)
              .setValue(WEST, false);
      if (this.hasColor()) {
        defaultState = defaultState.setValue(colorProperty, DEFAULT_COLOR);
      }
    }

    this.registerDefaultState(defaultState);
  }

  protected BlockState copyState(BlockState from, AbstractCableBlock to) {
    BlockState ret = to.defaultBlockState();
    if (to.insulation >= to.type.minColoredInsulation) {
      ret = ret.setValue(colorProperty, ((AbstractCableBlock) from.getBlock()).getColor(from));
    }

    if (to.isFoam()) {
      if (this.isFoam()) {
        ret = ret.setValue(foamProperty, from.getValue(foamProperty));
      }
    } else if (!this.isFoam()) {
      ret =
          ret.setValue(UP, from.getValue(UP))
              .setValue(DOWN, from.getValue(DOWN))
              .setValue(NORTH, from.getValue(NORTH))
              .setValue(EAST, from.getValue(EAST))
              .setValue(SOUTH, from.getValue(SOUTH))
              .setValue(WEST, from.getValue(WEST));
    }

    return ret;
  }

  protected BlockState toCableState(BlockState foamState, AbstractCableBlock cableBlock) {
    BlockState ret = cableBlock.defaultBlockState();
    if (cableBlock.hasColor() && this.hasColor()) {
      ret = ret.setValue(colorProperty, getColor(foamState));
    }

    if (cableBlock instanceof AbstractDetectorCableBlock
        && this instanceof AbstractDetectorCableBlock) {
      ret =
          ret.setValue(
              AbstractDetectorCableBlock.active,
              foamState.getValue(AbstractDetectorCableBlock.active));
    }

    if (cableBlock instanceof AbstractSplitterCableBlock
        && this instanceof AbstractSplitterCableBlock) {
      ret =
          ret.setValue(
              AbstractSplitterCableBlock.active,
              foamState.getValue(AbstractSplitterCableBlock.active));
    }

    return ret;
  }

  protected void createBlockStateDefinition(@NotNull Builder<Block, BlockState> builder) {
    if (this.isFoam()) {
      builder.add(foamProperty);
    } else {
      builder.add(WATERLOGGED, UP, DOWN, NORTH, EAST, SOUTH, WEST);
    }

    if (pendingHasColor) {
      builder.add(colorProperty);
    }
  }

  public @NotNull FluidState getFluidState(@NotNull BlockState state) {
    return !this.isFoam() && state.getValue(WATERLOGGED)
        ? Fluids.WATER.getSource(false)
        : Fluids.EMPTY.defaultFluidState();
  }

  public BlockState withConnectionStates(BlockState state, Level world, BlockPos pos) {
    boolean isConnectedDown = this.isConnectedWith(state, null, world, pos.below());
    boolean isConnectedUp = this.isConnectedWith(state, null, world, pos.above());
    boolean isConnectedNorth = this.isConnectedWith(state, null, world, pos.north());
    boolean isConnectedEast = this.isConnectedWith(state, null, world, pos.east());
    boolean isConnectedSouth = this.isConnectedWith(state, null, world, pos.south());
    boolean isConnectedWest = this.isConnectedWith(state, null, world, pos.west());
    return state
        .setValue(DOWN, isConnectedDown)
        .setValue(UP, isConnectedUp)
        .setValue(NORTH, isConnectedNorth)
        .setValue(EAST, isConnectedEast)
        .setValue(SOUTH, isConnectedSouth)
        .setValue(WEST, isConnectedWest);
  }

  public boolean isConnectedWith(
      BlockState state, BlockState neighborState, LevelReader world, BlockPos neighborPos) {
    if (neighborState == null) {
      neighborState = world.getBlockState(neighborPos);
    }

    if (neighborState.isAir()) {
      return false;
    }

    if (!(neighborState.getBlock() instanceof AbstractCableBlock neighborBlock)) {
      return isEuConnectable(world, neighborPos);
    } else if (neighborBlock.hasColor() && this.hasColor()) {
      DyeColor color = this.getColor(state);
      DyeColor neighborColor =
          getColor(neighborState, neighborBlock.type, neighborBlock.insulation);
      return color == DEFAULT_COLOR
          || neighborColor == DEFAULT_COLOR
          || neighborColor == this.getColor(state);
    } else {
      return true;
    }
  }

  private static boolean isEuConnectable(LevelReader world, BlockPos pos) {
    if (world.getBlockState(pos).isAir()) {
      return false;
    }

    if (world instanceof Level level && !level.isClientSide()) {
      IEnergyTile tile = EnergyNet.instance.getTile(level, pos);
      if (tile != null) {
        return !(tile instanceof IEnergyConductor);
      }
    }

    BlockEntity be = world.getBlockEntity(pos);
    if (be instanceof Ic2TileEntity ic2 && ic2.hasComponent(Energy.class)) {
      return true;
    }

    if (be instanceof TileEntityNuclearReactorElectric reactor) {
      return !reactor.isFluidCooled();
    }

    return be instanceof IEnergySink || be instanceof IEnergyEmitter;
  }

  public BlockState getStateForPlacement(BlockPlaceContext ctx) {
    BlockState ret = this.defaultBlockState();
    Fluid fluid = ctx.getLevel().getFluidState(ctx.getClickedPos()).getType();
    if (!this.isFoam() && fluid == Fluids.WATER) {
      ret = ret.setValue(WATERLOGGED, true);
    }

    if (!this.isFoam()) {
      ret = this.withConnectionStates(ret, ctx.getLevel(), ctx.getClickedPos());
    } else if (fluid == Ic2Fluids.CONSTRUCTION_FOAM.still()) {
      ret = ret.setValue(foamProperty, CableFoam.SOFT);
    }

    return ret;
  }

  @Override
  public @NotNull VoxelShape getOcclusionShape(@NotNull BlockState state) {
    if (!this.isFoam()) {
      return super.getOcclusionShape(state);
    } else {
      return state.getValue(foamProperty).isSoft()
          ? Shapes.empty()
          : super.getOcclusionShape(state);
    }
  }

  public @NotNull VoxelShape getShape(
      @NotNull BlockState state,
      @NotNull BlockGetter world,
      @NotNull BlockPos pos,
      @NotNull CollisionContext context) {
    if (!this.isFoam()) {
      int connections = 0;
      for (Map.Entry<Direction, BooleanProperty> entry : PROPERTY_BY_DIRECTION.entrySet()) {
        if (state.getValue(entry.getValue())) {
          connections |= 1 << entry.getKey().ordinal();
        }
      }
      return this.cableShapes[connections];
    } else {
      return state.getValue(foamProperty).isPresent() ? Shapes.block() : Shapes.empty();
    }
  }

  @Override
  protected boolean propagatesSkylightDown(BlockState state) {
    return false;
  }

  @Override
  public void neighborChanged(
      @NotNull BlockState state,
      @NotNull Level world,
      @NotNull BlockPos pos,
      @NotNull Block block,
      Orientation orientation,
      boolean notify) {
    super.neighborChanged(state, world, pos, block, orientation, notify);

    if (!this.isFoam() && !world.isClientSide()) {
      BlockState current = world.getBlockState(pos);
      BlockState newState = this.withConnectionStates(current, world, pos);
      if (newState != current) {
        world.setBlockAndUpdate(pos, newState);
      }
    }
  }

  @Override
  public @NotNull BlockState updateShape(
      @NotNull BlockState state,
      @NotNull LevelReader world,
      @NotNull ScheduledTickAccess ticks,
      @NotNull BlockPos pos,
      @NotNull Direction direction,
      @NotNull BlockPos neighborPos,
      @NotNull BlockState neighborState,
      @NotNull RandomSource random) {
    if (!this.isFoam() && state.getValue(WATERLOGGED)) {
      ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
    }

    if (!this.isFoam()) {
      boolean isConnected = this.isConnectedWith(state, neighborState, world, neighborPos);
      return state.setValue(PROPERTY_BY_DIRECTION.get(direction), isConnected);
    } else {
      return super.updateShape(state, world, ticks, pos, direction, neighborPos, neighborState, random);
    }
  }

  @Override
  public boolean canPlaceLiquid(
      LivingEntity entity,
      @NotNull BlockGetter world,
      @NotNull BlockPos pos,
      @NotNull BlockState state,
      @NotNull Fluid fluid) {
    return !this.isFoam() && !(Boolean) state.getValue(WATERLOGGED) && fluid == Fluids.WATER;
  }

  public boolean placeLiquid(
      @NotNull LevelAccessor world,
      @NotNull BlockPos pos,
      @NotNull BlockState state,
      FluidState fluidState) {
    if (!this.canPlaceLiquid(null, world, pos, state, fluidState.getType())) {
      return false;
    }

    if (!world.isClientSide()) {
      world.setBlock(pos, state.setValue(WATERLOGGED, true), 3);
      world.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(world));
    }

    return true;
  }

  @Override
  public @NotNull ItemStack pickupBlock(
      LivingEntity entity, @NotNull LevelAccessor world, @NotNull BlockPos pos, @NotNull BlockState state) {
    if (!this.isFoam() && state.getValue(WATERLOGGED)) {
      world.setBlock(pos, state.setValue(WATERLOGGED, false), 3);
      return new ItemStack(Items.WATER_BUCKET);
    } else {
      return ItemStack.EMPTY;
    }
  }

  private void scheduleFoamHardeningTick(Level world, BlockPos pos) {
    if (!world.isClientSide() && !world.getBlockTicks().hasScheduledTick(pos, this)) {
      world.scheduleTick(pos, this, 1);
    }
  }

  protected void tickFoamHardening(
      @NotNull BlockState state,
      @NotNull ServerLevel world,
      @NotNull BlockPos pos,
      @NotNull RandomSource random) {
    if (!this.isFoam() || !state.getValue(foamProperty).isSoft()) {
      return;
    }

    if (random.nextFloat()
        < FoamBlock.getHardenChance(world, pos, state, FoamBlock.FoamType.normal)) {
      world.setBlockAndUpdate(pos, state.setValue(foamProperty, CableFoam.DEFAULT_HARD));
    } else {
      world.scheduleTick(pos, this, 1);
    }
  }

  @Override
  public void tick(
      @NotNull BlockState state,
      @NotNull ServerLevel world,
      @NotNull BlockPos pos,
      @NotNull RandomSource random) {
    this.tickFoamHardening(state, world, pos, random);
  }

  /**
   * Breaking a foam-covered cable restores the underlying cable instead of dropping the foam.
   *
   * <p>NeoForge ran this from {@code Block#onDestroyedByPlayer}, whose {@code false} return
   * cancelled the removal. Vanilla 1.21.1 has no such hook, so the same logic is exposed through
   * {@link ic2.api.block.IPlayerBreakInterceptor} and invoked from the Fabric
   * {@code PlayerBlockBreakEvents.BEFORE} callback.
   */
  @Override
  public boolean onPlayerBreak(BlockState state, Level world, BlockPos pos, Player player) {
    if (!this.isFoam() || world.isClientSide()) {
      return false;
    }

    AbstractCableBlock cableBlock = this.getCableCounterpart();
    if (cableBlock == null) {
      return false;
    }

    BlockState cableState =
        cableBlock.withConnectionStates(this.toCableState(state, cableBlock), world, pos);
    world.setBlockAndUpdate(pos, cableState);
    return true;
  }

  @Override
  public @NotNull ItemStack getCloneItemStack(
      @NotNull net.minecraft.world.level.LevelReader level,
      @NotNull BlockPos pos,
      @NotNull BlockState state,
      boolean includeData) {
    if (this.isFoam()) {
      AbstractCableBlock cableBlock = this.getCableCounterpart();
      if (cableBlock != null) {
        return cableBlock.getCloneItemStack(level, pos, this.toCableState(state, cableBlock), includeData);
      }
    }

    return super.getCloneItemStack(level, pos, state, includeData);
  }

  @Override
  public @NotNull List<ItemStack> getDrops(
      @NotNull BlockState state, @NotNull LootParams.Builder builder) {
    if (this.isFoam()) {
      AbstractCableBlock cableBlock = this.getCableCounterpart();
      if (cableBlock != null) {
        return cableBlock.getDrops(this.toCableState(state, cableBlock), builder);
      }
    }

    return super.getDrops(state, builder);
  }

  public void attack(
      @NotNull BlockState state,
      @NotNull Level world,
      @NotNull BlockPos pos,
      @NotNull Player player) {
    if (!this.isHardFoam(state)) {
      ItemStack stack = player.getMainHandItem();
      Item item = stack.getItem();
      if (item instanceof ItemToolCutter) {
        ((ItemToolCutter) item)
            .removeInsulation(player, player.getUsedItemHand(), state, world, pos);
      }
    }
  }

  public double getLoss() {
    return this.type.loss;
  }

  public boolean hasColor() {
    return this.hasColor;
  }

  DyeColor getColor(BlockState state) {
    return getColor(state, this.type, this.insulation);
  }

  public boolean tryAddInsulation(BlockState state, Level world, BlockPos pos) {
    if (this.insulation >= this.type.maxInsulation) {
      return false;
    }

    if (this.isHardFoam(state)) {
      return false;
    }

    AbstractCableBlock newBlock = types.get(this.type).get(this.insulation + 1);
    if (newBlock == null) {
      return false;
    }

    world.setBlockAndUpdate(pos, this.copyState(state, newBlock));
    return true;
  }

  public boolean tryRemoveInsulation(
      BlockState state, Level world, BlockPos pos, boolean simulate) {
    if (this.insulation <= 0) {
      return false;
    }

    if (this.isHardFoam(state)) {
      return false;
    }

    AbstractCableBlock newBlock = types.get(this.type).get(this.insulation - 1);
    if (newBlock == null) {
      return false;
    }

    if (simulate) {
      return true;
    }

    BlockState newState = this.copyState(state, newBlock);
    IEnergyTile tile = null;
    if (this.insulation == this.type.minColoredInsulation
        && this.getColor(state) != DEFAULT_COLOR) {
      assert newBlock.getColor(newState) == DEFAULT_COLOR;
      if (!world.isClientSide()) {
        tile = EnergyNet.instance.getTile(world, pos);
        if (tile != null) {
          EnergyNet.instance.removeTile(tile);
        }
      }
    }

    world.setBlockAndUpdate(pos, newState);
    if (tile != null) {
      EnergyNet.instance.addTileUnchecked(tile);
    }

    return true;
  }

  @Override
  public void affectNeighborsAfterRemoval(
      @NotNull BlockState state,
      @NotNull ServerLevel world,
      @NotNull BlockPos pos,
      boolean moved) {
    BlockState newState = world.getBlockState(pos);
    if (!newState.is(this) || this.getColor(newState) != this.getColor(state)) {
      this.removeFromEnet(state, world, pos);
    }
    super.affectNeighborsAfterRemoval(state, world, pos, moved);
  }

  public void onPlace(
      BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
    if (!world.isClientSide()) {
      if (oldState.getBlock() instanceof AbstractCableBlock oldCable
          && (!oldState.is(this) || oldCable.getColor(oldState) != this.getColor(state))) {
        oldCable.removeFromEnet(oldState, world, pos);
      }
      this.addToEnet(state, world, pos, true);
    }
    if (this.isFoam() && state.getValue(foamProperty).isSoft()) {
      this.scheduleFoamHardeningTick(world, pos);
    }
  }

  @Override
  public void onLoad(BlockState state, Level world, BlockPos pos) {
    if (!world.isClientSide()) {
      this.addToEnet(state, world, pos, false);
    }
    if (this.isFoam() && state.getValue(foamProperty).isSoft()) {
      this.scheduleFoamHardeningTick(world, pos);
    }
  }

  @Override
  public void onUnload(BlockState state, Level world, BlockPos pos) {
    if (!world.isClientSide()) {
      this.removeFromEnet(state, world, pos);
    }
  }

  protected void addToEnet(BlockState state, Level world, BlockPos pos, boolean checkConflicting) {
    if (world.isClientSide()) {
      return;
    }

    IEnergyTile existing = EnergyNet.instance.getTile(world, pos);
    if (existing != null) {
      if (checkConflicting) {
        return;
      }

      EnergyNet.instance.removeTile(existing);
    }

    EnergyNet.instance.addLocatableTile(new AbstractCableBlock.Conductor(state, world, pos));
  }

  protected void removeFromEnet(BlockState state, Level world, BlockPos pos) {
    if (world.isClientSide()) {
      return;
    }

    IEnergyTile tile = EnergyNet.instance.getTile(world, pos);
    if (tile != null) {
      EnergyNet.instance.removeTile(tile);
    }
  }

  private final class Conductor implements ILocatable, IColoredEnergyTile, IEnergyConductor {
    private final Level world;
    private final BlockPos pos;
    private BlockState state;

    Conductor(BlockState state, Level world, BlockPos pos) {
      this.state = state;
      this.world = world;
      this.pos = pos.immutable();
    }

    @Override
    public Level getWorldObj() {
      return this.world;
    }

    @Override
    public BlockPos getPosition() {
      return this.pos;
    }

    @Override
    public DyeColor getColor(Direction side) {
      DyeColor color = AbstractCableBlock.getColor(
          this.state, AbstractCableBlock.this.type, AbstractCableBlock.this.insulation);
      // In the IC2 energy API, an unpainted (black) cable connects to every color.
      return color == DEFAULT_COLOR ? null : color;
    }

    @Override
    public boolean acceptsEnergyFrom(IEnergyEmitter emitter, Direction side) {
      return this.canInteractWith(emitter, side);
    }

    @Override
    public boolean emitsEnergyTo(IEnergyAcceptor receiver, Direction side) {
      return this.canInteractWith(receiver, side);
    }

    @Override
    public double getConductionLoss() {
      return AbstractCableBlock.this.type.loss;
    }

    @Override
    public double getInsulationEnergyAbsorption() {
      if (AbstractCableBlock.this.type.maxInsulation == 0) {
        return 2.147483647E9;
      } else {
        return AbstractCableBlock.this.type.capacity < 128
            ? EnergyNet.instance.getPowerFromTier(AbstractCableBlock.this.insulation)
            : EnergyNet.instance.getPowerFromTier(AbstractCableBlock.this.insulation + 1);
      }
    }

    @Override
    public double getInsulationBreakdownEnergy() {
      return 9001.0;
    }

    @Override
    public double getConductorBreakdownEnergy() {
      return AbstractCableBlock.this.type.capacity + 1;
    }

    @Override
    public void removeInsulation() {
      AbstractCableBlock.this.tryRemoveInsulation(this.state, this.world, this.pos, false);
    }

    @Override
    public void removeConductor() {
      world.playSound(
          null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 4.0F, 1.0F);
      world.addParticle(
          ParticleTypes.SMOKE,
          pos.getX() + new Random().nextFloat(),
          pos.getY() + 0.95F,
          pos.getZ() + new Random().nextFloat(),
          0.0,
          0.0,
          0.0);
      world.removeBlock(this.pos, false);
    }

    private void setState(BlockState state) {
      assert state != this.state;
      this.state = state;
      this.world.setBlockAndUpdate(this.pos, state);
    }
  }
}
