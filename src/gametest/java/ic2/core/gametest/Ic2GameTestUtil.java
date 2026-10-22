package ic2.core.gametest;

import ic2.core.IC2;
import ic2.core.util.Keyboard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.clock.WorldClocks;

final class Ic2GameTestUtil {
  private Ic2GameTestUtil() {}

  /** Builds the context for right-clicking the given face of a block within the test structure. */
  static UseOnContext useOn(
      GameTestHelper helper, Player player, BlockPos relativePos, Direction side) {
    BlockPos pos = helper.absolutePos(relativePos);
    Vec3 hitVec = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(side.getUnitVec3i()).scale(0.5));
    return new UseOnContext(
        player, InteractionHand.MAIN_HAND, new BlockHitResult(hitVec, side, pos, false));
  }

  /** Simulates the player holding down the IC2 mode switch key (default M). */
  static void pressModeSwitchKey(Player player) {
    IC2.keyboard.processKeyUpdate(player, 1 << Keyboard.Key.modeSwitch.ordinal());
  }

  static void releaseModeSwitchKey(Player player) {
    IC2.keyboard.processKeyUpdate(player, 0);
  }

  static CompoundTag saveStack(GameTestHelper helper, ItemStack stack) {
    return (CompoundTag) ItemStack.CODEC.encodeStart(
        helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE), stack)
        .getOrThrow();
  }

  static void setDayTime(ServerLevel level, long ticks) {
    var clock = level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK)
        .getOrThrow(WorldClocks.OVERWORLD);
    level.clockManager().setTotalTicks(clock, ticks);
  }

  static void setWeatherParameters(
      ServerLevel level, int clearTicks, int weatherTicks, boolean raining, boolean thundering) {
    var weather = level.getWeatherData();
    weather.setClearWeatherTime(clearTicks);
    weather.setRainTime(weatherTicks);
    weather.setThunderTime(weatherTicks);
    weather.setRaining(raining);
    weather.setThundering(thundering);
  }
}
