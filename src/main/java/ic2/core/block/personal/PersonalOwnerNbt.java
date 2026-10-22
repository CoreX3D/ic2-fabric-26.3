package ic2.core.block.personal;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Owner (de)serialization for personal blocks. ExtraCodecs.GAME_PROFILE cannot be used here: it
 * rejects the legacy NbtUtils.writeGameProfile format ("Id"/"Name") still present in old worlds,
 * and it refuses to encode owners whose names fail player-name validation (fake players such as
 * "[SomeMod]"). Either failure silently discards the owner of a block that only its owner can
 * remove.
 */
public final class PersonalOwnerNbt {
  private static final String OWNER_KEY = "ownerGameProfile";

  private PersonalOwnerNbt() {}

  public static void write(CompoundTag nbt, GameProfile owner) {
    if (owner == null) {
      return;
    }

    CompoundTag ownerNbt = new CompoundTag();
    ownerNbt.store("id", UUIDUtil.CODEC, owner.id());
    ownerNbt.putString("name", owner.name());
    nbt.put(OWNER_KEY, ownerNbt);
  }

  public static GameProfile read(CompoundTag nbt) {
    if (!(nbt.get(OWNER_KEY) instanceof CompoundTag tag)) {
      return null;
    }

    String name =
        tag.getString("name").isPresent() ? tag.getStringOr("name", "") : tag.getStringOr("Name", "");
    UUID id = null;
    if (tag.read("id", UUIDUtil.CODEC).isPresent()) {
      id = tag.read("id", UUIDUtil.CODEC).orElseThrow();
    } else {
      String rawId = tag.getString("id").isPresent() ? tag.getStringOr("id", "") : tag.getStringOr("Id", "");
      if (!rawId.isEmpty()) {
        try {
          id = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
          // unparseable id: fall back to the name-derived offline id below
        }
      }
    }

    if (id == null) {
      if (name.isEmpty()) {
        return null;
      }

      // offline servers assign exactly this id, so a name-only legacy owner still matches
      id = UUIDUtil.createOfflinePlayerUUID(name);
    }

    return new GameProfile(id, name);
  }
}
