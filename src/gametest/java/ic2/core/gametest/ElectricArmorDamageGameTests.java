package ic2.core.gametest;

import ic2.api.item.ElectricItem;
import ic2.core.item.ElectricItemManager;
import ic2.core.ref.Ic2Items;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Regressions through native player damage, attacks and landing rather than IC armor helpers. */
public final class ElectricArmorDamageGameTests {
  private static final String EMPTY = "ic2:gametest/empty3x3x3";
  private static final double NANO_CAPACITY = 1_000_000.0;
  private static final double QUANTUM_CAPACITY = 10_000_000.0;
  private static final EquipmentSlot[] ARMOR = {
    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
  };

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void fullNanoSuitAbsorbsNinetyPercentDuringNativePlayerDamage(GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    equipNano(player, NANO_CAPACITY);
    syncEquipment(helper, player);
    helper.assertValueEqual(player.getArmorValue(), 20, "all four nano armor attributes must be active");
    ServerPlayer attacker = freshPlayer(helper);

    helper.assertTrue(
        player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 10.0F),
        "the native player attack damage must be accepted");

    assertNear(helper, player.getHealth(), 19.0, "full nano health after ten damage");
    assertNear(helper, charge(player), 4 * NANO_CAPACITY - 45_000, "full nano EU after nine absorbed HP");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void fullQuantumSuitAbsorbsAllDamageWithoutOvercharging(GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    equipQuantum(player, QUANTUM_CAPACITY);
    syncEquipment(helper, player);
    helper.assertValueEqual(player.getArmorValue(), 20, "all four quantum armor attributes must be active");
    ServerPlayer attacker = freshPlayer(helper);

    player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 10.0F);

    assertNear(helper, player.getHealth(), 20.0, "full quantum health after ten damage");
    assertNear(helper, charge(player), 4 * QUANTUM_CAPACITY - 200_000, "quantum EU is capped at ten absorbed HP");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void emptyElectricArmorProvidesNoFreeDamageReduction(GameTestHelper helper) {
    for (boolean quantum : List.of(false, true)) {
      ServerPlayer player = freshPlayer(helper);
      if (quantum) equipQuantum(player, 0); else equipNano(player, 0);
      player.doTick();

      helper.assertTrue(player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 10.0F),
          "native damage must reach a player in an empty suit");

      assertNear(helper, player.getHealth(), 10.0, "empty suit health, quantum=" + quantum);
      assertNear(helper, charge(player), 0, "empty suit cannot spend or acquire EU");
    }
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void nanoChestUsesItsShareAndNearlyEmptyChargeCannotProtectForFree(GameTestHelper helper) {
    ServerPlayer chestOnly = freshPlayer(helper);
    chestOnly.setItemSlot(EquipmentSlot.CHEST, charged(Ic2Items.NANO_CHESTPLATE, 100_000));
    syncEquipment(helper, chestOnly);
    chestOnly.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 10.0F);
    assertNear(helper, chestOnly.getHealth(), 13.6, "nano chest absorbs exactly 36 percent");
    assertNear(helper, charge(chestOnly), 82_000, "nano chest spends 18000 EU for 3.6 HP");

