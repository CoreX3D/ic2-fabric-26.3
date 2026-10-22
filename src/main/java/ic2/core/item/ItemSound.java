package ic2.core.item;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/** Plays a tool response only for its user, without broadcasting or duplicating it nearby. */
public final class ItemSound {
  private ItemSound() {}

  public static void notifyPlayer(Player player, SoundEvent sound, SoundSource source,
      float volume, float pitch) {
    if (player instanceof ServerPlayer serverPlayer) {
      serverPlayer.connection.send(new ClientboundSoundPacket(Holder.direct(sound), source,
          player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
    } else {
      player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), sound,
          source, volume, pitch, false);
    }
  }
}
