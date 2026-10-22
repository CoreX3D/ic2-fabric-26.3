package ic2.mixin;

import ic2.api.entity.boat.AbstractBoatEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Connects IC's fluid buoyancy and electric acceleration to the private vanilla boat physics. */
@Mixin(AbstractBoat.class)
public abstract class AbstractBoatMixin {
  @Unique private Vec3 ic2$movementBeforeSteering;

  @Inject(method = "getStatus", at = @At("HEAD"), cancellable = true)
  private void ic2$boatFluidStatus(CallbackInfoReturnable<AbstractBoat.Status> callback) {
    if ((Object) this instanceof AbstractBoatEntity boat) callback.setReturnValue(boat.getBoatStatus());
  }

  @Inject(method = "controlBoat", at = @At("HEAD"))
  private void ic2$beforeSteering(CallbackInfo callback) {
    if ((Object) this instanceof AbstractBoatEntity boat) this.ic2$movementBeforeSteering = boat.getDeltaMovement();
  }

  @Inject(method = "controlBoat", at = @At("TAIL"))
  private void ic2$electricAcceleration(CallbackInfo callback) {
    if ((Object) this instanceof AbstractBoatEntity boat && this.ic2$movementBeforeSteering != null) {
      Vec3 movement = boat.getDeltaMovement();
      Vec3 acceleration = movement.subtract(this.ic2$movementBeforeSteering);
      double factor = boat.getAccelerationFactor();
      boat.setDeltaMovement(movement.add(acceleration.x * (factor - 1.0), 0.0, acceleration.z * (factor - 1.0)));
    }
  }
}
