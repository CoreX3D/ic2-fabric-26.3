import ic2.core.util.ItemNbt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** Standalone regression checks against the real Minecraft 26.3 classes, without IC stubs. */
public final class ItemNbtRoundTrip {
  private static int checks;

  public static void main(String[] args) throws InterruptedException {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    // 26.3 binds item defaults in a separate registry component initialization phase.
    BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
        .forEach(pending -> pending.apply());
    testCopyIsolationAndReplacement();
    testNestedUpdates();
    testRecursiveDelta();
    testRemovals();
    testTransactionsAndExceptions();
    testEmptyAndNoChange();
    testSeparateConcurrentEdits();
    System.out.println("PASS: ItemNbt " + checks + " checks against Minecraft 26.3");
  }

  private static void testCopyIsolationAndReplacement() {
    ItemStack stack = new ItemStack(Items.COPPER_INGOT);
    CompoundTag original = new CompoundTag();
    CompoundTag nested = new CompoundTag();
    nested.putInt("mode", 1);
    original.put("nested", nested);
    original.putIntArray("array", new int[] {1, 2});
    ListTag list = new ListTag();
    list.add(StringTag.valueOf("first"));
    original.put("list", list);
    ItemNbt.set(stack, original);
    nested.putInt("mode", 99);
    original.getIntArray("array").orElseThrow()[0] = 99;
    list.add(StringTag.valueOf("late"));
    CompoundTag read = ItemNbt.get(stack);
    check(read.getCompoundOrEmpty("nested").getIntOr("mode", 0) == 1, "set aliases nested tags");
    check(read.getIntArray("array").orElseThrow()[0] == 1, "set aliases arrays");
    check(read.getListOrEmpty("list").size() == 1, "set aliases lists");
    read.getCompoundOrEmpty("nested").putInt("mode", 55);
    read.getIntArray("array").orElseThrow()[0] = 55;
    read.getListOrEmpty("list").clear();
    check(ItemNbt.get(stack).getCompoundOrEmpty("nested").getIntOr("mode", 0) == 1,
        "get exposes nested component data");
    check(ItemNbt.get(stack).getIntArray("array").orElseThrow()[0] == 1, "get exposes arrays");
    check(ItemNbt.get(stack).getListOrEmpty("list").size() == 1, "get exposes lists");

    ItemStack copiedStack = stack.copy();
    ItemNbt.update(stack, tag -> tag.putBoolean("hoverMode", true));
    check(!ItemNbt.get(copiedStack).contains("hoverMode"), "editing one stack changes its copy");
    CompoundTag replacement = new CompoundTag();
    replacement.putString("replacement", "yes");
    ItemNbt.set(stack, replacement);
    check(ItemNbt.get(stack).size() == 1, "set must replace rather than merge");
    ItemNbt.set(stack, null);
    check(!stack.has(DataComponents.CUSTOM_DATA), "null set must remove the component");
  }

  private static void testNestedUpdates() {
    ItemStack stack = new ItemStack(Items.COPPER_INGOT);
    ItemNbt.update(stack, tag -> tag.putDouble("charge", 1000));
    ItemNbt.update(stack, tag -> {
      tag.putBoolean("hoverMode", true);
      // Models ElectricItem.use() making its own component update in an outer armor tick.
      ItemNbt.update(stack, electric -> electric.putDouble("charge", 750));
    });
    check(ItemNbt.get(stack).getDoubleOr("charge", -1) == 750, "outer edit loses electric charge");
    check(ItemNbt.get(stack).getBooleanOr("hoverMode", false), "hover mode was not saved");
    ItemNbt.update(stack, tag -> {
      ItemNbt.update(stack, electric -> electric.putDouble("charge", 700));
      tag.putDouble("charge", 500);
    });
    check(ItemNbt.get(stack).getDoubleOr("charge", -1) == 500, "same-key local change must win");
  }

