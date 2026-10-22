package ic2.fabric;

import java.util.Collections;
import java.util.Iterator;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;

/** Standalone regression checks for fractional foreign tanks and transaction rollback. */
public final class FluidTransferCheck {
  private static final class Tank extends SnapshotParticipant<Long> implements Storage<FluidVariant> {
    long amount;
    final long capacity;
    Tank(long amount, long capacity) { this.amount = amount; this.capacity = capacity; }
    @Override public long insert(FluidVariant resource, long maximum, TransactionContext transaction) {
      long moved = Math.min(maximum, capacity - amount);
      updateSnapshots(transaction);
      amount += moved;
      return moved;
    }
    @Override public long extract(FluidVariant resource, long maximum, TransactionContext transaction) {
      long moved = Math.min(maximum, amount);
      updateSnapshots(transaction);
      amount -= moved;
      return moved;
    }
    @Override protected Long createSnapshot() { return amount; }
    @Override protected void readSnapshot(Long snapshot) { amount = snapshot; }
    @Override public Iterator<StorageView<FluidVariant>> iterator() { return Collections.emptyIterator(); }
  }

  private static void equal(long expected, long actual) {
    if (expected != actual) throw new AssertionError("expected " + expected + ", got " + actual);
  }

  public static void main(String[] args) {
    Tank source = new Tank(82, 82);
    try (Transaction transaction = Transaction.openOuter()) {
      equal(81, FabricFluidTransfer.extract(source, null, 162, transaction));
      equal(1, source.amount);
    }
    equal(82, source.amount);
    try (Transaction transaction = Transaction.openOuter()) {
      equal(81, FabricFluidTransfer.extract(source, null, 162, transaction));
      transaction.commit();
    }
    equal(1, source.amount);
    try (Transaction transaction = Transaction.openOuter()) {
      equal(0, FabricFluidTransfer.extract(source, null, 81, transaction));
      transaction.commit();
    }
    equal(1, source.amount);
    Tank target = new Tank(0, 82);
    try (Transaction transaction = Transaction.openOuter()) {
      equal(81, FabricFluidTransfer.insert(target, null, 162, transaction));
      equal(81, target.amount);
    }
    equal(0, target.amount);
    try (Transaction transaction = Transaction.openOuter()) {
      equal(81, FabricFluidTransfer.insert(target, null, 162, transaction));
      transaction.commit();
    }
    equal(81, target.amount);
    try (Transaction transaction = Transaction.openOuter()) {
      equal(0, FabricFluidTransfer.insert(target, null, 81, transaction));
      transaction.commit();
    }
    equal(81, target.amount);
    System.out.println("PASS: 14 checks, fractional fluid conserved and simulation rolled back");
  }
}
