package ic2.client.gametest;

import ic2.api.item.ElectricItem;
import ic2.core.Ic2Potion;
import ic2.core.item.ElectricItemManager;
import ic2.core.ref.Ic2Items;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Scheduled radiation ticks and ordinary inventory/health packets, without invoking damage directly. */
@Environment(EnvType.CLIENT)
@SuppressWarnings("UnstableApiUsage")
public final class RadiationEffectRenderChecks {
  private static final BlockPos BASE = new BlockPos(-44, 64, 48);
  private static final List<EquipmentSlot> ARMOR = List.of(
      EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
  private static final List<ArmorCase> VANILLA = List.of(
      new ArmorCase("unarmored", List.of(), 0),
      new ArmorCase("diamond", List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
          Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS), 20),
      new ArmorCase("netherite", List.of(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
          Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS), 20));
  private static final List<Item> NANO = List.of(Ic2Items.NANO_HELMET, Ic2Items.NANO_CHESTPLATE,
      Ic2Items.NANO_LEGGINGS, Ic2Items.NANO_BOOTS);

  private RadiationEffectRenderChecks() {}

  public static List<String> validate(ClientGameTestContext context, TestServerContext server,
      TestServerConnection connection) {
    Path directory = context.computeOnClient(client -> client.gameDirectory.toPath().toAbsolutePath().normalize());
    require(directory.endsWith(Path.of("build", "client-render-smoke")),
        "Radiation fixtures are permitted only in build/client-render-smoke");
    SavedPlayer saved = server.computeOnServer(instance -> {
      var player = connection.getServerPlayer();
      List<ItemStack> inventory = new ArrayList<>();
      for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
        inventory.add(player.getInventory().getItem(slot).copy());
      }
      return new SavedPlayer(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
          player.gameMode.getGameModeForPlayer(), player.getHealth(), player.getAbsorptionAmount(),
          player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel(),
          player.getAbilities().flying, player.isPermanentlyInvulnerable(), player.getInvulnerableTime(),
          player.getDeltaMovement(), inventory, ARMOR.stream().map(slot -> player.getItemBySlot(slot).copy()).toList(),
          player.getActiveEffects().stream().map(MobEffectInstance::new).toList());
    });
    List<String> evidence = new ArrayList<>();
    boolean fixtureCreated = false;
    try {
      server.runOnServer(instance -> {
        for (BlockPos position : platform()) require(connection.getServerLevel().isEmptyBlock(position),
            "Radiation platform would overwrite another scene: " + position);
      });
      fixtureCreated = true;
      server.runOnServer(instance -> {
        var level = connection.getServerLevel();
        for (BlockPos position : platform()) level.setBlock(position, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
        var player = connection.getServerPlayer();
        player.closeContainer();
        player.getInventory().clearContent();
        player.removeAllEffects();
        player.setGameMode(GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(10);
        player.getFoodData().setSaturation(0.0F);
        player.setAbsorptionAmount(0.0F);
        player.setPermanentlyInvulnerable(false);
        player.teleportTo(level, BASE.getX() + 0.5, BASE.getY(), BASE.getZ() + 0.5,
            Set.of(), 0.0F, 0.0F, true);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
      });
      connection.waitForChunksDownload();
      context.waitTicks(3);

      for (ArmorCase armor : VANILLA) {
        server.runOnServer(instance -> {
          var player = connection.getServerPlayer();
          resetDamageState(player);
          for (int index = 0; index < ARMOR.size(); index++) {
            player.setItemSlot(ARMOR.get(index), armor.items().isEmpty()
                ? ItemStack.EMPTY : new ItemStack(armor.items().get(index)));
          }
          player.inventoryMenu.broadcastChanges();
        });
        context.waitTicks(3);
        connection.waitForClientboundPackets();
        context.waitFor(client -> near(client.player.getHealth(), 20.0)
            && client.player.getArmorValue() == armor.armorValue()
            && !client.player.hasEffect(Ic2Potion.radiationHolder()));
        server.runOnServer(instance -> {
          var player = connection.getServerPlayer();
          require(player.getArmorValue() == armor.armorValue(),
              "Native " + armor.name() + " armor attributes were not equipped before radiation");
          // At duration 25 the native scheduler invokes the tick once. Duration 0 expires the effect.
          require(player.addEffect(new MobEffectInstance(Ic2Potion.radiationHolder(), 26, 0)),
              "Native player rejected scheduled " + armor.name() + " radiation effect");
        });
        context.waitTicks(4);
        connection.waitForClientboundPackets();
        context.waitFor(client -> near(client.player.getHealth(), 19.5));
        server.runOnServer(instance -> {
          var player = connection.getServerPlayer();
          require(near(player.getHealth(), 19.5), "Scheduled radiation did not inflict exactly 0.5 HP through "
              + armor.name() + " armor: " + player.getHealth());
          require(near(totalCharge(player), 0.0), "Ordinary armor unexpectedly gained or spent IC charge");
        });
        if (armor.items().isEmpty()) {
          context.runOnClient(client -> require(client.player.hasEffect(Ic2Potion.radiationHolder()),
              "Native client did not receive the active radiation status effect"));
          evidence.add("Radiation native HUD screenshot: " + context.takeScreenshot(
              TestScreenshotOptions.of("ic2-radiation-effect").disableCounterPrefix()).toAbsolutePath());
        }
        // Leave no second pulse or effect between separate armor controls.
        server.runOnServer(instance -> connection.getServerPlayer().removeEffect(Ic2Potion.radiationHolder()));
        connection.waitForClientboundPackets();
        context.waitFor(client -> !client.player.hasEffect(Ic2Potion.radiationHolder()));
        evidence.add("Radiation native effect tick, " + armor.name() + ": real connected survival player "
            + "received duration 26/amplifier 0; ordinary server ticks reduced health 20 -> 19.5. "
            + "Native client effect/health packets agreed; armor=" + armor.armorValue() + ".");
      }

      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        resetDamageState(player);
        for (int index = 0; index < ARMOR.size(); index++) {
          player.setItemSlot(ARMOR.get(index), ElectricItemManager.getCharged(NANO.get(index), 1000000.0));
        }
        player.inventoryMenu.broadcastChanges();
      });
      context.waitTicks(3);
      connection.waitForClientboundPackets();
      context.waitFor(client -> near(client.player.getHealth(), 20.0) && client.player.getArmorValue() == 20
          && near(totalCharge(client.player), 4000000.0));
      server.runOnServer(instance -> require(connection.getServerPlayer().addEffect(
          new MobEffectInstance(Ic2Potion.radiationHolder(), 1, 100)),
          "Native player rejected one-tick irradiated fuel-rod dose"));
      context.waitTicks(4);
      connection.waitForClientboundPackets();
      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        require(!player.hasEffect(Ic2Potion.radiationHolder()), "One-tick radiation dose failed to expire naturally");
        require(near(player.getHealth(), 19.85), "Full nanosuit did not absorb 90% of the native 1.5 HP radiation dose: "
            + player.getHealth());
        require(near(totalCharge(player), 3993250.0), "Full nanosuit did not spend exactly 6750 EU on one native radiation dose: "
            + totalCharge(player));
        player.inventoryMenu.broadcastChanges();
      });
      context.waitTicks(2);
      connection.waitForClientboundPackets();
      context.waitFor(client -> near(client.player.getHealth(), 19.85)
          && near(totalCharge(client.player), 3993250.0)
          && !client.player.hasEffect(Ic2Potion.radiationHolder()));
      evidence.add("Radiation native electric-armor control: a duration 1/amplifier 100 effect ran through the real "
          + "scheduler and expired; nanosuit absorbed 1.35 of 1.5 HP, health 20 -> 19.85 and total charge 4000000 -> "
          + "3993250 EU (6750 EU spent). Native equipped-stack/health packets delivered the same result to the client.");
      return evidence;
    } finally {
      boolean removeFixture = fixtureCreated;
      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        player.closeContainer();
        player.removeAllEffects();
        player.getInventory().clearContent();
        for (int slot = 0; slot < saved.inventory().size(); slot++) {
          player.getInventory().setItem(slot, saved.inventory().get(slot));
        }
        for (int index = 0; index < ARMOR.size(); index++) player.setItemSlot(ARMOR.get(index), saved.armor().get(index));
        player.setGameMode(saved.mode());
        player.getAbilities().flying = saved.flying();
        player.onUpdateAbilities();
        player.setPermanentlyInvulnerable(saved.invulnerable());
        player.setInvulnerableTime(saved.invulnerableTime());
        player.setHealth(saved.health());
        player.setAbsorptionAmount(saved.absorption());
        player.getFoodData().setFoodLevel(saved.food());
        player.getFoodData().setSaturation(saved.saturation());
        player.damageCooldownTime = 0;
        player.resetFallDistance();
        player.teleportTo(connection.getServerLevel(), saved.x(), saved.y(), saved.z(), Set.of(),
            saved.yaw(), saved.pitch(), true);
        player.setDeltaMovement(saved.velocity());
        for (MobEffectInstance effect : saved.effects()) player.addEffect(effect);
        player.inventoryMenu.broadcastChanges();
        if (removeFixture) for (BlockPos position : platform()) connection.getServerLevel()
            .setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
      });
      connection.waitForClientboundPackets();
      context.waitTick();
    }
  }

  private static void resetDamageState(Player player) {
    player.removeAllEffects();
    player.setHealth(20.0F);
    player.setAbsorptionAmount(0.0F);
    player.setInvulnerableTime(0);
    player.damageCooldownTime = 0;
  }

  private static double totalCharge(Player player) {
    return ARMOR.stream().mapToDouble(slot -> ElectricItem.manager.getCharge(player.getItemBySlot(slot))).sum();
  }

  private static List<BlockPos> platform() {
    List<BlockPos> result = new ArrayList<>();
    for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) result.add(BASE.offset(x, -1, z));
    return result;
  }

  private static boolean near(double actual, double expected) { return Math.abs(actual - expected) < 0.00001; }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  private record ArmorCase(String name, List<Item> items, int armorValue) {}

  private record SavedPlayer(double x, double y, double z, float yaw, float pitch, GameType mode,
      float health, float absorption, int food, float saturation, boolean flying, boolean invulnerable,
      int invulnerableTime, Vec3 velocity, List<ItemStack> inventory, List<ItemStack> armor,
      List<MobEffectInstance> effects) {}
}
