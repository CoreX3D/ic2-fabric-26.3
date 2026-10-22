package ic2.client.gametest;

import com.mojang.authlib.GameProfile;
import ic2.api.item.ElectricItem;
import ic2.core.IC2;
import ic2.core.item.ElectricItemManager;
import ic2.core.ref.Ic2Items;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Native attacks, fall handling and equipped-stack packets for the real connected test player. */
@Environment(EnvType.CLIENT)
@SuppressWarnings("UnstableApiUsage")
public final class ElectricArmorRenderChecks {
  private static final BlockPos BASE = new BlockPos(-36, 64, 48);
  private static final List<EquipmentSlot> ARMOR = List.of(
      EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
  private static final List<Item> NANO = List.of(Ic2Items.NANO_HELMET, Ic2Items.NANO_CHESTPLATE,
      Ic2Items.NANO_LEGGINGS, Ic2Items.NANO_BOOTS);
  private static final List<Item> QUANTUM = List.of(Ic2Items.QUANTUM_HELMET, Ic2Items.QUANTUM_CHESTPLATE,
      Ic2Items.QUANTUM_LEGGINGS, Ic2Items.QUANTUM_BOOTS);
  private static final GameProfile ATTACKER = new GameProfile(
      UUID.fromString("49796c92-49c4-48e0-aaf3-26fb828da1f9"), "IC2ArmorClient");

  private ElectricArmorRenderChecks() {}

  public static List<String> validate(ClientGameTestContext context, TestServerContext server,
      TestServerConnection connection) {
    Path directory = context.computeOnClient(client -> client.gameDirectory.toPath().toAbsolutePath().normalize());
    require(directory.endsWith(Path.of("build", "client-render-smoke")),
        "Electric armor fixtures are permitted only in build/client-render-smoke");
    SavedPlayer saved = server.computeOnServer(instance -> {
      var player = connection.getServerPlayer();
      List<ItemStack> inventory = new ArrayList<>();
      for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
        inventory.add(player.getInventory().getItem(slot).copy());
      }
      return new SavedPlayer(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
          player.gameMode.getGameModeForPlayer(), player.getHealth(), player.getAbsorptionAmount(),
          player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel(), inventory,
          ARMOR.stream().map(slot -> player.getItemBySlot(slot).copy()).toList());
    });
    List<String> evidence = new ArrayList<>();
    boolean fixtureCreated = false;
    try {
      server.runOnServer(instance -> {
        for (BlockPos position : platform()) require(connection.getServerLevel().isEmptyBlock(position),
            "Electric armor platform would overwrite another scene: " + position);
      });
      fixtureCreated = true;
      server.runOnServer(instance -> {
        var level = connection.getServerLevel();
        for (BlockPos position : platform()) level.setBlock(position, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
        var player = connection.getServerPlayer();
        player.closeContainer();
        player.getInventory().clearContent();
        player.setGameMode(GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(10);
        player.getFoodData().setSaturation(0.0F);
        player.setAbsorptionAmount(0.0F);
        player.setHealth(20.0F);
        player.setPermanentlyInvulnerable(false);
        player.setInvulnerableTime(0);
        player.damageCooldownTime = 0;
        player.teleportTo(level, BASE.getX() + 0.5, BASE.getY(), BASE.getZ() + 0.5,
            Set.of(), 0.0F, 0.0F, true);
        player.setDeltaMovement(Vec3.ZERO);
        equip(player, NANO, 1000000.0);
        player.inventoryMenu.broadcastChanges();
      });
      connection.waitForChunksDownload();
      context.waitTicks(3);
      connection.waitForClientboundPackets();
      context.waitFor(client -> client.player.getArmorValue() == 20
          && near(totalCharge(client.player), 4000000.0));
      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        require(player.getArmorValue() == 20, "Full charged nanosuit does not supply 20 equipped armor points");
        attack(connection);
        require(near(player.getHealth(), 19.0), "Real survival player did not retain exactly 1 HP of a 10 HP nano-armored attack: "
            + player.getHealth());
        require(near(totalCharge(player), 3955000.0), "Native attack did not spend 45000 EU across all nano pieces: "
            + totalCharge(player));
        player.inventoryMenu.broadcastChanges();
      });
      context.waitTicks(2);
      connection.waitForClientboundPackets();
      context.waitFor(client -> near(client.player.getHealth(), 19.0)
          && near(totalCharge(client.player), 3955000.0));
      evidence.add("Electric armor native PvP: actual FakePlayer.attack hit the connected survival player for 10 HP; "
          + "the full nanosuit absorbed 90%, health 20 -> 19, total charge 4000000 -> 3955000 EU. "
          + "Native equipment/health packets delivered the same armor value 20, health and EU to the client.");

      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        double bootsBefore = ElectricItem.manager.getCharge(player.getItemBySlot(EquipmentSlot.FEET));
        player.damageCooldownTime = 0;
        player.setInvulnerableTime(0);
        player.causeFallDamage(6.0, 1.0F, connection.getServerLevel().damageSources().fall());
        require(near(player.getHealth(), 19.0), "Native six-block fall callback damaged the charged nanosuit wearer");
        require(near(ElectricItem.manager.getCharge(player.getItemBySlot(EquipmentSlot.FEET)), bootsBefore - 15000.0),
            "Native six-block fall callback did not consume exactly 15000 EU from nano boots");
        require(near(totalCharge(player), 3940000.0), "Native fall changed unexpected armor charge");
        player.inventoryMenu.broadcastChanges();
      });
      context.waitTicks(2);
      connection.waitForClientboundPackets();
      context.waitFor(client -> near(client.player.getHealth(), 19.0)
          && near(totalCharge(client.player), 3940000.0));
      evidence.add("Electric armor native fall: connected survival player's causeFallDamage(6, 1, fall) "
          + "preserved health 19 and spent exactly 15000 EU from the nano boots; client received the changed equipped stack.");

