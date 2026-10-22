package ic2.core.util;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Detached access to the mutable NBT inside an item's immutable custom-data component.
 *
 * <p>Every read and write is copied. Edits commit only their differences from the opening
 * snapshot, so a nested operation such as charging an electric item does not lose its changes
 * when the outer operation later saves a mode or another unrelated NBT field. Compounds merge
 * recursively; lists, arrays and scalar tags are indivisible values. An explicit replacement
 * or removal wins over another edit to the same key.
 *
 * <p>There is no shared tag cache. Accesses through this utility synchronize their snapshots
 * and commits on the stack, but callers must still obey Minecraft's thread rules for all
 * other ItemStack operations. An Edit and its mutable tag belong to the calling thread.
 */
public final class ItemNbt {
  private ItemNbt() {}

  /** Returns an independent tag, or a new empty tag for a missing component or empty stack. */
  public static CompoundTag get(ItemStack stack) {
    if (stack == null) return new CompoundTag();
    synchronized (stack) {
      if (stack.isEmpty()) return new CompoundTag();
      return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
  }

  /**
   * Replaces the complete custom-data component. A null or empty tag removes the component.
   * Empty stacks are never modified. Use update or edit when unrelated changes must survive.
   */
  public static void set(ItemStack stack, CompoundTag tag) {
    if (stack == null) return;
    synchronized (stack) {
      if (stack.isEmpty()) return;
      if (tag == null || tag.isEmpty()) {
        stack.remove(DataComponents.CUSTOM_DATA);
      } else {
        // CustomData.of performs a deep copy, including arrays, lists and nested compounds.
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
      }
    }
  }

  /**
   * Edits a detached copy and merges its delta into the component as it exists after the
   * callback. If the callback throws, its local changes are discarded; independent nested
   * updates that it already performed remain committed. Empty stacks skip the callback.
   */
  public static void update(ItemStack stack, Consumer<CompoundTag> action) {
    Objects.requireNonNull(action, "action");
    if (stack == null || stack.isEmpty()) return;
    CompoundTag baseline = get(stack);
    CompoundTag edited = baseline.copy();
    action.accept(edited);
    commit(stack, baseline, edited);
  }

  /**
   * Opens an explicit edit for legacy code retaining a local NBT variable. Close commits the
   * delta once, including when try-with-resources closes during exception unwinding. Use
   * update instead if a failed callback must discard its local changes.
   */
  public static Edit edit(ItemStack stack) {
    return new Edit(stack);
  }

  private static void commit(ItemStack stack, CompoundTag baseline, CompoundTag edited) {
    if (stack == null || baseline.equals(edited)) return;
    synchronized (stack) {
      if (stack.isEmpty()) return;
      CompoundTag current = get(stack);
      CompoundTag merged = current.copy();
      applyDelta(baseline, edited, merged);
      if (!current.equals(merged)) set(stack, merged);
    }
  }

  private static void applyDelta(CompoundTag baseline, CompoundTag edited, CompoundTag target) {
    for (String key : baseline.keySet()) {
      if (!edited.contains(key)) target.remove(key);
    }
    for (var entry : edited.entrySet()) {
      String key = entry.getKey();
      Tag after = entry.getValue();
      Tag before = baseline.get(key);
      if (Objects.equals(before, after)) continue;

      if (after instanceof CompoundTag afterCompound
          && (before == null || before instanceof CompoundTag)) {
        Tag current = target.get(key);
        CompoundTag child = current instanceof CompoundTag currentCompound
            ? currentCompound : new CompoundTag();
        applyDelta(before instanceof CompoundTag beforeCompound
            ? beforeCompound : new CompoundTag(), afterCompound, child);
        // A deletion alone must not resurrect a parent removed by another operation.
        // Adding an explicitly empty compound, however, is an intentional key addition.
        if (!child.isEmpty() || current instanceof CompoundTag
            || (before == null && afterCompound.isEmpty())) {
          target.put(key, child);
        }
      } else {
        target.put(key, after.copy());
      }
    }
  }

  public static final class Edit implements AutoCloseable {
    private final ItemStack stack;
    private final CompoundTag baseline;
    private final CompoundTag edited;
    private boolean closed;

    private Edit(ItemStack stack) {
      this.stack = stack;
      this.baseline = get(stack);
      this.edited = baseline.copy();
    }

    /** Returns the detached working tag. Calling tag() after close is rejected. */
    public CompoundTag tag() {
      if (closed) throw new IllegalStateException("Item NBT edit is already closed");
      return edited;
    }

    @Override
    public void close() {
      if (closed) return;
      closed = true;
      commit(stack, baseline, edited);
    }
  }
}
