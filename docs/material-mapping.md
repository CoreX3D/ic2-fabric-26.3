# Материалы и данные Minecraft 26.3

Цель переноса — добыча vanilla и последующая промышленная переработка IC2.
Регистрации vanilla используются напрямую; промежуточные продукты IC2 остаются
самостоятельными предметами. Совпадение названия вещества не означает совпадение
его технологической формы.

## Соответствия

| Старое обозначение IC2 Experimental | В порте | Решение |
| --- | --- | --- |
| `resource#copper_ore` | `minecraft:copper_ore`, `minecraft:deepslate_copper_ore` | Только vanilla worldgen и loot; без собственной медной руды |
| `ingot#copper` | `minecraft:copper_ingot` | Vanilla слиток во входах и выходах рецептов |
| `resource#copper_block` | `minecraft:copper_block` | Девять vanilla слитков; машинное прессование допустимо |
| Уже vanilla iron/gold ingots и blocks | `minecraft:*` | Не создавать модовые дубли |
| `resource#basalt` | `minecraft:basalt` | Материальный alias на уровне Java; свойства прочности принимает vanilla |
| `crafting#industrial_diamond` | `minecraft:diamond` | Coal chunk теперь дает vanilla diamond; advancement и common tag тоже используют его |
| Tin, lead, uranium ores и deepslate-варианты | `ic2:*` | Сохранить: vanilla эквивалентов нет |
| Bronze, tin, lead, silver, steel, uranium ingots и blocks | `ic2:*` | Сохранить |
| Mixed metal ingot / alloy и refined iron | `ic2:*`, где предусмотрено активным профилем | Не заменять обычным железом и не включать Classic-only контент автоматически |
| Crushed и purified ores, в том числе copper/iron/gold | `ic2:*` | Сохранить дробление, промывку и центрифугу |
| Dust, small dust, plates, dense plates, casings, cables | `ic2:*` | Vanilla не содержит равнозначных форм этих материалов |
| Small copper/iron/gold dust | `ic2:*` | Не заменять металлическими nuggets |
| Sulfur dust | `ic2:sulfur_dust` | Vanilla `minecraft:sulfur` — строительный блок, не пыль |
| Resin и rubber | `ic2:resin`, `ic2:rubber` | Vanilla resin clump перерабатывается в resin brick и не заменяет каучуковое сырье |
| Bronze equipment | `ic2:*` | Vanilla copper equipment изготовлено из другого металла |
| Iridium, isotopes, MOX, fuel pellets, slag, ashes, iodine, matter, coke | `ic2:*` | Сохранить собственные технологические цепочки |
| Iron fence, усиленные scaffolds, reactor/machine blocks | `ic2:*` | Сохранить функциональные варианты IC2 |

`minecraft:lead` — поводок, а не свинец. `clay_ball`, `sand`, `quartz` также не
заменяют IC2 clay dust или silicon dioxide dust.

## Принятый баланс

| Вход в macerator | Выход |
| --- | --- |
| 1 raw copper / raw iron / raw gold | 2 соответствующих crushed ore |
| 1 vanilla raw metal block | 18 соответствующих crushed ore |
| 1 copper ore или deepslate copper ore (Silk Touch) | 7 crushed copper |
| 1 iron ore или deepslate iron ore (Silk Touch) | 2 crushed iron |
| 1 overworld gold ore или deepslate gold ore (Silk Touch) | 2 crushed gold |
| 1 nether gold ore | 8 vanilla gold nuggets |
| 1 copper / iron / gold ingot | 1 соответствующая dust |

Vanilla 26.3 copper ore дает 2–5 raw copper без Fortune. Семь crushed из блока
соответствуют среднему обычному выходу `3.5 raw × 2`; это явный выбранный баланс,
а не точное воспроизведение доходности руды IC2 1.12.2. Fortune остается частью
vanilla-добычи. Для Nether gold ore выбран отдельный рецепт `2 × средние 4 nuggets`;
тег `ic2:overworld_gold_ores` исключает его из маршрута двух crushed gold.