  private static void testRecursiveDelta() {
    ItemStack stack = new ItemStack(Items.COPPER_INGOT);
    ItemNbt.update(stack, tag -> {
      CompoundTag electric = new CompoundTag();
      electric.putDouble("charge", 1000);
      electric.putBoolean("hoverMode", false);
      tag.put("electric", electric);
    });
    ItemNbt.update(stack, tag -> {
      tag.getCompoundOrEmpty("electric").putBoolean("hoverMode", true);
      ItemNbt.update(stack, inner -> {
        inner.getCompoundOrEmpty("electric").putDouble("charge", 750);
        inner.getCompoundOrEmpty("electric").putString("addedElsewhere", "preserve");
      });
    });
    CompoundTag electric = ItemNbt.get(stack).getCompoundOrEmpty("electric");
    check(electric.getDoubleOr("charge", -1) == 750, "recursive delta loses nested charge");
    check(electric.getBooleanOr("hoverMode", false), "recursive delta loses nested mode");
    check(electric.contains("addedElsewhere"), "recursive delta loses externally added nested key");

    ItemNbt.set(stack, null);
    ItemNbt.update(stack, outer -> {
      CompoundTag added = new CompoundTag();
      added.putBoolean("hoverMode", true);
      outer.put("electric", added);
      ItemNbt.update(stack, inner -> {
        CompoundTag alsoAdded = new CompoundTag();
        alsoAdded.putDouble("charge", 123);
        inner.put("electric", alsoAdded);
      });
    });
    electric = ItemNbt.get(stack).getCompoundOrEmpty("electric");
    check(electric.getDoubleOr("charge", -1) == 123, "adding a compound loses newly added nested data");
    check(electric.getBooleanOr("hoverMode", false), "adding a compound loses local leaf");

    ItemNbt.update(stack, outer -> {
      outer.getCompoundOrEmpty("electric").putBoolean("hoverMode", false);
      ItemNbt.update(stack, inner -> inner.remove("electric"));
    });
    electric = ItemNbt.get(stack).getCompoundOrEmpty("electric");
    check(electric.size() == 1 && !electric.getBooleanOr("hoverMode", true),
        "restoring a changed leaf resurrects externally removed unchanged siblings");
  }

  private static void testRemovals() {
    ItemStack stack = new ItemStack(Items.COPPER_INGOT);
    ItemNbt.update(stack, tag -> {
      tag.putString("removeRoot", "old");
      CompoundTag child = new CompoundTag();
      child.putString("removeLeaf", "old");
      child.putString("untouched", "old");
      tag.put("child", child);
    });
    ItemNbt.update(stack, outer -> {
      outer.remove("removeRoot");
      outer.getCompoundOrEmpty("child").remove("removeLeaf");
      ItemNbt.update(stack, inner -> {
        inner.putString("addedRoot", "preserve");
        inner.getCompoundOrEmpty("child").putString("addedLeaf", "preserve");
        inner.getCompoundOrEmpty("child").putString("untouched", "changed elsewhere");
      });
    });
    CompoundTag result = ItemNbt.get(stack);
    check(!result.contains("removeRoot"), "root deletion was lost");
    check(!result.getCompoundOrEmpty("child").contains("removeLeaf"), "nested deletion was lost");
    check(result.contains("addedRoot") && result.getCompoundOrEmpty("child").contains("addedLeaf"),
        "deletions lost unrelated additions");
    check(result.getCompoundOrEmpty("child").getStringOr("untouched", "").equals("changed elsewhere"),
        "deletions overwrite unchanged nested key");
    ItemNbt.update(stack, outer -> {
      outer.remove("child");
      ItemNbt.update(stack, inner -> inner.getCompoundOrEmpty("child").putDouble("charge", 3));
    });
    check(!ItemNbt.get(stack).contains("child"), "explicit compound removal must win");

    ItemNbt.update(stack, tag -> {
      CompoundTag child = new CompoundTag();
      child.putInt("delete", 1);
      tag.put("child", child);
    });
    ItemNbt.update(stack, outer -> {
      outer.getCompoundOrEmpty("child").remove("delete");
      ItemNbt.update(stack, inner -> inner.remove("child"));
    });
    check(!ItemNbt.get(stack).contains("child"), "a nested deletion resurrects removed parent");

    ItemNbt.update(stack, outer -> {
      outer.put("emptyChild", new CompoundTag());
      ItemNbt.update(stack, inner -> inner.putString("keep", "external"));
    });
    check(ItemNbt.get(stack).getCompound("emptyChild").isPresent(), "new empty compound was omitted");
  }

