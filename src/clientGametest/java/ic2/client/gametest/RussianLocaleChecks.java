package ic2.client.gametest;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;

/** Optional locale checks; all options and resource reloads belong to the isolated test client. */
@Environment(EnvType.CLIENT)
@SuppressWarnings("UnstableApiUsage")
final class RussianLocaleChecks {
  private static final String REQUESTED_LANGUAGE = System.getProperty("ic2.gametest.language", "").trim();
  private static final Map<String, String> EXPECTED = Map.ofEntries(
      Map.entry("block.ic2.generator", "Генератор"),
      Map.entry("block.ic2.macerator", "Дробитель"),
      Map.entry("block.ic2.metal_former", "Формовщик металла"),
      Map.entry("container.ic2.generator", "Генератор"),
      Map.entry("container.ic2.macerator", "Дробитель"),
      Map.entry("container.ic2.metal_former", "Формовщик металла"),
      Map.entry("item.ic2.drill", "Шахтёрский бур"),
      Map.entry("item.ic2.re_battery", "Аккумулятор"),
      Map.entry("ic2.MetalFormer.gui.switch.Rolling", "Прокатка"),
      Map.entry("ic2.tooltip.mode.disabled", "Выключен"),
      Map.entry("ic2.crop_analyzer.growth", "Рост:"),
      Map.entry("ic2.upgrade.advancedGUI.energy", "Энергия"));

  private RussianLocaleChecks() {}

  static boolean isRussianRequested() { return REQUESTED_LANGUAGE.equals("ru_ru"); }

  static SavedLanguage configure(ClientGameTestContext context, List<String> evidence) {
    SavedLanguage previous = context.computeOnClient(client -> new SavedLanguage(
        client.getLanguageManager().getSelected(), client.options.languageCode));
    if (REQUESTED_LANGUAGE.isEmpty()) {
      evidence.add("Language: default test locale " + previous.selected() + " (unchanged).");
      return previous;
    }
    try {
      context.runOnClient(client -> require(client.getLanguageManager().getLanguage(REQUESTED_LANGUAGE) != null,
          "Requested client test language is unavailable: " + REQUESTED_LANGUAGE));
      reload(context, REQUESTED_LANGUAGE, REQUESTED_LANGUAGE);
      context.runOnClient(client -> {
        require(client.getLanguageManager().getSelected().equals(REQUESTED_LANGUAGE)
                && client.options.languageCode.equals(REQUESTED_LANGUAGE),
            "Client language selection and options disagree");
        evidence.add("Language: " + REQUESTED_LANGUAGE + "; actual Minecraft resource reload completed.");
        if (isRussianRequested()) evidence.addAll(validate(client));
      });
      return previous;
    } catch (RuntimeException | Error failure) {
      try { restore(context, previous); }
      catch (RuntimeException | Error restoreFailure) { failure.addSuppressed(restoreFailure); }
      throw failure;
    }
  }

  static void restore(ClientGameTestContext context, SavedLanguage previous) {
    if (!REQUESTED_LANGUAGE.isEmpty()) reload(context, previous.selected(), previous.option());
  }

  private static void reload(ClientGameTestContext context, String selected, String option) {
    CompletableFuture<Void> reload = context.computeOnClient(client -> {
      client.getLanguageManager().setSelected(selected);
      client.options.languageCode = option;
      return client.reloadResourcePacks();
    });
    // Resource application needs render-thread tasks. Never block that thread on its own reload.
    context.waitFor(client -> reload.isDone(), 1200);
    reload.join();
  }

