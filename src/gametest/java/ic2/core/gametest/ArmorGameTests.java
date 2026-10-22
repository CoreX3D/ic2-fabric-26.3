package ic2.core.gametest;

import ic2.api.item.ElectricItem;
import ic2.core.item.ElectricItemManager;
import ic2.core.ref.Ic2Items;
import ic2.core.util.StackUtil;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;

public final class ArmorGameTests {
  private static final String EMPTY = "ic2:gametest/empty3x3x3";
  private static final double NIGHT_VISION_GOGGLES_MAX_CHARGE = 200000.0;

  // Public because Fabric Loader instantiates every "fabric-gametest" entrypoint.
  public ArmorGameTests() {}

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void bronzeArmorHasExpectedDurability(GameTestHelper helper) {
    assertMaxDamage(helper, Ic2Items.BRONZE_HELMET.getDefaultInstance(), 165, "bronze helmet");
    assertMaxDamage(
        helper, Ic2Items.BRONZE_CHESTPLATE.getDefaultInstance(), 240, "bronze chestplate");
    assertMaxDamage(helper, Ic2Items.BRONZE_LEGGINGS.getDefaultInstance(), 225, "bronze leggings");
    assertMaxDamage(helper, Ic2Items.BRONZE_BOOTS.getDefaultInstance(), 195, "bronze boots");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void bronzeArmorConsumesDurabilityWhenPlayerIsDamaged(GameTestHelper helper) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Ic2Items.BRONZE_HELMET));
    player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Ic2Items.BRONZE_CHESTPLATE));
    player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Ic2Items.BRONZE_LEGGINGS));
    player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Ic2Items.BRONZE_BOOTS));
    LivingEntity attacker = helper.spawn(net.minecraft.world.entity.EntityTypes.PIG, new BlockPos(1, 1, 1));
    DamageSource source = helper.getLevel().damageSources().mobAttack(attacker);

    helper.assertTrue(player.hurtServer(helper.getLevel(), source, 4.0F), "the test player should take damage");
    assertDamageConsumed(helper, player.getItemBySlot(EquipmentSlot.HEAD), "helmet");
    assertDamageConsumed(helper, player.getItemBySlot(EquipmentSlot.CHEST), "chestplate");
    assertDamageConsumed(helper, player.getItemBySlot(EquipmentSlot.LEGS), "leggings");
    assertDamageConsumed(helper, player.getItemBySlot(EquipmentSlot.FEET), "boots");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void hazmatArmorHasExpectedProtectionAndDurability(GameTestHelper helper) {
    assertMaxDamage(helper, Ic2Items.HAZMAT_HELMET.getDefaultInstance(), 64, "hazmat helmet");
    assertMaxDamage(
        helper, Ic2Items.HAZMAT_CHESTPLATE.getDefaultInstance(), 64, "hazmat chestplate");
    assertMaxDamage(helper, Ic2Items.HAZMAT_LEGGINGS.getDefaultInstance(), 64, "hazmat leggings");
    assertMaxDamage(helper, Ic2Items.RUBBER_BOOTS.getDefaultInstance(), 64, "rubber boots");

    assertArmorProtection(helper, Ic2Items.RUBBER_BOOTS, 1, "rubber boots");
    assertArmorProtection(helper, Ic2Items.BRONZE_BOOTS, 2, "bronze boots");
    helper.succeed();
  }

  @GameTest(structure = EMPTY, maxTicks = 100)
  public void nightVisionGogglesRunFromEquippedHelmetSlot(GameTestHelper helper) {
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    ItemStack goggles =
        ElectricItemManager.getCharged(Ic2Items.NIGHT_VISION_GOGGLES, Double.POSITIVE_INFINITY);
    StackUtil.updateNbtData(goggles, nbt -> nbt.putBoolean("active", true));
    player.setItemSlot(EquipmentSlot.HEAD, goggles);

    BlockPos playerPos = helper.absolutePos(new BlockPos(1, 1, 1));
    player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
    // Armor lives in EntityEquipment in 26.3; aiStep dispatches the real equipped-stack tick.
    player.aiStep();

    Ic2GameTestAssertions.assertNear(
        helper,
        ElectricItem.manager.getCharge(goggles),
        NIGHT_VISION_GOGGLES_MAX_CHARGE - 1.0,
        "goggles charge after equipped inventory tick");
    helper.succeed();
  }

  private static void assertMaxDamage(
      GameTestHelper helper, ItemStack stack, int expected, String piece) {
    helper.assertValueEqual(stack.getMaxDamage(), expected, piece + " max durability");
  }

  private static void assertArmorProtection(
      GameTestHelper helper, Item armor, int expected, String piece) {
    ItemStack stack = armor.getDefaultInstance();
    var equippable = stack.get(DataComponents.EQUIPPABLE);
    helper.assertTrue(equippable != null, piece + " must be equippable");
    double protection = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
        .compute(Attributes.ARMOR, 0.0, equippable.slot());
    Ic2GameTestAssertions.assertNear(helper, protection, expected, piece + " armor protection");
  }

  private static void assertDamageConsumed(GameTestHelper helper, ItemStack stack, String piece) {
    helper.assertTrue(stack.getDamageValue() > 0, "bronze " + piece + " should lose durability");
  }
}
