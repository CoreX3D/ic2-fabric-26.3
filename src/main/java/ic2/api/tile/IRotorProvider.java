package ic2.api.tile;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

public interface IRotorProvider {
  int getRotorDiameter();

  Direction getFacing();

  float getAngle();

  default float getRotorAnimationSpeed() {
    return 0.0F;
  }

  Identifier getRotorRenderTexture();
}
