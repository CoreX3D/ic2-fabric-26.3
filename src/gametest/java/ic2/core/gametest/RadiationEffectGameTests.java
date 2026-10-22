package ic2.core.gametest;

import ic2.api.item.ElectricItem;
import ic2.core.Ic2DamageSource;
import ic2.core.Ic2Potion;
import ic2.core.item.ElectricItemManager;
import ic2.core.ref.Ic2Items;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Real effect lifecycle regressions: no direct calls to radiation or armor damage helpers. */
public final class RadiationEffectGameTests {
  private static final String EMPTY = "ic2:gametest/empty3x3x3";
  private static final EquipmentSlot[] ARMOR = {
    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
  };

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void nativeRadiationTicksPreserveOriginalIntegerDoseScaling(GameTestHelper helper) {
    int[] amplifiers = {0, 50, 90, 100, 150, 200};
    double[] damage = {0.5, 0.5, 0.5, 1.5, 1.5, 2.5};
    for (int i = 0; i < amplifiers.length; i++) {
      ServerPlayer player = freshPlayer(helper);
      irradiate(player, 100, amplifiers[i]);
      step(player);

      assertNear(helper, player.getHealth(), 20.0 - damage[i],
          "native radiation damage at amplifier " + amplifiers[i]);
      helper.assertTrue(player.hasEffect(Ic2Potion.radiationHolder()),
          "a damaging effect tick must preserve its remaining duration");
      helper.assertTrue(player.getLastDamageSource() != null
              && player.getLastDamageSource().is(Ic2DamageSource.RADIATION),
          "the native effect must use the current level's IC radiation damage type");
    }
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void amplifierZeroOnlyDamagesOnItsOriginalTwentyFiveTickInterval(GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    irradiate(player, 49, 0);
    advance(player, 24);
    assertNear(helper, player.getHealth(), 20, "no radiation pulse before duration reaches 25");
    helper.assertValueEqual(player.getEffect(Ic2Potion.radiationHolder()).getDuration(), 25,
        "effect duration before first pulse");

    step(player);
    assertNear(helper, player.getHealth(), 19.5, "duration 25 emits one half-HP radiation pulse");
    advance(player, 24);
    assertNear(helper, player.getHealth(), 19.5, "there must be no extra pulse before expiration");
    helper.assertFalse(player.hasEffect(Ic2Potion.radiationHolder()),
        "the native effect must expire at the end of its original duration");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void actualUranium238InventoryExposureContinuesHurtingAfterItemIsRemoved(GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    player.getInventory().setItem(0, new ItemStack(Ic2Items.URANIUM_238));
    step(player);
    MobEffectInstance effect = player.getEffect(Ic2Potion.radiationHolder());
    helper.assertTrue(effect != null, "the real radioactive inventory item must apply radiation");
    helper.assertValueEqual(effect.getAmplifier(), 90, "uranium-238 radiation dose");
    player.getInventory().setItem(0, ItemStack.EMPTY);
    float beforePulse = player.getHealth();
    step(player);

    assertNear(helper, player.getHealth(), beforePulse - 0.5,
        "the native ongoing effect must hurt after exposure has ended");
    helper.assertTrue(player.hasEffect(Ic2Potion.radiationHolder()),
        "removing radioactive material does not instantly cure an existing effect");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void ordinaryDiamondAndNetheriteArmorDoNotReduceRadiation(GameTestHelper helper) {
    Item[][] sets = {
      {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
      {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS}
    };
    for (Item[] set : sets) {
      ServerPlayer player = freshPlayer(helper);
      for (int i = 0; i < ARMOR.length; i++) player.setItemSlot(ARMOR[i], new ItemStack(set[i]));
      step(player);
      helper.assertValueEqual(player.getArmorValue(), 20, "the ordinary armor must be natively equipped");
      irradiate(player, 100, 100);
      step(player);

      assertNear(helper, player.getHealth(), 18.5, "ordinary armor cannot absorb radiation");
      for (EquipmentSlot slot : ARMOR) {
        helper.assertValueEqual(player.getItemBySlot(slot).getDamageValue(), 0,
            "radiation bypasses ordinary armor durability damage");
      }
    }
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void nanoSuitSpendsEnergyOnAcceptedRadiationPulsesOnly(GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    equipNano(player, 1_000_000);
    step(player);
    helper.assertValueEqual(player.getArmorValue(), 20, "the full nano suit must be natively equipped");
    irradiate(player, 100, 100);
    step(player);

    assertNear(helper, player.getHealth(), 19.85, "nano suit absorbs 90 percent of 1.5 radiation HP");
    assertNear(helper, charge(player), 4_000_000 - 6_750,
        "nano suit spends 5000 EU per absorbed radiation HP");
    double chargeAfterAcceptedPulse = charge(player);
    step(player);
    assertNear(helper, player.getHealth(), 19.85, "native hurt cooldown rejects the next equal pulse");
    assertNear(helper, charge(player), chargeAfterAcceptedPulse,
        "a cooldown-rejected pulse must not consume EU");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void completeHazmatBlocksAnExistingRadiationEffectButPartialSuitDoesNot(GameTestHelper helper) {
    ServerPlayer protectedPlayer = freshPlayer(helper);
    equipHazmat(protectedPlayer);
    irradiate(protectedPlayer, 100, 100);
    advance(protectedPlayer, 3);
    assertNear(helper, protectedPlayer.getHealth(), 20, "complete hazmat prevents ongoing radiation damage");
    helper.assertTrue(protectedPlayer.hasEffect(Ic2Potion.radiationHolder()),
        "hazmat protection blocks damage without curing the existing effect");

    ServerPlayer partial = freshPlayer(helper);
    equipHazmat(partial);
    partial.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
    irradiate(partial, 100, 100);
    step(partial);
    assertNear(helper, partial.getHealth(), 18.5, "an incomplete hazmat suit cannot prevent radiation");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void chargedQuantumBlocksRadiationAndItsHelmetCanSeparatelyCureTheEffect(GameTestHelper helper) {
    ServerPlayer shielded = freshPlayer(helper);
    // Every piece supplies hazmat protection while positively charged. One EU in the helmet is
    // deliberately insufficient for a cure, so this observes damage prevention by the full suit.
    equipQuantum(shielded, 1);
    irradiate(shielded, 100, 90);
    advance(shielded, 3);
    assertNear(helper, shielded.getHealth(), 20, "positively charged full quantum blocks radiation");
    assertNear(helper, charge(shielded), 4, "quantum hazmat protection does not charge damage-absorption EU");
    helper.assertTrue(shielded.hasEffect(Ic2Potion.radiationHolder()),
        "an undercharged helmet cannot cure the blocked effect");

    ServerPlayer cured = freshPlayer(helper);
    equipQuantum(cured, 10_000_000);
    irradiate(cured, 100, 90);
    step(cured);
    assertNear(helper, cured.getHealth(), 20, "a charged quantum helmet wearer is protected during cure");
    helper.assertFalse(cured.hasEffect(Ic2Potion.radiationHolder()),
        "the native equipped quantum helmet tick must cure radiation when sufficiently charged");
    assertNear(helper, charge(cured), 40_000_000 - 19_000,
        "amplifier 90 costs the legacy 10000 + 90*100 EU cure with no additional damage cost");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void creativePlayersDoNotLoseHealthOrNanoChargeToAnActiveEffect(GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    equipNano(player, 1_000_000);
    player.getAbilities().invulnerable = true;
    irradiate(player, 100, 100);
    advance(player, 3);

    assertNear(helper, player.getHealth(), 20, "creative immunity rejects radiation damage");
    assertNear(helper, charge(player), 4_000_000, "creative immunity must not consume nano charge");
    helper.assertTrue(player.hasEffect(Ic2Potion.radiationHolder()),
        "creative immunity does not need to erase the active effect");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void ongoingRadiationCanKillAnUnprotectedPlayer(GameTestHelper helper) {
    ServerPlayer player = freshPlayer(helper);
    irradiate(player, 600, 200);
    for (int tick = 0; tick < 100 && !player.isDeadOrDying(); tick++) step(player);

    helper.assertTrue(player.isDeadOrDying(), "ongoing radiation must remain lethal rather than stopping at half a heart");
    helper.assertTrue(player.getLastDamageSource() != null
            && player.getLastDamageSource().is(Ic2DamageSource.RADIATION),
        "the lethal native hit must be radiation");
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
    player.getFoodData().setFoodLevel(10);
    player.getFoodData().setSaturation(0);
    player.setNoGravity(true);
    BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
    player.setPos(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5);
    player.setOnGround(true);
    return player;
  }

  private static void irradiate(ServerPlayer player, int duration, int amplifier) {
    player.addEffect(new MobEffectInstance(Ic2Potion.radiationHolder(), duration, amplifier));
  }

  private static void step(ServerPlayer player) {
    // ServerPlayer.tick advances the damage cooldown; doTick delegates to Player/LivingEntity.tick
    // and advances MobEffectInstance plus actual equipped/inventory item ticks.
    player.tick();
    player.doTick();
  }

  private static void advance(ServerPlayer player, int ticks) {
    for (int tick = 0; tick < ticks; tick++) step(player);
  }

  private static void equipNano(ServerPlayer player, double eu) {
    equip(player, eu, Ic2Items.NANO_HELMET, Ic2Items.NANO_CHESTPLATE, Ic2Items.NANO_LEGGINGS, Ic2Items.NANO_BOOTS);
  }

  private static void equipQuantum(ServerPlayer player, double eu) {
    equip(player, eu, Ic2Items.QUANTUM_HELMET, Ic2Items.QUANTUM_CHESTPLATE, Ic2Items.QUANTUM_LEGGINGS, Ic2Items.QUANTUM_BOOTS);
  }

  private static void equip(ServerPlayer player, double eu, Item... items) {
    for (int i = 0; i < ARMOR.length; i++) {
      player.setItemSlot(ARMOR[i], ElectricItemManager.getCharged(items[i], eu));
    }
  }

  private static void equipHazmat(ServerPlayer player) {
    Item[] items = {Ic2Items.HAZMAT_HELMET, Ic2Items.HAZMAT_CHESTPLATE, Ic2Items.HAZMAT_LEGGINGS, Ic2Items.RUBBER_BOOTS};
    for (int i = 0; i < ARMOR.length; i++) player.setItemSlot(ARMOR[i], new ItemStack(items[i]));
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
