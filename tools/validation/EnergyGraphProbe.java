package ic2.core.energy.grid;

import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.*;
import ic2.api.info.ILocatable;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Exercises the production graph/cache using isolated positional energy API fixtures. */
public final class EnergyGraphProbe {
  public static void main(String[] args) throws Exception {
    EnergyNet.instance = EnergyNetGlobal.create();
    int checks = 0;
    for (int length = 1; length <= 12; length++) {
      for (int attempt = 0; attempt < 40; attempt++) {
        EnergyNetLocal enet = EnergyNetLocal.create(null);
        Grid grid = new Grid(enet);
        List<Node> nodes = new ArrayList<>();
        nodes.add(node(enet, new Source(new BlockPos(0, 0, 0))));
        for (int x = 1; x <= length; x++) nodes.add(node(enet, new Cable(new BlockPos(x, 0, 0))));
        nodes.add(node(enet, new Sink(new BlockPos(length + 1, 0, 0))));
        for (int i = 0; i < nodes.size(); i++) {
          grid.add(nodes.get(i), i == 0 ? List.of() : List.of(nodes.get(i - 1)));
        }
        new EnergyCalculatorUnified().handleGridChange(grid);
        Object data = grid.getData();
        var pathsField = data.getClass().getDeclaredField("energySourceToEnergyPathMap");
        pathsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Node, List<EnergyPath>> paths = (Map<Node, List<EnergyPath>>) pathsField.get(data);
        EnergyPath path = paths.get(nodes.getFirst()).getFirst();
        List<Node> expected = nodes.subList(1, nodes.size() - 1);
        check(path.conductors.equals(expected), "Reordered conductors for " + length + " cables: "
            + path.conductors.stream().map(n -> Integer.toString(n.uid)).toList());
        check(path.targetDirection == Direction.WEST, "Lost sink input direction for " + length + " cables");
        check(Math.abs(path.loss - Math.floor(length * 0.2 + 0.002)) < 1.0e-9,
            "Wrong path loss for " + length + " cables: " + path.loss);
        checks++;
      }
    }
    System.out.println("PASS: " + checks + " production graph caches preserve conductor order, input direction, and IC2 loss");
  }

  private static Node node(EnergyNetLocal enet, IEnergyTile endpoint) {
    return new Tile(enet, endpoint, List.of(endpoint)).nodes.getFirst();
  }
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
  private abstract static class Endpoint implements IEnergyTile, ILocatable {
    private final BlockPos pos;
    Endpoint(BlockPos pos) { this.pos = pos; }
    public BlockPos getPosition() { return pos; }
    public Level getWorldObj() { return null; }
  }
  private static final class Source extends Endpoint implements IEnergySource {
    Source(BlockPos pos) { super(pos); }
    public boolean emitsEnergyTo(IEnergyAcceptor receiver, Direction side) { return true; }
    public double getOfferedEnergy() { return 32; }
    public int getSourceTier() { return 1; }
    public void drawEnergy(double amount) {}
  }
  private static final class Sink extends Endpoint implements IEnergySink {
    Sink(BlockPos pos) { super(pos); }
    public boolean acceptsEnergyFrom(IEnergyEmitter emitter, Direction side) { return true; }
    public double getDemandedEnergy() { return 320; }
    public int getSinkTier() { return 1; }
    public double injectEnergy(Direction side, double amount, double voltage) { return 0; }
  }
  private static final class Cable extends Endpoint implements IEnergyConductor {
    Cable(BlockPos pos) { super(pos); }
    public boolean emitsEnergyTo(IEnergyAcceptor receiver, Direction side) { return true; }
    public boolean acceptsEnergyFrom(IEnergyEmitter emitter, Direction side) { return true; }
    public double getConductionLoss() { return 0.2; }
    public double getInsulationEnergyAbsorption() { return 128; }
    public double getInsulationBreakdownEnergy() { return 9001; }
    public double getConductorBreakdownEnergy() { return 129; }
    public void removeInsulation() {}
    public void removeConductor() {}
  }
}