  private static List<String> validate(Minecraft client) {
    List<String> evidence = new ArrayList<>();
    for (var entry : EXPECTED.entrySet()) {
      require(Language.getInstance().has(entry.getKey()), "Missing Russian translation " + entry.getKey());
      String translated = Component.translatable(entry.getKey()).getString();
      require(translated.equals(entry.getValue()),
          "Unexpected Russian translation for " + entry.getKey() + ": " + translated);
      evidence.add("Russian component " + entry.getKey() + ": " + translated);
    }
    try {
      // The production Font exposes no missing-glyph query; inspect its real default provider only here.
      Field providerField = Font.class.getDeclaredField("provider");
      providerField.setAccessible(true);
      Font.Provider provider = (Font.Provider) providerField.get(client.font);
      var glyphs = provider.glyphs(FontDescription.DEFAULT);
      var missing = glyphs.getGlyph(0x10ffff);
      String alphabet = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя";
      alphabet.codePoints().forEach(codePoint -> {
        var glyph = glyphs.getGlyph(codePoint);
        require(glyph != null && glyph != missing && glyph.info() != SpecialGlyphs.MISSING,
            "Missing Cyrillic glyph U+" + Integer.toHexString(codePoint));
      });
      evidence.add("Russian font: all 66 upper/lowercase alphabet glyphs, including Ё/ё, are available.");
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError("26.3 font provider inspection failed", exception);
    }
    return evidence;
  }

  static List<String> validateItemNames(List<ItemStack> stacks) {
    List<String> evidence = new ArrayList<>();
    if (!isRussianRequested()) return evidence;
    for (ItemStack stack : stacks) {
      String name = stack.getHoverName().getString();
      require(name.codePoints().anyMatch(codePoint -> Character.UnicodeBlock.of(codePoint)
              == Character.UnicodeBlock.CYRILLIC),
          "Sampled item lacks a Russian display name: " + name);
      require(!name.contains("item.ic2.") && !name.contains("block.ic2."),
          "Sampled item displays its untranslated key: " + name);
      evidence.add("Russian item name: " + name);
    }
    return evidence;
  }

  static List<String> validateRegisteredItemNames(Minecraft client) {
    if (!isRussianRequested()) return List.of();
    List<String> evidence = new ArrayList<>();
    List<String> failures = new ArrayList<>();
    int count = 0;
    for (var item : BuiltInRegistries.ITEM) {
      var id = BuiltInRegistries.ITEM.getKey(item);
      if (!id.getNamespace().equals("ic2")) continue;
      Component hoverName = new ItemStack(item).getHoverName();
      collectMissingTranslations(hoverName, id.toString(), failures);
      String name = hoverName.getString();
      if (name.isBlank() || name.contains("item.ic2.") || name.contains("block.ic2.")) {
        failures.add("Registered item displays an empty name or untranslated key: " + id + " -> " + name);
      }
      evidence.add("Russian registered item " + id + ": " + name);
      count++;
    }
    require(count > 0, "Russian item-name check found no registered IC2 items");
    var report = client.gameDirectory.toPath().resolve("russian-item-names.txt");
    try {
      List<String> reportLines = new ArrayList<>(evidence);
      reportLines.add("Checked registered IC2 item names: " + count);
      reportLines.addAll(failures);
      Files.write(report, reportLines);
    } catch (java.io.IOException exception) {
      throw new AssertionError("Could not save Russian registered item-name report", exception);
    }
    require(failures.isEmpty(), "Russian registered item names have " + failures.size()
        + " translation failures; full report: " + report.toAbsolutePath() + "\n" + String.join("\n", failures));
    evidence.add("Russian registered item names: " + count
        + " items; all displayed translation components resolve without raw-key fallbacks.");
    return evidence;
  }

  private static void collectMissingTranslations(Component component, String itemId, List<String> failures) {
    if (component.getContents() instanceof TranslatableContents translation) {
      if (!Language.getInstance().has(translation.getKey())) {
        failures.add("Registered item " + itemId + " uses a missing display-name translation " + translation.getKey());
      }
      for (Object argument : translation.getArgs()) {
        if (argument instanceof Component nested) collectMissingTranslations(nested, itemId, failures);
      }
    }
    for (Component sibling : component.getSiblings()) collectMissingTranslations(sibling, itemId, failures);
  }

  private static void require(boolean valid, String message) {
    if (!valid) throw new AssertionError(message);
  }

  record SavedLanguage(String selected, String option) {}
}
