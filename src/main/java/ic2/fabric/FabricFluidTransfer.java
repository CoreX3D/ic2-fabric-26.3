package ic2.fabric;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;

/** Converts foreign droplet stores to whole IC2 millibuckets without losing fractional fluid. */
final class FabricFluidTransfer {
  private static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000L;

  private FabricFluidTransfer() {}

  static long extract(Storage<FluidVariant> storage, FluidVariant variant, long maximum,
      Transaction transaction) {
    return transfer(storage, variant, maximum, transaction, false);
  }

  static long insert(Storage<FluidVariant> storage, FluidVariant variant, long maximum,
      Transaction transaction) {
    return transfer(storage, variant, maximum, transaction, true);
  }

  private static long transfer(Storage<FluidVariant> storage, FluidVariant variant, long maximum,
      Transaction transaction, boolean insert) {
    long transferable;
    try (Transaction probe = transaction.openNested()) {
      transferable = insert ? storage.insert(variant, maximum, probe)
          : storage.extract(variant, maximum, probe);
      if (transferable % DROPLETS_PER_MB == 0) {
        probe.commit();
        return transferable;
      }
    }
    transferable -= transferable % DROPLETS_PER_MB;
    if (transferable == 0) return 0;
    try (Transaction exact = transaction.openNested()) {
      long transferred = insert ? storage.insert(variant, transferable, exact)
          : storage.extract(variant, transferable, exact);
      if (transferred != transferable) return 0;
      exact.commit();
      return transferred;
    }
  }
}
