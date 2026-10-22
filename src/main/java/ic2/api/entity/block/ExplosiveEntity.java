package ic2.api.entity.block;

import ic2.core.Ic2Explosion;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.UUIDUtil;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.MovementEmission;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public abstract class ExplosiveEntity extends Entity {
  private static final EntityDataAccessor<Integer> FUSE =
      SynchedEntityData.defineId(ExplosiveEntity.class, EntityDataSerializers.INT);
  private static final int DEFAULT_FUSE = 80;
  public LivingEntity causingEntity;
  private UUID causingEntityId;
  public float explosivePower = 4.0F;
  public int radiationRange = 0;
  public float dropRate = 0.3F;
  public float damageVsEntities = 1.0F;
  public BlockState renderBlockState;

  public ExplosiveEntity(
      EntityType<? extends Entity> type,
      Level world,
      double x,
      double y,
      double z,
      int fuse,
      float power,
      float dropRate,
      float damage,
      BlockState renderBlockState,
      int radiationRange) {
    this(type, world);
    this.setPos(x, y, z);
    RandomSource rng = RandomSource.create();
    double angle = (Math.PI * 2) * rng.nextDouble();
    this.setDeltaMovement(-Math.sin(angle) * 0.02, 0.2, -Math.cos(angle) * 0.02);
    this.xo = x;
    this.yo = y;
    this.zo = z;
    this.setFuse(fuse);
    this.explosivePower = power;
    this.radiationRange = radiationRange;
    this.dropRate = dropRate;
    this.damageVsEntities = damage;
    this.renderBlockState = renderBlockState;
  }

  public ExplosiveEntity(EntityType<? extends Entity> entityType, Level world) {
    super(entityType, world);
    this.blocksBuilding = true;
  }

  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    builder.define(FUSE, 80);
  }

  public boolean canBeCollidedWith(Entity entity) {
    return !this.isRemoved();
  }

  public void tick() {
    if (this.causingEntity == null && this.causingEntityId != null && this.level() instanceof ServerLevel level) {
      Entity source = level.getEntity(this.causingEntityId);
      if (source instanceof LivingEntity living) this.causingEntity = living;
    }
    if (!this.isNoGravity()) {
      this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.04, 0.0));
    }

    this.move(MoverType.SELF, this.getDeltaMovement());
    this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
    if (this.onGround()) {
      this.setDeltaMovement(this.getDeltaMovement().multiply(0.7, -0.5, 0.7));
    }

    int i = this.getFuse() - 1;
    this.setFuse(i);
    if (i <= 0) {
      this.discard();
      if (!this.level().isClientSide()) {
        this.explode();
      }
    } else {
      this.updateFluidInteraction();
      if (this.level().isClientSide()) {
        this.level()
            .addParticle(
                ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.0, 0.0);
      }
    }
  }

  private void explode() {
    Ic2Explosion explosion =
        new Ic2Explosion(
            this.level(),
            this,
            this.getX(),
            this.getY(),
            this.getZ(),
            this.explosivePower,
            this.dropRate,
            this.radiationRange > 0 ? Ic2Explosion.Type.Nuclear : Ic2Explosion.Type.Normal,
            this.causingEntity,
            this.radiationRange);
    explosion.doExplosion();
  }

  @Nullable public LivingEntity getCausingEntity() {
    return this.causingEntity;
  }

  public ExplosiveEntity setCausingEntity(LivingEntity igniter1) {
    this.causingEntity = igniter1;
    return this;
  }

  protected MovementEmission getMovementEmission() {
    return MovementEmission.NONE;
  }

  public boolean isPickable() {
    return !this.isRemoved();
  }

  protected void addAdditionalSaveData(ValueOutput nbt) {
    nbt.putShort("Fuse", (short) this.getFuse());
    nbt.putFloat("Power", this.explosivePower);
    nbt.putInt("RadiationRange", this.radiationRange);
    nbt.putFloat("DropRate", this.dropRate);
    nbt.putFloat("DamageVsEntities", this.damageVsEntities);
    nbt.storeNullable("Owner", UUIDUtil.CODEC, this.causingEntity != null ? this.causingEntity.getUUID() : this.causingEntityId);
  }

  protected void readAdditionalSaveData(ValueInput nbt) {
    this.setFuse(nbt.getShortOr("Fuse", (short) DEFAULT_FUSE));
    this.explosivePower = nbt.getFloatOr("Power", this.explosivePower);
    this.radiationRange = nbt.getIntOr("RadiationRange", this.radiationRange);
    this.dropRate = nbt.getFloatOr("DropRate", this.dropRate);
    this.damageVsEntities = nbt.getFloatOr("DamageVsEntities", this.damageVsEntities);
    this.causingEntityId = nbt.read("Owner", UUIDUtil.CODEC).orElse(null);
  }

  @Override
  public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    // Primed explosives, like vanilla TNT, cannot be destroyed by ordinary damage.
    return false;
  }

  public int getFuse() {
    return (Integer) this.entityData.get(FUSE);
  }

  public void setFuse(int fuse) {
    this.entityData.set(FUSE, fuse);
  }
}