  private static void testTransactionsAndExceptions() {
    ItemStack stack = new ItemStack(Items.COPPER_INGOT);
    ItemNbt.update(stack, tag -> tag.putDouble("charge", 10));
    CompoundTag retained;
    ItemNbt.Edit transaction = ItemNbt.edit(stack);
    retained = transaction.tag();
    retained.putBoolean("hoverMode", true);
    ItemNbt.update(stack, tag -> tag.putDouble("charge", 8));
    transaction.close();
    check(ItemNbt.get(stack).getDoubleOr("charge", -1) == 8, "transaction loses nested charge");
    check(ItemNbt.get(stack).getBooleanOr("hoverMode", false), "transaction does not commit mode");
    retained.putDouble("charge", 999);
    check(ItemNbt.get(stack).getDoubleOr("charge", -1) == 8, "retained edit reference aliases component");
    transaction.close();
    check(ItemNbt.get(stack).getDoubleOr("charge", -1) == 8, "second close commits again");
    try {
      transaction.tag();
      throw new AssertionError("closed transaction permits tag access");
    } catch (IllegalStateException expected) {
      checks++;
    }
    try {
      ItemNbt.update(stack, tag -> {
        tag.putInt("partial", 1);
        ItemNbt.update(stack, inner -> inner.putDouble("charge", 7));
        throw new IllegalArgumentException("expected");
      });
      throw new AssertionError("callback failure not propagated");
    } catch (IllegalArgumentException expected) {
      check(!ItemNbt.get(stack).contains("partial"), "failed callback commits local mutations");
      check(ItemNbt.get(stack).getDoubleOr("charge", -1) == 7, "failed callback loses nested committed update");
    }
    try (var edit = ItemNbt.edit(stack)) {
      edit.tag().putInt("closeOnException", 1);
      throw new IllegalArgumentException("expected");
    } catch (IllegalArgumentException expected) {
      check(ItemNbt.get(stack).getIntOr("closeOnException", 0) == 1,
          "AutoCloseable must commit its documented close-on-exception behavior");
    }
  }

  private static void testEmptyAndNoChange() {
    ItemStack stack = new ItemStack(Items.COPPER_INGOT);
    ItemNbt.update(stack, tag -> {});
    try (var edit = ItemNbt.edit(stack)) { /* Read-only access does not create a component. */ }
    check(!stack.has(DataComponents.CUSTOM_DATA), "empty no-op creates CUSTOM_DATA");
    ItemNbt.update(stack, tag -> tag.putInt("existing", 1));
    CustomData component = stack.get(DataComponents.CUSTOM_DATA);
    ItemNbt.update(stack, tag -> {});
    try (var edit = ItemNbt.edit(stack)) { edit.tag().getIntOr("existing", 0); }
    check(stack.get(DataComponents.CUSTOM_DATA) == component, "no-op needlessly replaces component");
    ItemNbt.update(stack, tag -> ItemNbt.update(stack, inner -> inner.putInt("external", 2)));
    check(ItemNbt.get(stack).getIntOr("external", 0) == 2, "no-op overwrites nested update");
    CompoundTag any = new CompoundTag();
    any.putInt("bad", 1);
    ItemStack zero = new ItemStack(Items.COPPER_INGOT, 0);
    for (ItemStack empty : new ItemStack[] {ItemStack.EMPTY, zero, null}) {
      ItemNbt.set(empty, any);
      ItemNbt.update(empty, ignored -> { throw new AssertionError("empty callback ran"); });
      try (var edit = ItemNbt.edit(empty)) { edit.tag().putInt("bad", 1); }
      check(ItemNbt.get(empty).isEmpty(), "empty stack has mutable custom data");
    }
    check(!ItemStack.EMPTY.has(DataComponents.CUSTOM_DATA), "global EMPTY was contaminated");
    check(!zero.has(DataComponents.CUSTOM_DATA), "zero-count stack was contaminated");
    CompoundTag firstEmpty = ItemNbt.get(null);
    firstEmpty.putInt("bad", 1);
    check(ItemNbt.get(null).isEmpty(), "empty reads share a global mutable tag");
    ItemNbt.set(stack, new CompoundTag());
    check(!stack.has(DataComponents.CUSTOM_DATA), "empty set does not remove component");
  }

  private static void testSeparateConcurrentEdits() throws InterruptedException {
    ItemStack stack = new ItemStack(Items.COPPER_INGOT);
    CountDownLatch opened = new CountDownLatch(2);
    CountDownLatch commit = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread mode = editThread(stack, "mode", opened, commit, failure);
    Thread charge = editThread(stack, "charge", opened, commit, failure);
    mode.start();
    charge.start();
    opened.await();
    commit.countDown();
    mode.join();
    charge.join();
    if (failure.get() != null) throw new AssertionError("concurrent edit failed", failure.get());
    check(ItemNbt.get(stack).getCompoundOrEmpty("electric").size() == 2,
        "separate concurrent helper edits lose unrelated nested additions");
  }

  private static Thread editThread(ItemStack stack, String key, CountDownLatch opened,
      CountDownLatch commit, AtomicReference<Throwable> failure) {
    return new Thread(() -> {
      try (var edit = ItemNbt.edit(stack)) {
        CompoundTag child = new CompoundTag();
        child.putInt(key, 1);
        edit.tag().put("electric", child);
        opened.countDown();
        commit.await();
      } catch (Throwable thrown) {
        failure.compareAndSet(null, thrown);
        opened.countDown();
      }
    }, "item-nbt-" + key);
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
    checks++;
  }
}