    ServerPlayer almostEmpty = freshPlayer(helper);
    almostEmpty.setItemSlot(EquipmentSlot.CHEST, charged(Ic2Items.NANO_CHESTPLATE, 5_000));
    syncEquipment(helper, almostEmpty);
    almostEmpty.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 10.0F);
    assertNear(helper, almostEmpty.getHealth(), 11.0, "5000 EU covers only one HP");
    assertNear(helper, charge(almostEmpty), 0, "nearly empty armor cannot overdraw charge");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void actualPlayerAttackConsumesNanoChargeAndLeavesOneTenthOfNakedDamage(GameTestHelper helper) {
    ServerPlayer attacker = freshPlayer(helper);
    attacker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10.0);
    attacker.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1_000.0);
    attacker.setOnGround(true);
    ServerPlayer naked = freshPlayer(helper);
    ServerPlayer armored = freshPlayer(helper);
    equipNano(armored, NANO_CAPACITY);
    syncEquipment(helper, armored);

    // The high attack speed makes both native attacks fully charged without touching damage helpers.
    attacker.attack(naked);
    double nakedDamage = 20.0 - naked.getHealth();
    helper.assertTrue(nakedDamage > 0.0, "the real PvP control attack must damage the naked player");
    attacker.attack(armored);

    assertNear(helper, 20.0 - armored.getHealth(), nakedDamage * 0.1, "real PvP nano damage");
    assertNear(helper, charge(armored), 4 * NANO_CAPACITY - nakedDamage * 0.9 * 5_000,
        "real PvP must spend charge once");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void mixedSuitKeepsOrdinaryArmorWithoutApplyingElectricArmorPointsTwice(GameTestHelper helper) {
    DamageSource source = helper.getLevel().damageSources().playerAttack(freshPlayer(helper));
    ServerPlayer mixed = freshPlayer(helper);
    mixed.setItemSlot(EquipmentSlot.CHEST, charged(Ic2Items.NANO_CHESTPLATE, 100_000));
    mixed.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
    syncEquipment(helper, mixed);
    float mixedExpected = CombatRules.getDamageAfterAbsorb(mixed, 6.4F, source, 3.0F,
        (float) mixed.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
    mixed.hurtServer(helper.getLevel(), source, 10.0F);
    assertNear(helper, mixed.getHealth(), 20.0 - mixedExpected,
        "only ordinary diamond boots mitigate the damage left by electric armor");
    assertNear(helper, charge(mixed), 82_000, "ordinary armor must not change the electric chest EU cost");
    helper.assertTrue(mixed.getItemBySlot(EquipmentSlot.FEET).getDamageValue() > 0,
        "the ordinary boots still take normal armor durability damage");

    ServerPlayer ordinary = freshPlayer(helper);
    ordinary.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
    ordinary.doTick();
    float ordinaryExpected = CombatRules.getDamageAfterAbsorb(ordinary, 10.0F, source, 8.0F,
        (float) ordinary.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
    ordinary.hurtServer(helper.getLevel(), source, 10.0F);
    assertNear(helper, ordinary.getHealth(), 20.0 - ordinaryExpected,
        "a fully ordinary armor loadout retains native damage reduction");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void fireExplosionProjectileAndMagicAllUseElectricProtection(GameTestHelper helper) {
    var arrow = helper.spawn(EntityTypes.ARROW, new BlockPos(1, 1, 1));
    var attacker = helper.spawn(EntityTypes.PIG, new BlockPos(1, 1, 1));
    var sources = helper.getLevel().damageSources();
    DamageSource[] damageSources = {
      sources.inFire(), sources.explosion(attacker, attacker), sources.arrow(arrow, attacker),
      sources.magic(), sources.lightningBolt()
    };
    for (DamageSource source : damageSources) {
      ServerPlayer player = freshPlayer(helper);
      equipNano(player, NANO_CAPACITY);
      syncEquipment(helper, player);

      helper.assertTrue(player.hurtServer(helper.getLevel(), source, 10.0F),
          "the native damage source must be accepted: " + source.getMsgId());

      assertNear(helper, player.getHealth(), 19.0, "nano health after " + source.getMsgId());
      assertNear(helper, charge(player), 4 * NANO_CAPACITY - 45_000,
          "nano EU after " + source.getMsgId());
    }
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void voidAndKillDamageBypassElectricProtectionWithoutSpendingCharge(GameTestHelper helper) {
    for (DamageSource source : List.of(
        helper.getLevel().damageSources().fellOutOfWorld(), helper.getLevel().damageSources().genericKill())) {
      ServerPlayer player = freshPlayer(helper);
      equipNano(player, NANO_CAPACITY);
      syncEquipment(helper, player);
      player.hurtServer(helper.getLevel(), source, 2.0F);

      assertNear(helper, player.getHealth(), 18.0, "bypassing source health: " + source.getMsgId());
      assertNear(helper, charge(player), 4 * NANO_CAPACITY,
          "bypassing source must not spend EU: " + source.getMsgId());
    }
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void creativeAndInvulnerabilityWindowDoNotSpendChargeForRejectedHits(GameTestHelper helper) {
    ServerPlayer creative = freshPlayer(helper);
    equipNano(creative, NANO_CAPACITY);
    creative.getAbilities().invulnerable = true;
    helper.assertFalse(creative.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 10.0F),
        "native creative protection must reject damage");
    assertNear(helper, creative.getHealth(), 20, "creative player's health");
    assertNear(helper, charge(creative), 4 * NANO_CAPACITY, "creative protection spends no EU");

    ServerPlayer player = freshPlayer(helper);
    equipNano(player, NANO_CAPACITY);
    syncEquipment(helper, player);
    player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 10.0F);
    double firstCharge = charge(player);
    double firstHealth = player.getHealth();
    helper.assertFalse(player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 10.0F),
        "the second equal hit in the native invulnerability window must be rejected");
    assertNear(helper, player.getHealth(), firstHealth, "rejected repeat hit health");
    assertNear(helper, charge(player), firstCharge, "rejected repeat hit spends no EU");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void absorptionHeartsConsumeOnlyResidualDamageWithoutDuplicateEnergyCost(GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    equipNano(player, NANO_CAPACITY);
    syncEquipment(helper, player);
    player.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(4.0);
    player.setAbsorptionAmount(4.0F);
    player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 10.0F);

    assertNear(helper, player.getHealth(), 20.0, "absorption hearts protect remaining health");
    assertNear(helper, player.getAbsorptionAmount(), 3.0, "hearts absorb the one HP left by nano armor");
    assertNear(helper, charge(player), 4 * NANO_CAPACITY - 45_000, "electric armor charged exactly once");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void shieldBlockedHitsDoNotConsumeElectricCharge(GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    equipNano(player, NANO_CAPACITY);
    syncEquipment(helper, player);
    ServerPlayer attacker = freshPlayer(helper);
    attacker.setPos(player.getX(), player.getY(), player.getZ() + 2.0);
    player.setYRot(0.0F);
    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
    player.startUsingItem(InteractionHand.OFF_HAND);
    // This mock has no client connection tick to advance item use; run those same native ticks
    // explicitly so the shield passes its ordinary activation delay without changing its fields.
    for (int tick = 0; tick < 8; tick++) player.doTick();
    helper.assertTrue(player.isBlocking(), "the native shield must be active before the hit");
    player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 10.0F);
    assertNear(helper, player.getHealth(), 20, "shield blocked hit health");
    assertNear(helper, charge(player), 4 * NANO_CAPACITY, "shield blocked hit must spend no EU");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void nanoBootsUseNativeShortFallProtectionAndEmptyBootsDoNot(GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    player.setItemSlot(EquipmentSlot.FEET, charged(Ic2Items.NANO_BOOTS, NANO_CAPACITY));
    syncEquipment(helper, player);
    player.causeFallDamage(6.0, 1.0F, helper.getLevel().damageSources().fall());
    assertNear(helper, player.getHealth(), 20, "six block fall with nano boots");
    assertNear(helper, charge(player), NANO_CAPACITY - 15_000, "nano short fall EU");

    ServerPlayer empty = freshPlayer(helper);
    empty.setItemSlot(EquipmentSlot.FEET, charged(Ic2Items.NANO_BOOTS, 0));
    empty.causeFallDamage(6.0, 1.0F, helper.getLevel().damageSources().fall());
    assertNear(helper, empty.getHealth(), 17, "six block fall with empty nano boots");
    assertNear(helper, charge(empty), 0, "empty fall protection cannot acquire EU");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void quantumBootsUseNativeLongFallProtection(GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    player.setItemSlot(EquipmentSlot.FEET, charged(Ic2Items.QUANTUM_BOOTS, QUANTUM_CAPACITY));
    syncEquipment(helper, player);
    player.causeFallDamage(12.0, 1.0F, helper.getLevel().damageSources().fall());

    assertNear(helper, player.getHealth(), 20, "twelve block fall with quantum boots");
    assertNear(helper, charge(player), QUANTUM_CAPACITY - 40_000, "quantum long fall EU");

    ServerPlayer lowBoots = freshPlayer(helper);
    lowBoots.setItemSlot(EquipmentSlot.FEET, charged(Ic2Items.QUANTUM_BOOTS, 10_000));
    lowBoots.setItemSlot(EquipmentSlot.LEGS, charged(Ic2Items.QUANTUM_LEGGINGS, 1_000_000));
    syncEquipment(helper, lowBoots);
    lowBoots.causeFallDamage(12.0, 1.0F, helper.getLevel().damageSources().fall());
    assertNear(helper, lowBoots.getHealth(), 18.3,
        "depleted boots absorb half a HP before quantum leggings absorb 80 percent of the remainder");
    assertNear(helper, charge(lowBoots), 864_000, "low quantum boots and leggings fall EU");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void longNanoFallUsesOriginalBootPriorityBeforeRemainingSuitPieces(GameTestHelper helper) {
    ServerPlayer bootsOnly = freshPlayer(helper);
    bootsOnly.setItemSlot(EquipmentSlot.FEET, charged(Ic2Items.NANO_BOOTS, NANO_CAPACITY));
    syncEquipment(helper, bootsOnly);
    bootsOnly.causeFallDamage(12.0, 1.0F, helper.getLevel().damageSources().fall());
    assertNear(helper, bootsOnly.getHealth(), 18.875, "nano boots absorb 87.5 percent of nine fall damage");
    assertNear(helper, charge(bootsOnly), NANO_CAPACITY - 39_375, "nano boots long fall EU");

    ServerPlayer full = freshPlayer(helper);
    equipNano(full, NANO_CAPACITY);
    syncEquipment(helper, full);
    full.causeFallDamage(12.0, 1.0F, helper.getLevel().damageSources().fall());
    double remaining = 9.0 * (1 - 0.875) * (1 - 0.765);
    assertNear(helper, full.getHealth(), 20.0 - remaining,
        "remaining nano pieces absorb their shares after the fall boots priority");
    assertNear(helper, charge(full), 4 * NANO_CAPACITY - (9.0 - remaining) * 5_000,
        "full nano long fall EU");
    helper.succeed();
  }

  private static ServerPlayer freshPlayer(GameTestHelper helper) {
    ServerPlayer player = helper.makeMockServerPlayerInLevel();
    player.setGameMode(GameType.SURVIVAL);
    player.getAbilities().invulnerable = false;
    player.getAbilities().mayfly = false;
    player.getAbilities().flying = false;
    player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
    player.setInvulnerableTime(0);
    player.damageCooldownTime = 0;
    player.getInventory().clearContent();
    for (EquipmentSlot slot : ARMOR) player.setItemSlot(slot, ItemStack.EMPTY);
    player.setHealth(20.0F);
    player.setAbsorptionAmount(0.0F);
    player.setNoGravity(true);
    BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
    player.setPos(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5);
    player.setOnGround(true);
    return player;
  }

  private static void equipNano(ServerPlayer player, double amount) {
    player.setItemSlot(EquipmentSlot.HEAD, charged(Ic2Items.NANO_HELMET, amount));
    player.setItemSlot(EquipmentSlot.CHEST, charged(Ic2Items.NANO_CHESTPLATE, amount));
    player.setItemSlot(EquipmentSlot.LEGS, charged(Ic2Items.NANO_LEGGINGS, amount));
    player.setItemSlot(EquipmentSlot.FEET, charged(Ic2Items.NANO_BOOTS, amount));
  }

  private static void equipQuantum(ServerPlayer player, double amount) {
    player.setItemSlot(EquipmentSlot.HEAD, charged(Ic2Items.QUANTUM_HELMET, amount));
    player.setItemSlot(EquipmentSlot.CHEST, charged(Ic2Items.QUANTUM_CHESTPLATE, amount));
    player.setItemSlot(EquipmentSlot.LEGS, charged(Ic2Items.QUANTUM_LEGGINGS, amount));
    player.setItemSlot(EquipmentSlot.FEET, charged(Ic2Items.QUANTUM_BOOTS, amount));
  }

  private static ItemStack charged(Item item, double amount) {
    return ElectricItemManager.getCharged(item, amount);
  }

  private static void syncEquipment(GameTestHelper helper, ServerPlayer player) {
    // Sync native equipped attributes too: calling the armor helper directly cannot detect a
    // second, free reduction from the charged armor points shown by the tooltip.
    // ServerPlayer.tick only handles server bookkeeping. doTick is the native connection tick
    // that delegates to Player/LivingEntity.tick and detects equipment attribute changes.
    player.doTick();
    helper.assertTrue(player.getArmorValue() > 0, "the charged armor's native attributes must be active");
    helper.assertFalse(player.isInvulnerableTo(helper.getLevel(), helper.getLevel().damageSources().generic()),
        "the native mock server player's loading and abilities must allow damage");
    assertNear(helper, player.getHealth(), 20, "player must start the damage check at full health");
  }

  private static double charge(ServerPlayer player) {
    double total = 0;
    for (EquipmentSlot slot : ARMOR) total += ElectricItem.manager.getCharge(player.getItemBySlot(slot));
    return total;
  }

  private static void assertNear(GameTestHelper helper, double actual, double expected, String label) {
    helper.assertTrue(Math.abs(actual - expected) < 0.02,
        label + ": expected " + expected + ", got " + actual);
  }
}
