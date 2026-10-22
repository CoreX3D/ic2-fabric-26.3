package ic2.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import ic2.core.event.EventHandlerClient;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Lets IC2 substitute a sound before it is played.
 *
 * <p>NeoForge exposed {@code PlaySoundEvent}, whose handler could replace the {@code SoundInstance}.
 * Fabric has no sound playback callback, so this wraps {@code SoundEngine#play}. A null result
 * cancels the original sound; IC2 has already queued its replacement. Non-null replacements
 * continue through the native engine without re-entering {@code play}.
 *
 * <p>The native 26.3 engine does not accept null sound instances, so cancellation must return
 * {@code NOT_STARTED} before its first instance access.
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {
  @WrapMethod(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;")
  private SoundEngine.PlayResult ic2$replaceSound(SoundInstance sound,
      Operation<SoundEngine.PlayResult> original) {
    SoundInstance replacement = EventHandlerClient.onSoundPlayed(sound);
    return replacement == null ? SoundEngine.PlayResult.NOT_STARTED : original.call(replacement);
  }
}