      context.runOnClient(client -> {
        ItemStack helmet = client.player.getItemBySlot(EquipmentSlot.HEAD);
        List<String> lines = helmet.getTooltipLines(Item.TooltipContext.of(client.level), client.player,
            TooltipFlag.NORMAL).stream().map(Component::getString).toList();
        String armorName = Component.translatable(Attributes.ARMOR.value().getDescriptionId()).getString();
        List<String> armorLines = lines.stream().filter(line -> line.contains(armorName)).toList();
        require(armorLines.size() == 1 && armorLines.getFirst().contains("+3"),
            "Native nano helmet tooltip should contain exactly one +3 armor entry: " + lines);
        evidence.add("Electric armor native tooltip: charged nano helmet has one +3 armor entry: "
            + String.join(" | ", lines));
      });
      context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
      context.waitTicks(2);
      double[] cursor = context.computeOnClient(client -> {
        require(client.gui.screen() instanceof InventoryScreen, "Native survival inventory did not open");
        var screen = (InventoryScreen) client.gui.screen();
        var helmet = playerHelmetSlot(client);
        var window = client.getWindow();
        return new double[] {(field(screen, "leftPos") + helmet.x + 8.0)
            * window.getScreenWidth() / window.getGuiScaledWidth(),
            (field(screen, "topPos") + helmet.y + 8.0)
                * window.getScreenHeight() / window.getGuiScaledHeight()};
      });
      context.getInput().setCursorPos(cursor[0], cursor[1]);
      context.waitTicks(3);
      evidence.add("Electric armor native inventory screenshot: " + context.takeScreenshot(
          TestScreenshotOptions.of("ic2-nano-armor-protection").disableCounterPrefix()).toAbsolutePath());
      context.runOnClient(client -> client.gui.screen().onClose());
      connection.waitForServerboundPackets();

      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        player.setHealth(20.0F);
        equip(player, NANO, 0.0);
        player.inventoryMenu.broadcastChanges();
      });
      context.waitTicks(3);
      connection.waitForClientboundPackets();
      context.waitFor(client -> client.player.getArmorValue() == 0 && near(totalCharge(client.player), 0.0));
      server.runOnServer(instance -> {
        attack(connection);
        var player = connection.getServerPlayer();
        require(near(player.getHealth(), 10.0), "Empty nanosuit still reduced native player attack damage: " + player.getHealth());
        require(near(totalCharge(player), 0.0), "Empty nanosuit charge changed during native damage");
        player.inventoryMenu.broadcastChanges();
      });
      context.waitTicks(2);
      connection.waitForClientboundPackets();
      context.waitFor(client -> near(client.player.getHealth(), 10.0));
      evidence.add("Electric armor empty-suit control: all four discharged nano pieces supplied zero armor; "
          + "actual 10 HP player attack reduced server/client health 20 -> 10 with zero EU spent.");

      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        player.setHealth(20.0F);
        equip(player, QUANTUM, 10000000.0);
        player.inventoryMenu.broadcastChanges();
      });
      context.waitTicks(3);
      connection.waitForClientboundPackets();
      context.waitFor(client -> client.player.getArmorValue() == 20 && near(totalCharge(client.player), 40000000.0));
      server.runOnServer(instance -> {
        attack(connection);
        var player = connection.getServerPlayer();
        require(near(player.getHealth(), 20.0), "Full quantum suit did not absorb the complete native player attack");
        require(near(totalCharge(player), 39800000.0), "Full quantum suit did not spend exactly 200000 EU: "
            + totalCharge(player));
        player.inventoryMenu.broadcastChanges();
      });
      context.waitTicks(2);
      connection.waitForClientboundPackets();
      context.waitFor(client -> near(client.player.getHealth(), 20.0)
          && near(totalCharge(client.player), 39800000.0));
      evidence.add("Electric armor quantum control: full charged quantum suit absorbed 100% of the same native "
          + "10 HP player attack, preserved server/client health 20 and spent 200000 EU, charge 40000000 -> 39800000.");
      return evidence;
    } finally {
      context.runOnClient(client -> {
        if (client.gui.screen() != null) client.gui.screen().onClose();
      });
      connection.waitForServerboundPackets();
      boolean removeFixture = fixtureCreated;
      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        player.closeContainer();
        player.getInventory().clearContent();
        for (int slot = 0; slot < saved.inventory().size(); slot++) {
          player.getInventory().setItem(slot, saved.inventory().get(slot));
        }
        for (int index = 0; index < ARMOR.size(); index++) player.setItemSlot(ARMOR.get(index), saved.armor().get(index));
        player.setGameMode(saved.mode());
        player.setHealth(saved.health());
        player.setAbsorptionAmount(saved.absorption());
        player.getFoodData().setFoodLevel(saved.food());
        player.getFoodData().setSaturation(saved.saturation());
        player.damageCooldownTime = 0;
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.teleportTo(connection.getServerLevel(), saved.x(), saved.y(), saved.z(), Set.of(),
            saved.yaw(), saved.pitch(), true);
        player.inventoryMenu.broadcastChanges();
        if (removeFixture) for (BlockPos position : platform()) connection.getServerLevel()
            .setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
      });
      connection.waitForClientboundPackets();
      context.waitTick();
    }
  }

  private static void attack(TestServerConnection connection) {
    var player = connection.getServerPlayer();
    player.damageCooldownTime = 0;
    player.setInvulnerableTime(0);
    player.setDeltaMovement(Vec3.ZERO);
    Player attacker = IC2.envProxy.createFakePlayer(connection.getServerLevel(), ATTACKER);
    attacker.getInventory().clearContent();
    attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10.0);
    attacker.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024.0);
    attacker.setPos(player.getX(), player.getY(), player.getZ() - 2.0);
    attacker.setOnGround(true);
    attacker.setSprinting(false);
    attacker.fallDistance = 0.0;
    require(attacker.getAttackStrengthScale(0.5F) == 1.0F, "Native attacker is not fully cooled down");
    attacker.attack(player);
    player.setDeltaMovement(Vec3.ZERO);
  }

  private static void equip(Player player, List<Item> items, double charge) {
    for (int index = 0; index < ARMOR.size(); index++) {
      player.setItemSlot(ARMOR.get(index), ElectricItemManager.getCharged(items.get(index), charge));
    }
  }

  private static double totalCharge(Player player) {
    return ARMOR.stream().mapToDouble(slot -> ElectricItem.manager.getCharge(player.getItemBySlot(slot))).sum();
  }

  private static net.minecraft.world.inventory.Slot playerHelmetSlot(Minecraft client) {
    return client.player.inventoryMenu.slots.stream().filter(slot -> slot.getItem().is(Ic2Items.NANO_HELMET))
        .findFirst().orElseThrow(() -> new AssertionError("Native player inventory has no equipped nano helmet slot"));
  }

  private static int field(AbstractContainerScreen<?> screen, String name) {
    try {
      Field field = AbstractContainerScreen.class.getDeclaredField(name);
      field.setAccessible(true);
      return field.getInt(screen);
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError("Could not inspect native inventory screen " + name, exception);
    }
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

  private record SavedPlayer(double x, double y, double z, float yaw, float pitch, GameType mode,
      float health, float absorption, int food, float saturation, List<ItemStack> inventory, List<ItemStack> armor) {}
}