Сжатие raw остается `9 raw → 1 raw block`. Обратный машинный маршрут сразу дает
18 crushed и не создает повторяемого цикла получения металла. Время и энергия
обработки блока остаются стандартными для одного рецепта; это удобство пакетной
обработки. Обратные рецепты из слитков не дают crushed и не удваивают металл.

Smelting crushed, purified и dust copper/iron/gold возвращает ровно один vanilla
слиток. Промывка и центрифуга сохраняют собственные дополнительные выходы IC2.
Декоративные cut copper/slabs/stairs не считаются блоками хранения девяти слитков.

Некоторые стабильные IDs машинных рецептов сохраняют старые окончания
`*_to_raw_*` и `coal_chunk_to_industrial_diamond`; фактические выходы в JSON
изменены согласно таблице. ID рецепта не является ID предмета.

## Совместимость форматов 26.3

- 38 vanilla рецептов используют строковые ингредиенты / `#tag` и `result.id`.
  Собственные сериализаторы IC2 сохраняют существующую схему `{item/tag}` и
  `result.item`; ее поддерживает кодек IC2.
- Старый ID vanilla `minecraft:chain` заменен на `minecraft:iron_chain`.
- Loot tables используют `condition`, `modifier` и дискриминатор `type`.
  Несколько условий объединяются через `minecraft:all_of`. Silk Touch использует
  компонентный predicate. `block_state_property` заменен на `match_block`.
- `recipe_unlocked` в advancements использует поле `recipes`; item tag predicates
  используют `items: "#namespace:tag"`.
- Семь worldgen definitions находятся в `worldgen/feature/`, без обертки `config`.
  Block states используют `id`/`properties`. У rubber tree —
  `below_trunk_provider`, inline foliage provider и `minecraft:weighted`.
- Для tin/lead/uranium взяты правила target из vanilla 26.3 `ore_iron` с сохранением
  собственных состояний и размера жил. Это учитывает tuff и новые
  `height_specific_ore_replaceables`. Медные features не добавляются.
- Четыре NeoForge biome modifier JSON удалены: размещение выполняет существующий
  Fabric callback. Остальные Java-регистрации, включая `ic2:block_nbt` для сохранения
  состояния при выпадении машин, проверяются отдельно при запуске игры.
- Repair tags используются Java material definitions. Bronze ремонтируется
  bronze ingot, alloy armor — alloy, iridium tools — iridium; пустые repair tags
  электрического оборудования не назначают обычный материал ремонта.

## Проверка

```powershell
python scripts/validate_material_resources.py `
  --minecraft-jar '<путь к vanilla 26.3 client.jar>' `
  --fabric-api-jar '<путь к fabric-api-0.163.0+26.3.jar>'
```

Скрипт читает оба resource source set, проверяет JSON и повторяющиеся ключи,
запрещенные дубли материалов, vanilla item IDs, item tags из vanilla и вложенных
Fabric API JAR, заданные выходы обработки, признаки старых форматов и связи features.
Он также ищет увеличивающие циклы среди детерминированных одно-входных/одно-выходных
преобразований меди, железа и золота вместе с vanilla упаковкой и распаковкой.
Промывка с несколькими выходами не объявляется циклом лишь из-за обогащения сырья.
Это статическая проверка ресурсов; загрузка реальными Minecraft codecs и игровой
прогон нужны отдельно.

Основания: исходные INI оригинального `industrialcraft-2-2.8.222-ex112.jar`, JSON
активной базы IC2R, `version.json`, recipes/loot/worldgen и codecs локального
vanilla 26.3 JAR, common tags из Fabric API 0.161.0+26.3; повторная проверка
выполнена с 0.163.0+26.3 из текущего профиля пользователя. Исходные JAR не
запускались и не изменялись.
