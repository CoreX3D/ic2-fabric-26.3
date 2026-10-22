package ic2.client.gametest;

import ic2.core.IC2;
import ic2.core.proxy.SideProxyServer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Checks components received over the real server connection, never a user session. */
@Environment(EnvType.CLIENT)
@SuppressWarnings("UnstableApiUsage")
final class RussianChatChecks {
  private static final AtomicReference<ConcurrentLinkedQueue<Component>> OBSERVED = new AtomicReference<>();
  private static boolean listenerRegistered;
  private static final List<ExpectedMessage> EXPECTED = List.of(
      new ExpectedMessage("server proxy / component", "ic2.tooltip.mode.normal", "Режим: Обычный"),
      new ExpectedMessage("active proxy / component", "ic2.tooltip.mode.enabled", "Режим: Включён"),
      new ExpectedMessage("server proxy / component argument", "ic2.tooltip.mode.disabled", "Режим: Выключен"),
      new ExpectedMessage("active proxy / string-key argument", "ic2.tooltip.mode.single", "Режим: Одиночный"));

  private RussianChatChecks() {}

  static List<String> validate(ClientGameTestContext context, TestServerContext server,
      TestServerConnection connection) {
    if (!RussianLocaleChecks.isRussianRequested()) return List.of();
    var received = new ConcurrentLinkedQueue<Component>();
    context.runOnClient(client -> {
      if (!listenerRegistered) {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
          var current = OBSERVED.get();
          if (current == null || overlay) return;
          String text = message.getString();
          if (text.startsWith("Режим: ") || text.startsWith("Mode: ")
              || text.startsWith("ic2.tooltip.mode")) current.add(message.copy());
        });
        listenerRegistered = true;
      }
      require(OBSERVED.compareAndSet(null, received), "Another Russian chat observer is still active");
    });
    try {
      server.runOnServer(instance -> {
        var player = connection.getServerPlayer();
        var dedicatedProxy = new SideProxyServer();
        dedicatedProxy.messagePlayer(player,
            Component.translatable("ic2.tooltip.mode", Component.translatable("ic2.tooltip.mode.normal")));
        IC2.sideProxy.messagePlayer(player,
            Component.translatable("ic2.tooltip.mode", Component.translatable("ic2.tooltip.mode.enabled")));
        dedicatedProxy.messagePlayer(player, "ic2.tooltip.mode", Component.translatable("ic2.tooltip.mode.disabled"));
        IC2.sideProxy.messagePlayer(player, "ic2.tooltip.mode", "ic2.tooltip.mode.single");
      });
      connection.waitForClientboundPackets();
      context.waitFor(client -> received.size() >= EXPECTED.size());
      return context.computeOnClient(client -> {
        List<Component> messages = List.copyOf(received);
        require(messages.size() == EXPECTED.size(), "Unexpected number of localized IC2 network messages: " + messages.size());
        List<String> evidence = new ArrayList<>();
        for (int i = 0; i < EXPECTED.size(); i++) {
          ExpectedMessage expected = EXPECTED.get(i);
          Component message = messages.get(i);
          require(message.getContents() instanceof TranslatableContents,
              "Network message was flattened before reaching the client: " + expected.route());
          var outer = (TranslatableContents) message.getContents();
          require(outer.getKey().equals("ic2.tooltip.mode") && outer.getArgs().length == 1,
              "Network message lost its outer translation key: " + expected.route());
          require(outer.getArgs()[0] instanceof Component,
              "Network message lost its nested component argument: " + expected.route());
          Component nested = (Component) outer.getArgs()[0];
          require(nested.getContents() instanceof TranslatableContents
                  && ((TranslatableContents) nested.getContents()).getKey().equals(expected.argumentKey()),
              "Network message resolved its nested translation on the server: " + expected.route());
          require(message.getString().equals(expected.text()),
              "Unexpected Russian network message for " + expected.route() + ": " + message.getString());
          evidence.add("Russian network chat " + expected.route() + ": " + message.getString()
              + "; outer and nested translatable components preserved.");
        }
        return evidence;
      });
    } finally {
      OBSERVED.compareAndSet(received, null);
    }
  }

  private static void require(boolean valid, String message) {
    if (!valid) throw new AssertionError(message);
  }

  private record ExpectedMessage(String route, String argumentKey, String text) {}
}
